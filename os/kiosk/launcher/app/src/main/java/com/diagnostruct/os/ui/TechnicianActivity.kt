package com.diagnostruct.os.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.diagnostruct.os.KioskConfig
import com.diagnostruct.os.R
import com.diagnostruct.os.databinding.ActivityTechnicianBinding
import com.diagnostruct.os.policy.EssentialPackages
import com.diagnostruct.os.policy.KioskPolicy
import com.diagnostruct.os.policy.PolicyScheduler
import com.diagnostruct.os.update.UpdateScheduler
import kotlin.math.ceil

/**
 * Panel de mantenimiento, protegido por PIN.
 *
 * Es la unica puerta prevista para salir del kiosco sin restablecer el equipo.
 * Existe porque un tecnico en obra necesita cambiar de red Wi-Fi o forzar una
 * actualizacion, y sin esta puerta la alternativa seria devolver la tablet.
 */
class TechnicianActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTechnicianBinding
    private lateinit var policy: KioskPolicy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTechnicianBinding.inflate(layoutInflater)
        setContentView(binding.root)
        policy = KioskPolicy(this)

        showLocked()

        binding.actionUnlock.setOnClickListener { attemptUnlock() }
        binding.actionCancel.setOnClickListener { finish() }

        binding.actionWifi.setOnClickListener { openWifiSettings() }
        binding.actionUpdate.setOnClickListener {
            UpdateScheduler.checkNow(this)
            toast(getString(R.string.tecnico_buscando_actualizacion))
        }
        binding.actionReapply.setOnClickListener {
            PolicyScheduler.applyNow(this)
            toast(getString(R.string.tecnico_politica_aplicada))
            refreshDiagnostics()
        }
        binding.actionToggleKiosk.setOnClickListener { toggleKiosk() }
        binding.actionOpenApp.setOnClickListener { backToApp() }
        binding.actionChangePin.setOnClickListener { changePin() }
    }

    /**
     * Cambia el PIN de mantenimiento.
     *
     * Existe porque el alta por USB no puede fijarlo (solo el QR lo lleva), y
     * sin esto una tablet dada de alta con cable se quedaria con el PIN de
     * fabrica para siempre, que ademas esta publicado en este repositorio.
     */
    private fun changePin() {
        val nuevo = binding.newPinInput.text?.toString()?.trim().orEmpty()
        when {
            nuevo.length < MIN_PIN_LENGTH -> {
                toast(getString(R.string.tecnico_pin_corto))
                return
            }
            KioskConfig.verifyPin(this, nuevo) -> {
                toast(getString(R.string.tecnico_pin_igual))
                return
            }
        }
        KioskConfig.setTechnicianPin(this, nuevo)
        binding.newPinInput.text?.clear()
        toast(getString(R.string.tecnico_pin_cambiado))
        refreshDiagnostics()
    }

    /**
     * Comprueba el PIN con freno a la fuerza bruta.
     *
     * Sin espera creciente, un PIN de cuatro cifras se agota probando a mano en
     * una tarde, y detras esta el control total del equipo.
     */
    private fun attemptUnlock() {
        val esperaMs = KioskConfig.lockoutRemainingMs(this)
        if (esperaMs > 0) {
            toast(getString(R.string.tecnico_pin_bloqueado, ceil(esperaMs / 1000.0).toInt()))
            return
        }

        val entered = binding.pinInput.text?.toString().orEmpty()
        binding.pinInput.text?.clear()

        if (!KioskConfig.verifyPin(this, entered)) {
            val castigoMs = KioskConfig.registerFailedAttempt(this)
            if (castigoMs > 0) {
                toast(getString(R.string.tecnico_pin_bloqueado, (castigoMs / 1000).toInt()))
            } else {
                toast(getString(R.string.tecnico_pin_incorrecto))
            }
            return
        }

        KioskConfig.clearFailedAttempts(this)
        showUnlocked()
    }

    private fun showLocked() {
        binding.lockedGroup.visibility = View.VISIBLE
        binding.unlockedGroup.visibility = View.GONE
    }

    private fun showUnlocked() {
        binding.lockedGroup.visibility = View.GONE
        binding.unlockedGroup.visibility = View.VISIBLE
        refreshDiagnostics()
    }

    /**
     * Alterna entre kiosco y mantenimiento. En mantenimiento se levantan las
     * restricciones y reaparecen las aplicaciones, para poder trabajar sobre el
     * equipo; al volver, se reaplica todo.
     */
    private fun toggleKiosk() {
        val enabled = KioskConfig.kioskEnabled(this)
        if (enabled) {
            KioskConfig.setKioskEnabled(this, false)
            policy.suspendKiosk()
            runCatching { stopLockTask() }
            toast(getString(R.string.tecnico_mantenimiento_activo))
        } else {
            KioskConfig.setKioskEnabled(this, true)
            PolicyScheduler.applyNow(this)
            toast(getString(R.string.tecnico_kiosco_activo))
        }
        refreshDiagnostics()
    }

    /**
     * Ajustes de Wi-Fi.
     *
     * Hay que soltar el anclaje antes, porque Ajustes no esta en la lista de
     * aplicaciones autorizadas. Y hay que abrir una tregua antes de soltarlo:
     * si no, el aviso de salida del anclaje hace que el kiosco reabra la
     * aplicacion y expulse al tecnico de Ajustes antes de que toque nada.
     */
    private fun openWifiSettings() {
        KioskConfig.startMaintenanceWindow(this, MAINTENANCE_MINUTES)
        runCatching { stopLockTask() }
        runCatching { startActivity(EssentialPackages.wifiSettingsIntent()) }
            .onSuccess { toast(getString(R.string.tecnico_wifi_tregua, MAINTENANCE_MINUTES)) }
            .onFailure {
                KioskConfig.endMaintenanceWindow(this)
                toast(getString(R.string.tecnico_sin_ajustes_wifi))
            }
    }

    /**
     * Cierra la tregua y devuelve el control a la pantalla de inicio, que es
     * quien sabe reabrir la aplicacion anclada. Lanzarla desde aqui la dejaria
     * suelta.
     */
    private fun backToApp() {
        KioskConfig.endMaintenanceWindow(this)
        finish()
    }

    private fun refreshDiagnostics() {
        val appVersion = installedAppVersion() ?: getString(R.string.tecnico_app_ausente)
        binding.diagnostics.text = getString(
            R.string.tecnico_diagnostico,
            if (policy.isDeviceOwner) getString(R.string.si) else getString(R.string.no),
            if (KioskConfig.kioskEnabled(this)) getString(R.string.si) else getString(R.string.no),
            KioskConfig.APP_PACKAGE,
            appVersion,
            EssentialPackages.lockTaskPackages(this).joinToString(", "),
        )
        binding.actionToggleKiosk.text = if (KioskConfig.kioskEnabled(this)) {
            getString(R.string.tecnico_entrar_mantenimiento)
        } else {
            getString(R.string.tecnico_volver_kiosco)
        }

        // El PIN de fabrica esta publicado en el repositorio, que es publico:
        // mientras siga puesto, el panel no protege nada.
        binding.pinWarning.visibility =
            if (KioskConfig.isDefaultPin(this)) View.VISIBLE else View.GONE
    }

    private fun installedAppVersion(): String? = runCatching {
        val pm = packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(KioskConfig.APP_PACKAGE, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(KioskConfig.APP_PACKAGE, 0)
        }
        info.versionName
    }.getOrNull()

    private fun toast(message: String) =
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    companion object {
        private const val MIN_PIN_LENGTH = 4

        /** Duracion de la tregua al salir a Ajustes. */
        private const val MAINTENANCE_MINUTES = 10

        fun start(context: Context) {
            context.startActivity(Intent(context, TechnicianActivity::class.java))
        }
    }
}
