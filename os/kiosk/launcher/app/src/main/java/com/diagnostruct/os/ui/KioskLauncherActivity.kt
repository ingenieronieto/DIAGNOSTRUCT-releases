package com.diagnostruct.os.ui

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.os.Build
import android.os.Bundle
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

/**
 * Pantalla de inicio del sistema.
 *
 * Sustituye al launcher de fabrica, de modo que el boton de inicio y la muerte
 * de la aplicacion desembocan siempre aqui. Su unico cometido es devolver al
 * usuario a DIAGNOSTRUCT; la pantalla que dibuja solo se ve un instante, o de
 * forma permanente cuando algo va mal y hay que avisar al tecnico.
 */
class KioskLauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityKioskBinding
    private lateinit var policy: KioskPolicy

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

        // Acceso tecnico: mantener pulsado el logotipo y despues introducir PIN.
        binding.logo.setOnLongClickListener {
            TechnicianActivity.start(this)
            true
        }

        binding.actionRetry.setOnClickListener {
            consecutiveBounces = 0
            faultMode = false
            launchOrExplain()
        }

        binding.actionTechnician.setOnClickListener { TechnicianActivity.start(this) }
    }

    override fun onResume() {
        super.onResume()
        goFullScreen()
        launchOrExplain()
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
        if (!KioskConfig.kioskEnabled(this)) {
            showState(
                title = getString(R.string.estado_mantenimiento_titulo),
                detail = getString(R.string.estado_mantenimiento_detalle),
                busy = false,
                showRetry = true,
            )
            return
        }

        if (faultMode) return

        if (!AppLauncher.isAppInstalled(this)) {
            showState(
                title = getString(R.string.estado_sin_app_titulo),
                detail = getString(R.string.estado_sin_app_detalle),
                busy = true,
                showRetry = true,
            )
            UpdateScheduler.checkNow(this)
            return
        }

        val now = SystemClock.elapsedRealtime()
        consecutiveBounces = if (now - lastLaunchAt < BOUNCE_WINDOW_MS) consecutiveBounces + 1 else 0
        lastLaunchAt = now

        if (consecutiveBounces >= MAX_BOUNCES) {
            Log.e(TAG, "La aplicacion no se mantiene abierta tras $consecutiveBounces intentos")
            faultMode = true
            showState(
                title = getString(R.string.estado_fallo_titulo),
                detail = getString(R.string.estado_fallo_detalle),
                busy = false,
                showRetry = true,
            )
            return
        }

        showState(
            title = getString(R.string.estado_abriendo_titulo),
            detail = getString(R.string.estado_abriendo_detalle),
            busy = true,
            showRetry = false,
        )

        if (!launchPinned()) {
            faultMode = true
            showState(
                title = getString(R.string.estado_fallo_titulo),
                detail = getString(R.string.estado_fallo_detalle),
                busy = false,
                showRetry = true,
            )
        }
    }

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
        return runCatching {
            if (canPin) {
                val options = ActivityOptions.makeBasic().setLockTaskEnabled(true)
                startActivity(intent, options.toBundle())
            } else {
                startActivity(intent)
            }
            true
        }.onFailure { error ->
            Log.e(TAG, "No se pudo abrir la aplicacion anclada", error)
        }.getOrElse {
            // Si el anclaje es lo que falla, al menos se abre sin anclar.
            runCatching { startActivity(intent); true }.getOrDefault(false)
        }
    }

    private fun showState(title: String, detail: String, busy: Boolean, showRetry: Boolean) {
        binding.stateTitle.text = title
        binding.stateDetail.text = detail
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.actionRetry.visibility = if (showRetry) View.VISIBLE else View.GONE
        binding.actionTechnician.visibility = if (showRetry) View.VISIBLE else View.GONE
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

        @Suppress("unused")
        fun isLockTaskActive(context: Context): Boolean {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        }
    }
}
