package com.diagnostruct.os.update

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import android.util.Log
import com.diagnostruct.os.BuildConfig
import java.security.MessageDigest

/**
 * Comprueba que la aplicacion instalada esta firmada con el certificado
 * esperado.
 *
 * Importa en la primera instalacion. Al actualizar, Android ya rechaza un APK
 * cuya firma no coincida con la de lo que hay puesto; pero cuando no hay nada
 * instalado no existe esa referencia, y el kiosco instala en silencio lo que le
 * sirva la URL y ademas le concede camara y ubicacion. Este anclaje cierra esa
 * ventana.
 */
object SignaturePin {

    private const val TAG = "SignaturePin"

    /** Huella esperada, en hexadecimal. Vacia desactiva la comprobacion. */
    private val expected: String = BuildConfig.APP_SIGNATURE_SHA256.lowercase()

    val isConfigured: Boolean get() = expected.isNotBlank()

    /**
     * Cierto si [pkg] esta firmado con el certificado esperado. Tambien cierto
     * si no hay huella configurada, para no bloquear despliegues internos con
     * una compilacion propia de la aplicacion.
     */
    fun matches(context: Context, pkg: String): Boolean {
        if (!isConfigured) return true

        val signatures = signaturesOf(context, pkg)
        if (signatures.isEmpty()) {
            Log.w(TAG, "No se pudo leer la firma de $pkg")
            return false
        }
        return signatures.any { firma ->
            val huella = sha256Hex(firma.toByteArray())
            if (huella != expected) Log.w(TAG, "Firma inesperada en $pkg: $huella")
            huella == expected
        }
    }

    private fun signaturesOf(context: Context, pkg: String): List<Signature> = runCatching {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo ?: return emptyList()
            // Se aceptan tanto los firmantes actuales como el historial de
            // rotacion: si algun dia se cambia la clave de firma de la
            // aplicacion, las tablets ya desplegadas la seguiran reconociendo.
            (signing.apkContentsSigners.orEmpty() + signing.signingCertificateHistory.orEmpty())
                .toList()
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES).signatures.orEmpty().toList()
        }
    }.getOrElse {
        Log.w(TAG, "No se pudo consultar la firma de $pkg", it)
        emptyList()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
