package com.diagnostruct.os.policy

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.util.Log
import com.diagnostruct.os.KioskConfig
import com.diagnostruct.os.kiosk.AppLauncher
import com.diagnostruct.os.update.UpdateScheduler

/**
 * Administrador del dispositivo. El sistema lo activa al terminar el alta (QR,
 * NFC o `dpm set-device-owner`) y es el punto desde el que arranca todo el
 * blindaje del equipo.
 */
class DiagnostructDeviceAdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, "Administrador activado")
        applyAndStart(context)
    }

    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        Log.i(TAG, "Aprovisionamiento completado")
        applyProvisioningExtras(context, intent)
        applyAndStart(context)
    }

    override fun onLockTaskModeEntering(context: Context, intent: Intent, pkg: String) {
        Log.i(TAG, "Modo bloqueado activo en $pkg")
    }

    override fun onLockTaskModeExiting(context: Context, intent: Intent) {
        Log.w(TAG, "Se salio del modo bloqueado")

        // Si la salida la pidio el panel tecnico hay una tregua abierta, y
        // reabrir ahora expulsaria al tecnico de donde acaba de entrar.
        if (!KioskConfig.kioskEnabled(context) || KioskConfig.maintenanceActive(context)) return

        // Intento de mejor esfuerzo: desde un receptor, Android 10 y posteriores
        // suelen bloquear el arranque de actividades en segundo plano. La via
        // que si funciona es la pantalla de inicio, que reancla al reanudarse.
        AppLauncher.returnToKiosk(context)
    }

    /**
     * Lee los parametros que viajan dentro del QR de alta. Hoy solo se usa el
     * PIN de mantenimiento: asi cada lote de tablets se puede dar de alta con
     * su propio PIN sin recompilar el launcher.
     */
    private fun applyProvisioningExtras(context: Context, intent: Intent) {
        val extras: PersistableBundle? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(
                    DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                    PersistableBundle::class.java,
                )
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)
            }

        val pin = extras?.getString(EXTRA_PIN)?.trim().orEmpty()
        if (pin.length < MIN_PIN_LENGTH) {
            if (pin.isNotEmpty()) Log.w(TAG, "El PIN del alta es demasiado corto; se ignora")
            return
        }
        KioskConfig.setTechnicianPin(context, pin)
        Log.i(TAG, "PIN de mantenimiento tomado del alta")
    }

    private fun applyAndStart(context: Context) {
        PolicyScheduler.applyNow(context)
        UpdateScheduler.schedule(context)
        AppLauncher.returnToKiosk(context)
    }

    companion object {
        private const val TAG = "DiagnostructAdmin"
        private const val EXTRA_PIN = "pin_tecnico"
        private const val MIN_PIN_LENGTH = 4

        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, DiagnostructDeviceAdminReceiver::class.java)
    }
}
