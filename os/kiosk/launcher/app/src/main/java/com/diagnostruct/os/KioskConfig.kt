package com.diagnostruct.os

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Ajustes del kiosco. Se guardan en el almacenamiento protegido por dispositivo
 * y no por usuario, para que el receptor de arranque y los trabajos en segundo
 * plano puedan leerlos en arranque directo, antes de que nadie desbloquee.
 */
object KioskConfig {

    private const val PREFS = "diagnostruct_os"

    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_PIN_SALT = "pin_salt"
    private const val KEY_FAILED_ATTEMPTS = "intentos_fallidos"
    private const val KEY_LOCKED_UNTIL = "bloqueado_hasta"
    private const val KEY_MAINTENANCE_UNTIL = "mantenimiento_hasta"
    private const val KEY_HIDE_OTHER_APPS = "ocultar_otras_apps"
    private const val KEY_BLOCK_ADB = "bloquear_adb"
    private const val KEY_AUTO_UPDATE = "actualizacion_automatica"
    private const val KEY_HIDE_STORE = "ocultar_tienda"
    private const val KEY_KIOSK_ENABLED = "kiosco_activo"
    private const val KEY_LAST_VERSION = "ultima_version_instalada"

    /**
     * PIN inicial, valido solo mientras no se haya fijado uno propio. Esta
     * publicado en este repositorio, asi que no protege nada: el panel avisa
     * mientras siga en uso.
     */
    const val DEFAULT_PIN = "285713"

    /** Unica aplicacion que el kiosco permite usar. */
    const val APP_PACKAGE = BuildConfig.APP_PACKAGE

    /** Manifiesto remoto de versiones publicadas. */
    const val VERSION_MANIFEST_URL = BuildConfig.VERSION_MANIFEST_URL

    // --- Derivacion del PIN --------------------------------------------------

    // Un PIN de seis cifras se agota por fuerza bruta en nada si se guarda como
    // un hash rapido. PBKDF2 encarece cada intento lo suficiente como para que
    // el ataque sin conexion, con el equipo abierto en la mano, deje de ser
    // cuestion de segundos.
    private const val PBKDF2_ITERATIONS = 50_000
    private const val PBKDF2_KEY_BITS = 256
    private const val SALT_BYTES = 16

    /** Intentos antes de empezar a penalizar. */
    private const val FREE_ATTEMPTS = 3
    private const val FIRST_LOCKOUT_MS = 30_000L
    private const val MAX_LOCKOUT_MS = 15 * 60_000L

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun derive(pin: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PBKDF2_ITERATIONS, PBKDF2_KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return Base64.encodeToString(factory.generateSecret(spec).encoded, Base64.NO_WRAP)
    }

    /** Cierto mientras el equipo siga con el PIN de fabrica. */
    fun isDefaultPin(context: Context): Boolean =
        prefs(context).getString(KEY_PIN_HASH, null) == null

    /**
     * Comprueba el PIN. La comparacion es de tiempo constante para no filtrar
     * cuantas cifras iniciales se acertaron.
     */
    fun verifyPin(context: Context, entered: String): Boolean {
        val store = prefs(context)
        val expected = store.getString(KEY_PIN_HASH, null)
            ?: return MessageDigest.isEqual(entered.toByteArray(), DEFAULT_PIN.toByteArray())
        val salt = store.getString(KEY_PIN_SALT, null)
            ?.let { Base64.decode(it, Base64.NO_WRAP) }
            ?: return false
        return MessageDigest.isEqual(
            derive(entered, salt).toByteArray(),
            expected.toByteArray(),
        )
    }

