package com.diagnostruct.os.policy

import android.app.admin.DevicePolicyManager
import android.app.admin.SystemUpdatePolicy
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import com.diagnostruct.os.KioskConfig

/**
 * Traduce la politica del kiosco a llamadas concretas de [DevicePolicyManager].
 *
 * Todo lo que hay aqui requiere que el equipo sea Device Owner. Si no lo es,
 * cada metodo se queda corto en silencio y deja constancia en el registro: el
 * launcher sigue funcionando como pantalla de inicio, pero sin blindaje.
 */
class KioskPolicy(context: Context) {

    private val context = context.applicationContext
    private val dpm =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val admin: ComponentName = DiagnostructDeviceAdminReceiver.componentName(context)

    val isDeviceOwner: Boolean
        get() = runCatching { dpm.isDeviceOwnerApp(context.packageName) }.getOrDefault(false)

    /** Aplica la configuracion completa. Es idempotente. */
    fun applyAll() {
        if (!isDeviceOwner) {
            Log.w(TAG, "El equipo no es Device Owner: no se aplica ninguna politica")
            return
        }
        runStep("actividad de inicio") { setPersistentHome() }
        runStep("modo bloqueado") { configureLockTask() }
        runStep("permisos de la aplicacion") { grantAppPermissions() }
        runStep("restricciones de usuario") { applyUserRestrictions() }
        runStep("ajustes del sistema") { applySystemSettings() }
        runStep("proteccion de la aplicacion") { protectApp() }
        runStep("actualizaciones del sistema") { deferSystemUpdates() }
        runStep("ocultado de aplicaciones") { hideNonEssentialApps() }
    }

    /**
     * Fija este launcher como pantalla de inicio permanente. Sin esto el
     * sistema preguntaria que launcher usar en el primer pulsado de inicio.
     */
    private fun setPersistentHome() {
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val home = ComponentName(context, HOME_ACTIVITY)
        dpm.clearPackagePersistentPreferredActivities(admin, context.packageName)
        dpm.addPersistentPreferredActivity(admin, filter, home)
    }

