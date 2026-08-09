package com.diagnostruct.os

import android.content.Context
import android.content.SharedPreferences

/**
 * Ajustes del kiosco. Se guardan en el almacenamiento protegido por dispositivo
 * y no por usuario, para que el receptor de arranque y el vigilante puedan
 * leerlos en arranque directo, antes de que nadie desbloquee la tablet.
 */
object KioskConfig {

    private const val PREFS = "diagnostruct_os"

    private const val KEY_PIN = "pin_tecnico"
    private const val KEY_HIDE_OTHER_APPS = "ocultar_otras_apps"
    private const val KEY_BLOCK_ADB = "bloquear_adb"
    private const val KEY_AUTO_UPDATE = "actualizacion_automatica"
    private const val KEY_HIDE_STORE = "ocultar_tienda"
    private const val KEY_KIOSK_ENABLED = "kiosco_activo"
    private const val KEY_LAST_VERSION = "ultima_version_instalada"

    /** PIN inicial del panel tecnico. Debe cambiarse durante el despliegue. */
    const val DEFAULT_PIN = "285713"

    /** Unica aplicacion que el kiosco permite usar. */
    const val APP_PACKAGE = BuildConfig.APP_PACKAGE

    /** Manifiesto remoto de versiones publicadas. */
    const val VERSION_MANIFEST_URL = BuildConfig.VERSION_MANIFEST_URL

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext
            .createDeviceProtectedStorageContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun technicianPin(context: Context): String =
        prefs(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setTechnicianPin(context: Context, pin: String) =
        prefs(context).edit().putString(KEY_PIN, pin).apply()

    /** Oculta toda aplicacion con icono que no sea imprescindible. */
    fun hideOtherApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_OTHER_APPS, true)

    fun setHideOtherApps(context: Context, value: Boolean) =
        prefs(context).edit().putBoolean(KEY_HIDE_OTHER_APPS, value).apply()

    /**
     * Bloquea la depuracion USB. Desactivado por defecto: activarlo antes de
     * cerrar el despliegue deja el equipo sin via de rescate por ADB.
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
     * equipo; mientras esta en falso el vigilante deja de reanclar.
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