    fun setTechnicianPin(context: Context, pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        prefs(context).edit()
            .putString(KEY_PIN_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_PIN_HASH, derive(pin, salt))
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    // --- Freno a la fuerza bruta --------------------------------------------

    /**
     * Milisegundos que faltan para poder volver a probar, o cero.
     *
     * El reloj usado es el de tiempo desde el arranque: no se puede adelantar
     * cambiando la fecha, y un reinicio levanta el bloqueo, que es un
     * compromiso razonable para un equipo que el propio tecnico maneja.
     */
    fun lockoutRemainingMs(context: Context): Long {
        val until = prefs(context).getLong(KEY_LOCKED_UNTIL, 0L)
        return (until - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    /** Anota un intento fallido y devuelve la espera resultante. */
    fun registerFailedAttempt(context: Context): Long {
        val store = prefs(context)
        val attempts = store.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val penalized = attempts - FREE_ATTEMPTS
        val lockoutMs = if (penalized <= 0) {
            0L
        } else {
            // 30 s, 1 min, 2 min… hasta el tope.
            val factor = 1L shl (penalized - 1).coerceAtMost(10)
            (FIRST_LOCKOUT_MS * factor).coerceAtMost(MAX_LOCKOUT_MS)
        }
        store.edit()
            .putInt(KEY_FAILED_ATTEMPTS, attempts)
            .putLong(KEY_LOCKED_UNTIL, SystemClock.elapsedRealtime() + lockoutMs)
            .apply()
        return lockoutMs
    }

    fun clearFailedAttempts(context: Context) {
        prefs(context).edit()
            .remove(KEY_FAILED_ATTEMPTS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
    }

    // --- Ventana de mantenimiento -------------------------------------------

    /**
     * Tregua corta durante la que el kiosco no vuelve a anclar la aplicacion.
     *
     * Sin esto, abrir los ajustes de Wi-Fi desde el panel se auto-sabotea: al
     * soltar el anclaje el sistema avisa, el kiosco reacciona reabriendo la
     * aplicacion y expulsa al tecnico de Ajustes antes de que toque nada.
     */
    fun startMaintenanceWindow(context: Context, minutes: Int) {
        prefs(context).edit()
            .putLong(KEY_MAINTENANCE_UNTIL, SystemClock.elapsedRealtime() + minutes * 60_000L)
            .apply()
    }

    fun endMaintenanceWindow(context: Context) {
        prefs(context).edit().remove(KEY_MAINTENANCE_UNTIL).apply()
    }

    fun maintenanceRemainingMs(context: Context): Long {
        val until = prefs(context).getLong(KEY_MAINTENANCE_UNTIL, 0L)
        return (until - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
    }

    fun maintenanceActive(context: Context): Boolean = maintenanceRemainingMs(context) > 0L

    // --- Interruptores -------------------------------------------------------

    /** Oculta toda aplicacion con icono que no sea imprescindible. */
    fun hideOtherApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_OTHER_APPS, true)

    fun setHideOtherApps(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_HIDE_OTHER_APPS, value).apply()

    /**
     * Bloquea la depuracion USB. Desactivado por defecto: activarlo antes de
     * cerrar el despliegue deja el equipo sin via de rescate.
     */
    fun blockAdb(context: Context): Boolean =
        prefs(context).getBoolean(KEY_BLOCK_ADB, false)

    fun setBlockAdb(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_BLOCK_ADB, value).apply()

    /** Descarga e instala en silencio las versiones nuevas de la aplicacion. */
    fun autoUpdate(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTO_UPDATE, true)

    fun setAutoUpdate(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_AUTO_UPDATE, value).apply()

    /** Oculta la tienda de aplicaciones. No afecta a Play Services. */
    fun hideStore(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_STORE, true)

    fun setHideStore(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_HIDE_STORE, value).apply()

    /**
     * Interruptor general. El panel tecnico lo baja para poder mantener el
     * equipo; mientras esta en falso el kiosco no reancla nada.
     */
    fun kioskEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_KIOSK_ENABLED, true)

    fun setKioskEnabled(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_KIOSK_ENABLED, value).apply()

    fun lastInstalledVersion(context: Context): String =
        prefs(context).getString(KEY_LAST_VERSION, "") ?: ""

    fun setLastInstalledVersion(context: Context, value: String) =
        prefs(context).edit().putString(KEY_LAST_VERSION, value).apply()
}