    /**
     * Autoriza el anclaje de pantalla. La lista incluye la camara y el selector
     * de archivos porque la aplicacion los abre por intent: si no estan, la
     * captura de fotos falla al entrar en modo bloqueado.
     */
    private fun configureLockTask() {
        dpm.setLockTaskPackages(admin, EssentialPackages.lockTaskPackages(context))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Se conserva la informacion de bateria y cobertura, y el menu de
            // apagado: un equipo de campo que no se puede apagar es un problema.
            // Quedan fuera inicio, recientes, notificaciones y bloqueo.
            dpm.setLockTaskFeatures(
                admin,
                DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO or
                    DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
            )
        }
    }

    /**
     * Concede de antemano los permisos peligrosos que declara el APK, para que
     * el tecnico no vea dialogos de permisos en campo.
     */
    private fun grantAppPermissions() {
        dpm.setPermissionPolicy(admin, DevicePolicyManager.PERMISSION_POLICY_AUTO_GRANT)
        val target = KioskConfig.APP_PACKAGE
        if (!isInstalled(target)) {
            Log.w(TAG, "$target no esta instalado todavia; los permisos se daran al instalarlo")
            return
        }
        APP_PERMISSIONS.forEach { permission ->
            runCatching {
                dpm.setPermissionGrantState(
                    admin,
                    target,
                    permission,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                )
            }.onFailure { Log.w(TAG, "No se pudo conceder $permission", it) }
        }
    }

    private fun applyUserRestrictions() {
        RESTRICTIONS.forEach { restriction ->
            runCatching { dpm.addUserRestriction(admin, restriction) }
                .onFailure { Log.w(TAG, "Restriccion no aplicada: $restriction", it) }
        }
        // La depuracion USB es la unica via de rescate si el kiosco falla; se
        // bloquea solo cuando el despliegue lo pide expresamente.
        val block = KioskConfig.blockAdb(context)
        runCatching {
            if (block) {
                dpm.addUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            } else {
                dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES)
            }
        }
    }

    private fun applySystemSettings() {
        // Sin pantalla de bloqueo: el equipo arranca directo en la aplicacion.
        runCatching { dpm.setKeyguardDisabled(admin, true) }
        runCatching { dpm.setMaximumTimeToLock(admin, 0) }
        // Mientras esta conectado a corriente la pantalla no se apaga.
        runCatching {
            dpm.setGlobalSetting(
                admin,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                STAY_ON_ALL_SOURCES.toString()
            )
        }
        runCatching { dpm.setSecureSetting(admin, Settings.Secure.SKIP_FIRST_USE_HINTS, "1") }
    }

    private fun protectApp() {
        val target = KioskConfig.APP_PACKAGE
        if (!isInstalled(target)) return
        runCatching { dpm.setUninstallBlocked(admin, target, true) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Impide que se fuerce la detencion o se borren los datos desde
            // ajustes, que es como se pierden proyectos sin subir.
            runCatching {
                dpm.setUserControlDisabledPackages(admin, listOf(target, context.packageName))
            }
        }
    }

    private fun deferSystemUpdates() {
        // Las actualizaciones del fabricante se instalan de madrugada, nunca en
        // mitad de una inspeccion.
        runCatching {
            dpm.setSystemUpdatePolicy(
                admin,
                SystemUpdatePolicy.createWindowedInstallPolicy(WINDOW_START_MINUTES, WINDOW_END_MINUTES)
            )
        }
    }

    /**
     * Oculta toda aplicacion con icono que no sea imprescindible. Es una capa
     * adicional: el anclaje ya impide salir, pero ocultarlas evita ademas que
     * corran en segundo plano y que aparezcan en selectores.
     */
    fun hideNonEssentialApps() {
        if (!KioskConfig.hideOtherApps(context)) {
            showAllApps()
            return
        }
        EssentialPackages.hideableLaunchers(context).forEach { pkg ->
            runCatching { dpm.setApplicationHidden(admin, pkg, true) }
                .onFailure { Log.w(TAG, "No se pudo ocultar $pkg", it) }
        }
    }

    /** Devuelve la visibilidad a las aplicaciones ocultadas por el kiosco. */
    fun showAllApps() {
        if (!isDeviceOwner) return
        val pm = context.packageManager
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(
                PackageManager.ApplicationInfoFlags.of(PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
        }
        installed.forEach { info ->
            if (info.packageName == context.packageName) return@forEach
            runCatching { dpm.setApplicationHidden(admin, info.packageName, false) }
        }
    }

    /**
     * Levanta el kiosco por completo. Se usa desde el panel tecnico para poder
     * mantener el equipo sin tener que desaprovisionarlo.
     */
    fun suspendKiosk() {
        if (!isDeviceOwner) return
        runCatching { dpm.setLockTaskPackages(admin, arrayOf(context.packageName)) }
        runCatching { dpm.clearPackagePersistentPreferredActivities(admin, context.packageName) }
        runCatching { dpm.setKeyguardDisabled(admin, false) }
        RESTRICTIONS.forEach { runCatching { dpm.clearUserRestriction(admin, it) } }
        runCatching { dpm.clearUserRestriction(admin, UserManager.DISALLOW_DEBUGGING_FEATURES) }
        showAllApps()
    }

    /**
     * Deja el equipo como estaba y renuncia a la propiedad. Es irreversible sin
     * restablecer de fabrica y volver a aprovisionar.
     */
    fun releaseDevice() {
        if (!isDeviceOwner) return
        suspendKiosk()
        runCatching { dpm.setSystemUpdatePolicy(admin, null) }
        runCatching { dpm.setUninstallBlocked(admin, KioskConfig.APP_PACKAGE, false) }
        runCatching { dpm.clearDeviceOwnerApp(context.packageName) }
    }

    fun isInstalled(pkg: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
        }
        true
    }.getOrDefault(false)

    /**
     * Ejecuta un paso aislando su fallo: que una politica no se pueda aplicar
     * en un fabricante concreto no debe impedir que se apliquen las demas.
     */
    private fun runStep(name: String, step: () -> Unit) {
        runCatching(step).onFailure { Log.e(TAG, "Fallo aplicando: $name", it) }
    }

    companion object {
        private const val TAG = "KioskPolicy"
        private const val HOME_ACTIVITY = "com.diagnostruct.os.ui.KioskLauncherActivity"

        /** Cargador de red, USB e inalambrico a la vez. */
        private const val STAY_ON_ALL_SOURCES = 0b111

        /** Ventana de actualizacion del sistema: de 02:00 a 04:00. */
        private const val WINDOW_START_MINUTES = 2 * 60
        private const val WINDOW_END_MINUTES = 4 * 60

        private val APP_PERMISSIONS = listOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.POST_NOTIFICATIONS",
        )

        /**
         * Restricciones permanentes. No se restringe la configuracion de Wi-Fi:
         * el tecnico necesita cambiar de red en obra, y el panel protegido por
         * PIN es la via prevista para hacerlo.
         */
        private val RESTRICTIONS = buildList {
            add(UserManager.DISALLOW_FACTORY_RESET)
            add(UserManager.DISALLOW_SAFE_BOOT)
            add(UserManager.DISALLOW_ADD_USER)
            add(UserManager.DISALLOW_USB_FILE_TRANSFER)
            add(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            add(UserManager.DISALLOW_UNINSTALL_APPS)
            add(UserManager.DISALLOW_MODIFY_ACCOUNTS)
            add(UserManager.DISALLOW_CREATE_WINDOWS)
            add(UserManager.DISALLOW_SET_WALLPAPER)
            add(UserManager.DISALLOW_OUTGOING_BEAM)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                add(UserManager.DISALLOW_AIRPLANE_MODE)
                // La fecha del equipo sella los informes: no debe tocarse.
                add(UserManager.DISALLOW_CONFIG_DATE_TIME)
            }
        }
    }
}
