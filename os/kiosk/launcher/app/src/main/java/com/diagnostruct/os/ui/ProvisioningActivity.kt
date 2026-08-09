package com.diagnostruct.os.ui

import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.diagnostruct.os.R
import com.diagnostruct.os.databinding.ActivityProvisioningBinding
import com.diagnostruct.os.kiosk.AppLauncher
import com.diagnostruct.os.policy.KioskPolicy
import com.diagnostruct.os.policy.PolicyScheduler
import com.diagnostruct.os.update.UpdateScheduler

/**
 * Pantallas del alta gestionada.
 *
 * El sistema llama aqui dos veces durante el aprovisionamiento por QR o NFC:
 * primero para preguntar en que modo se administra el equipo, y despues para
 * que el administrador confirme que ya ha dejado el equipo conforme.
 */
class ProvisioningActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProvisioningBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProvisioningBinding.inflate(layoutInflater)
        setContentView(binding.root)

        when (intent?.action) {
            ACTION_GET_PROVISIONING_MODE -> replyFullyManaged()
            ACTION_ADMIN_POLICY_COMPLIANCE -> finishSetup()
            else -> finishSetup()
        }
    }

    /** Este producto solo tiene sentido como equipo totalmente gestionado. */
    private fun replyFullyManaged() {
        val result = Intent()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            result.putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_MODE,
                DevicePolicyManager.PROVISIONING_MODE_FULLY_MANAGED_DEVICE,
            )
        }
        setResult(RESULT_OK, result)
        finish()
    }

    /** Aplica la politica y devuelve el control al asistente del sistema. */
    private fun finishSetup() {
        binding.status.text = getString(R.string.provision_aplicando)
        binding.progress.visibility = View.VISIBLE

        val policy = KioskPolicy(this)
        runCatching { PolicyScheduler.applyNow(this) }
        runCatching { UpdateScheduler.schedule(this) }

        binding.progress.visibility = View.GONE
        binding.status.text = if (policy.isDeviceOwner) {
            getString(R.string.provision_listo)
        } else {
            getString(R.string.provision_sin_propiedad)
        }

        setResult(RESULT_OK)
        AppLauncher.returnToKiosk(this)
        finish()
    }

    private companion object {
        const val ACTION_GET_PROVISIONING_MODE = "android.app.action.GET_PROVISIONING_MODE"
        const val ACTION_ADMIN_POLICY_COMPLIANCE = "android.app.action.ADMIN_POLICY_COMPLIANCE"
    }
}
