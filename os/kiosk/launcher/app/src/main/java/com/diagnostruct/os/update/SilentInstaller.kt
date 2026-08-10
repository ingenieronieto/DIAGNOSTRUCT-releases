package com.diagnostruct.os.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Instala el APK sin intervencion del usuario.
 *
 * El equipo es Device Owner, asi que el sistema acepta la sesion de instalacion
 * sin mostrar dialogo. El APK se transmite directamente desde la red a la
 * sesion, sin escribirlo antes en disco: estas tablets se quedan sin espacio con
 * facilidad y una copia intermedia de 16 MB es evitable.
 */
object SilentInstaller {

    private const val TAG = "SilentInstaller"
    private const val CONNECT_TIMEOUT_MS = 30_000
    private const val READ_TIMEOUT_MS = 120_000
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_REDIRECTS = 5

    const val ACTION_INSTALL_RESULT = "com.diagnostruct.os.INSTALL_RESULT"
    const val EXTRA_VERSION = "version"

    sealed interface Result {
        /** La sesion se entrego al sistema; el desenlace llega al receptor. */
        data object Started : Result
        data class Failed(val reason: String) : Result
    }

    suspend fun downloadAndInstall(
        context: Context,
        apkUrl: String,
        version: String,
    ): Result = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        try {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setAppPackageName(com.diagnostruct.os.KioskConfig.APP_PACKAGE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    setInstallReason(PackageManager.INSTALL_REASON_POLICY)
                }
            }

            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                val connection = openConnection(apkUrl)
                val declaredLength = connection.contentLengthLong
                try {
                    connection.inputStream.use { input ->
                        session.openWrite(APK_NAME, 0, declaredLength).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var copied = 0L
                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                copied += read
                            }
                            session.fsync(output)
                            Log.i(TAG, "Descargados $copied bytes de la version $version")
                            if (copied == 0L) error("La descarga vino vacia")
                            if (declaredLength > 0 && copied != declaredLength) {
                                error("Descarga incompleta: $copied de $declaredLength bytes")
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
                session.commit(buildStatusIntent(context, sessionId, version).intentSender)
            }
            Result.Started
        } catch (t: Throwable) {
            if (sessionId != -1) runCatching { installer.abandonSession(sessionId) }
            Log.e(TAG, "Fallo la instalacion de la version $version", t)
            Result.Failed(t.message ?: t::class.java.simpleName)
        }
    }

    /** Descarga el manifiesto de versiones. */
    suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
        val connection = openConnection(url)
        try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Abre la conexion siguiendo redirecciones a mano. `HttpURLConnection` no
     * sigue por si solo los saltos entre http y https, y las descargas de
     * GitHub acaban en un dominio distinto del inicial.
     */
    private fun openConnection(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(MAX_REDIRECTS) {
            // Se comprueba el esquema ANTES de abrir nada: asi un destino en
            // claro no llega siquiera a establecer conexion.
            require(current.protocol.equals("https", ignoreCase = true)) {
                "Solo se aceptan descargas por HTTPS"
            }
            val connection = (current.openConnection() as HttpsURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "DiagnostructOS")
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> return connection
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                TEMPORARY_REDIRECT,
                PERMANENT_REDIRECT -> {
                    val location = connection.getHeaderField("Location")
                    connection.disconnect()
                    requireNotNull(location) { "Redireccion sin destino" }
                    current = URL(current, location)
                }
                else -> {
                    connection.disconnect()
                    error("El servidor respondio $code")
                }
            }
        }
        error("Demasiadas redirecciones")
    }

    private fun buildStatusIntent(context: Context, sessionId: Int, version: String): PendingIntent {
        val intent = Intent(context, InstallResultReceiver::class.java).apply {
            action = ACTION_INSTALL_RESULT
            putExtra(EXTRA_VERSION, version)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, sessionId, intent, flags)
    }

    private const val APK_NAME = "diagnostruct.apk"
    private const val TEMPORARY_REDIRECT = 307
    private const val PERMANENT_REDIRECT = 308
}
