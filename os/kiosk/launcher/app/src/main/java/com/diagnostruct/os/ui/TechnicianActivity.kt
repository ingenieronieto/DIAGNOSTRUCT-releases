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
import com.diagnostruct.os.kiosk.AppLauncher
import com.diagnostruct.os.policy.EssentialPackages
import com.diagnostruct.os.policy.KioskPolicy
import com.diagnostruct.os.policy.PolicyScheduler
import com.diagnostruct.os.update.UpdateScheduler

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
        binding.actionOpenApp.setOnClickListener {
            AppLauncher.launchApp(this)
            finish()
        }
    }

    private fun attemptUnlock() {
        val entered = binding.pinInput.text?.toString().orEmpty()
        if (entered != KioskConfig.technicianPin(this)) {
            binding.pinInput.text?.clear()
            toast(getString(R.string.tecnico_pin_incorrecto))
            return
        }
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
     * Ajustes de Wi-Fi. Hay que soltar el anclaje antes: Ajustes no esta en la
     * lista de aplicaciones autorizadas y el sistema bloquearia el intento.
     */
    private fun openWifiSettings() {
        runCatching { stopLockTask() }
        runCatching { startActivity(EssentialPackages.wifiSettingsIntent()) }
            .onFailure { toast(getString(R.string.tecnico_sin_ajustes_wifi)) }
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
        fun start(context: Context) {
            context.startActivity(Intent(context, TechnicianActivity::class.java))
        }
    }
}
