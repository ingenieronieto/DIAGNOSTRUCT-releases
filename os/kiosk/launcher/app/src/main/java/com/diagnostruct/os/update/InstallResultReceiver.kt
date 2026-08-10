package com.diagnostruct.os.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.diagnostruct.os.KioskConfig
import com.diagnostruct.os.kiosk.AppLauncher
import com.diagnostruct.os.policy.PolicyScheduler

/** Recoge el desenlace de cada sesion de instalacion. */
class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        val version = intent.getStringExtra(SilentInstaller.EXTRA_VERSION).orEmpty()

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> onInstalled(context, version)

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Solo ocurre si el equipo aun no es Device Owner. Se muestra el
                // dialogo del sistema como respaldo para poder seguir probando.
                Log.w(TAG, "La instalacion requiere confirmacion manual")
                confirmationIntent(intent)?.let { confirm ->
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { Log.e(TAG, "No se pudo pedir confirmacion", it) }
                }
            }

            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.e(TAG, "Fallo la instalacion de $version (estado $status): $message")
            }
        }
    }

    /**
     * Se comprueba la firma ANTES de conceder permisos o abrir la aplicacion.
     * Si no es la esperada, lo instalado se retira sin llegar a ejecutarse.
     */
    private fun onInstalled(context: Context, version: String) {
        val target = KioskConfig.APP_PACKAGE

        if (!SignaturePin.matches(context, target)) {
            Log.e(TAG, "$target quedo instalado con una firma que no es la esperada; se retira")
            uninstall(context, target)
            return
        }

        Log.i(TAG, "Version $version instalada")
        KioskConfig.setLastInstalledVersion(context, version)
        // La aplicacion recien instalada vuelve a necesitar sus permisos
        // concedidos de antemano y su bloqueo de desinstalacion.
        runCatching { PolicyScheduler.applyNow(context) }
        AppLauncher.returnToKiosk(context)
    }

    private fun uninstall(context: Context, pkg: String) {
        runCatching {
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    android.app.PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
            val pending = android.app.PendingIntent.getBroadcast(context, 1, intent, flags)
            context.packageManager.packageInstaller.uninstall(pkg, pending.intentSender)
        }.onFailure { Log.e(TAG, "No se pudo retirar $pkg", it) }
    }

    private fun confirmationIntent(intent: Intent): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private companion object {
        const val TAG = "InstallResult"
    }
}
