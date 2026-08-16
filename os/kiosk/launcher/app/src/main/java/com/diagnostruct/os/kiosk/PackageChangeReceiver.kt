package com.diagnostruct.os.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.diagnostruct.os.policy.KioskPolicy
import com.diagnostruct.os.policy.PolicyScheduler
import com.diagnostruct.os.update.UpdateScheduler

/**
 * Reaplica la politica cuando cambia el conjunto de aplicaciones instaladas.
 *
 * Importa por dos motivos: una aplicacion recien instalada aparece visible y
 * hay que ocultarla, y una actualizacion de DIAGNOSTRUCT reinicia el estado de
 * los permisos que se le habian concedido de antemano.
 */
class PackageChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "Cambio de paquetes (${intent.action}): ${intent.data?.schemeSpecificPart}")

        if (!KioskPolicy(context).isDeviceOwner) return

        runCatching { PolicyScheduler.applyNow(context) }
            .onFailure { Log.e(TAG, "No se pudo encolar la politica", it) }

        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            runCatching { UpdateScheduler.schedule(context) }
            AppLauncher.returnToKiosk(context)
        }
    }

    private companion object {
        const val TAG = "PackageChange"
    }
}
