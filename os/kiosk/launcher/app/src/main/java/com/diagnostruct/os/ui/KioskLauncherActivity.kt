package com.diagnostruct.os.ui

import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.diagnostruct.os.KioskConfig
import com.diagnostruct.os.R
import com.diagnostruct.os.databinding.ActivityKioskBinding
import com.diagnostruct.os.kiosk.AppLauncher
import com.diagnostruct.os.policy.KioskPolicy
import com.diagnostruct.os.update.UpdateScheduler
import kotlin.math.ceil

/**
 * Pantalla de inicio del sistema.
 *
 * Sustituye al launcher de fabrica, de modo que el boton de inicio y la muerte
 * de la aplicacion desembocan siempre aqui. Su cometido es devolver al usuario a
 * DIAGNOSTRUCT; la pantalla que dibuja solo se ve unos segundos al encender, o
 * de forma permanente cuando algo va mal y hay que avisar al tecnico.
 */
class KioskLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding
    private lateinit var policy: KioskPolicy

    private val handler = Handler(Looper.getMainLooper())

    /** Relanzamientos seguidos y muy rapidos: sintoma de que la app no arranca. */
    private var consecutiveBounces = 0
    private var lastLaunchAt = 0L
    private var faultMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityKioskBinding.inflate(layoutInflater)
        setContentView(binding.root)
        policy = KioskPolicy(this)

        goFullScreen()

        // En el kiosco no hay "atras" desde la pantalla de inicio.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        binding.logo.setOnLongClickListener {
            openTechnicianPanel()
            true
        }

        binding.actionRetry.setOnClickListener {
            consecutiveBounces = 0
            faultMode = false
            decideNextStep()
        }

        binding.actionTechnician.setOnClickListener { openTechnicianPanel() }
    }

    override fun onResume() {
        super.onResume()
        goFullScreen()
        decideNextStep()
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacksAndMessages(null)
    }

    private fun openTechnicianPanel() {
        handler.removeCallbacksAndMessages(null)
        TechnicianActivity.start(this)
    }

    private fun decideNextStep() {
        handler.removeCallbacksAndMessages(null)

        if (!KioskConfig.kioskEnabled(this)) {
            showState(
                title = getString(R.string.estado_mantenimiento_titulo),
                detail = getString(R.string.estado_mantenimiento_detalle),
                busy = false,
                showActions = true,
            )
            return
        }

        // Tregua corta pedida desde el panel: el tecnico esta cambiando la red
        // o revisando el equipo, y reabrir la aplicacion ahora lo expulsaria.
        val treguaMs = KioskConfig.maintenanceRemainingMs(this)
        if (treguaMs > 0) {
            val minutos = ceil(treguaMs / 60_000.0).toInt()
            showState(
                title = getString(R.string.estado_tregua_titulo),
                detail = resources.getQuantityString(R.plurals.estado_tregua_detalle, minutos, minutos),
                busy = false,
                showActions = true,
            )
            return
        }

        if (faultMode) return

        if (!AppLauncher.isAppInstalled(this)) {
            showState(
                title = getString(R.string.estado_sin_app_titulo),
                detail = getString(R.string.estado_sin_app_detalle),
                busy = true,
                showActions = true,
            )
            UpdateScheduler.checkNow(this)
            return
        }

        if (!bootWindowConsumed) {
            startBootWindow()
            return
        }

        launchOrExplain()
    }

    /**
     * Ventana de acceso al encender.
     *
     * Sin ella el panel tecnico es inalcanzable: la aplicacion se abre al
     * instante y, una vez anclada, el boton de inicio no responde, asi que no
     * hay forma de volver aqui. Estos segundos son la unica puerta fiable, y se
     * pagan una vez por encendido, no cada vez que se vuelve de la aplicacion.
     */
    private fun startBootWindow() {
        var restantes = BOOT_WINDOW_SECONDS
        showState(
            title = getString(R.string.estado_abriendo_titulo),
            detail = getString(R.string.estado_arranque_detalle, restantes),
            busy = true,
            showActions = true,
        )

        val tick = object : Runnable {
            override fun run() {
                restantes -= 1
                if (restantes > 0) {
                    binding.stateDetail.text = getString(R.string.estado_arranque_detalle, restantes)
                    handler.postDelayed(this, 1_000L)
                } else {
                    bootWindowConsumed = true
                    launchOrExplain()
                }
            }
        }
        handler.postDelayed(tick, 1_000L)
    }

    /**
     * Abre la aplicacion, o explica por que no puede abrirla.
     *
     * Un launcher que reabre sin condiciones convierte un fallo de arranque de
     * la aplicacion en un bucle infinito, y la tablet queda inservible incluso
     * para el tecnico. Por eso se cuentan los rebotes rapidos y, pasado el
     * umbral, se para y se muestra la pantalla de aviso.
     */
    private fun launchOrExplain() {
        val now = SystemClock.elapsedRealtime()
        consecutiveBounces = if (now - lastLaunchAt < BOUNCE_WINDOW_MS) consecutiveBounces + 1 else 0
        lastLaunchAt = now

        if (consecutiveBounces >= MAX_BOUNCES) {
            Log.e(TAG, "La aplicacion no se mantiene abierta tras $consecutiveBounces intentos")
            faultMode = true
            showFault()
            return
        }

        showState(
            title = getString(R.string.estado_abriendo_titulo),
            detail = getString(R.string.estado_abriendo_detalle),
            busy = true,
            showActions = false,
        )

        if (!launchPinned()) {
            faultMode = true
            showFault()
        }
    }

    private fun showFault() = showState(
        title = getString(R.string.estado_fallo_titulo),
        detail = getString(R.string.estado_fallo_detalle),
        busy = false,
        showActions = true,
    )

    /**
     * Abre DIAGNOSTRUCT anclada a la pantalla.
     *
     * A partir de Android 9 se puede pedir el anclaje al lanzar, que es la via
     * limpia para fijar una aplicacion ajena que no sabe nada del kiosco. En
     * versiones anteriores se abre sin anclar: ahi el blindaje lo sostienen la
     * pantalla de inicio persistente y el ocultado de aplicaciones.
     */
    private fun launchPinned(): Boolean {
        val intent = AppLauncher.appIntent(this) ?: return false
        val canPin = policy.isDeviceOwner && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

        if (canPin) {
            val anclada = runCatching {
                val options = ActivityOptions.makeBasic().setLockTaskEnabled(true)
                startActivity(intent, options.toBundle())
            }.onFailure { Log.e(TAG, "No se pudo abrir la aplicacion anclada", it) }.isSuccess
            if (anclada) return true
        }

        // Si el anclaje es lo que falla, al menos se abre sin anclar.
        return runCatching { startActivity(intent) }
            .onFailure { Log.e(TAG, "No se pudo abrir la aplicacion", it) }
            .isSuccess
    }

    private fun showState(title: String, detail: String, busy: Boolean, showActions: Boolean) {
        binding.stateTitle.text = title
        binding.stateDetail.text = detail
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.actionRetry.visibility = if (showActions) View.VISIBLE else View.GONE
        binding.actionTechnician.visibility = if (showActions) View.VISIBLE else View.GONE
    }

    private fun goFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private companion object {
        const val TAG = "KioskLauncher"

        /** Dos vueltas a la pantalla de inicio en menos de esto es un rebote. */
        const val BOUNCE_WINDOW_MS = 4_000L
        const val MAX_BOUNCES = 3

        /** Segundos de acceso al panel tras encender. Poner a 0 lo desactiva. */
        const val BOOT_WINDOW_SECONDS = 3

        /**
         * Vive en el companion a proposito: se quiere una ventana por arranque
         * del proceso, no una cada vez que se vuelve de la aplicacion.
         */
        @Volatile
        var bootWindowConsumed = BOOT_WINDOW_SECONDS <= 0
    }
}
