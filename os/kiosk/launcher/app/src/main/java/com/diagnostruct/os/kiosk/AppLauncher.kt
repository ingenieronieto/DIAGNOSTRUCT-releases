package com.diagnostruct.os.kiosk

import android.content.Context
import android.content.Intent
import android.util.Log
import com.diagnostruct.os.KioskConfig
import com.diagnostruct.os.ui.KioskLauncherActivity

/** Arranque de la aplicacion de campo y regreso a la pantalla de inicio. */
object AppLauncher {

    private const val TAG = "AppLauncher"

    /** Intent de arranque de DIAGNOSTRUCT, o null si no esta instalada. */
    fun appIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(KioskConfig.APP_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Se reutiliza la tarea existente en lugar de recrearla, para no
            // perder el formulario a medio rellenar cuando se vuelve del
            // selector de fotos o de la camara.
            addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        }

    fun isAppInstalled(context: Context): Boolean = appIntent(context) != null

    /**
     * Vuelve a la pantalla de inicio del kiosco, que reanclara la aplicacion.
     *
     * Nadie abre la aplicacion directamente salvo la pantalla de inicio: es la
     * unica que sabe pedir el anclaje al lanzar. Abrirla desde otro sitio la
     * dejaria suelta y el kiosco se quedaria sin efecto.
     */
    fun returnToKiosk(context: Context) {
        val intent = Intent(context, KioskLauncherActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "No se pudo volver a la pantalla de inicio", it) }
    }
}
