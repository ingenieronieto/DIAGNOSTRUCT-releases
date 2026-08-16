package com.diagnostruct.os.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.diagnostruct.os.policy.PolicyScheduler
import com.diagnostruct.os.update.UpdateScheduler


/**
 * Reconstruye el kiosco en cada arranque. La politica ya es persistente en el
 * sistema, pero volver a aplicarla cuesta poco y cubre los casos en los que una
 * actualizacion del fabricante ha revertido parte de la configuracion.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            return
        }
        Log.i(TAG, "Arranque detectado: $action")

        // En arranque directo el almacenamiento del usuario aun no esta
        // disponible y WorkManager no puede consultar su base de datos; se
        // espera al arranque completo para encolar nada.
        if (action == Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        runCatching { PolicyScheduler.applyNow(context) }
            .onFailure { Log.e(TAG, "No se pudo encolar la politica en el arranque", it) }
        runCatching { UpdateScheduler.schedule(context) }

        // No se lanza nada desde aqui: el sistema abre por su cuenta la
        // actividad de inicio al terminar el arranque, y ademas Android bloquea
        // el arranque de actividades en segundo plano desde un receptor.
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
