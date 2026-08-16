package com.diagnostruct.os.policy

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import com.diagnostruct.os.KioskConfig

/**
 * Calcula que paquetes deben seguir vivos para que DIAGNOSTRUCT funcione.
 *
 * No basta con permitir la propia aplicacion: es una app Capacitor y delega en
 * otros paquetes del sistema. La camara se abre con un intent a la aplicacion
 * de camara del equipo, la seleccion de fotos abre el selector de documentos, y
 * toda la interfaz se dibuja sobre el WebView del sistema. Si alguno de esos
 * paquetes se oculta o se deja fuera del modo bloqueado, la funcion
 * correspondiente deja de funcionar dentro del kiosco.
 */
object EssentialPackages {

    /** Paquetes que nunca deben ocultarse aunque tengan icono. */
    private val ALWAYS_KEEP = setOf(
        "android",
        "com.android.systemui",
        "com.android.settings",
        "com.android.providers.settings",
        "com.android.providers.media",
        "com.android.providers.media.module",
        "com.android.providers.downloads",
        "com.android.documentsui",
        "com.android.permissioncontroller",
        "com.android.packageinstaller",
        "com.google.android.permissioncontroller",
        "com.google.android.packageinstaller",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.certinstaller",
        "com.android.keychain",
        "com.android.shell",
    )

    /** Tiendas de aplicaciones: se ocultan salvo que se pida lo contrario. */
    private val STORES = setOf(
        "com.android.vending",
        "com.amazon.venezia",
        "com.sec.android.app.samsungapps",
        "com.huawei.appmarket",
    )

    /**
     * Conjunto completo de paquetes que el kiosco protege: la propia
     * aplicacion, este launcher, el WebView, la camara, el selector de
     * archivos, los teclados y la infraestructura del sistema.
     */
    fun protectedPackages(context: Context): Set<String> {
        val packages = mutableSetOf<String>()
        packages += context.packageName
        packages += KioskConfig.APP_PACKAGE
        packages += ALWAYS_KEEP
        packages += webViewProvider()
        packages += cameraApps(context)
        packages += filePickerApps(context)
        packages += inputMethods(context)
        if (!KioskConfig.hideStore(context)) packages += STORES
        return packages
    }

    /**
     * Paquetes autorizados a ejecutarse en modo bloqueado. Es un subconjunto de
     * los protegidos: aqui solo entra lo que el usuario puede llegar a abrir
     * desde la aplicacion, no toda la infraestructura del sistema.
     */
    fun lockTaskPackages(context: Context): Array<String> {
        val packages = mutableSetOf<String>()
        packages += context.packageName
        packages += KioskConfig.APP_PACKAGE
        packages += cameraApps(context)
        packages += filePickerApps(context)
        packages += "com.android.documentsui"
        packages += "com.android.permissioncontroller"
        packages += "com.google.android.permissioncontroller"
        return packages.filter { it.isNotBlank() }.toTypedArray()
    }

    /** Aplicacion que provee el WebView; sin ella la app Capacitor no arranca. */
    fun webViewProvider(): Set<String> {
        val providers = mutableSetOf(
            "com.google.android.webview",
            "com.android.webview",
            "com.android.chrome",
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { WebView.getCurrentWebViewPackage()?.packageName }
                .getOrNull()
                ?.let { providers += it }
        }
        return providers
    }

    /** Aplicaciones capaces de atender la captura de foto de Capacitor. */
    fun cameraApps(context: Context): Set<String> =
        resolveAll(context, Intent(MediaStore.ACTION_IMAGE_CAPTURE)) +
            resolveAll(context, Intent(MediaStore.ACTION_VIDEO_CAPTURE))

    /** Aplicaciones que atienden la seleccion de imagenes y documentos. */
    fun filePickerApps(context: Context): Set<String> {
        val getContent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val openDocument = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        val pick = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        return resolveAll(context, getContent) +
            resolveAll(context, openDocument) +
            resolveAll(context, pick)
    }

    /** Teclados instalados: ocultarlos dejaria el equipo sin entrada de texto. */
    fun inputMethods(context: Context): Set<String> {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            ?: return emptySet()
        return runCatching {
            imm.enabledInputMethodList.mapNotNull { it.packageName }.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * Aplicaciones con icono que el usuario podria abrir. Son las candidatas a
     * ocultarse; se excluyen las protegidas.
     */
    fun hideableLaunchers(context: Context): List<String> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val protected = protectedPackages(context)
        return resolveAll(context, launcherIntent)
            .filter { it !in protected }
            .sorted()
    }

    private fun resolveAll(context: Context, intent: Intent): Set<String> {
        val pm = context.packageManager
        val results: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        return results.mapNotNull { it.activityInfo?.packageName }.toSet()
    }

    /** Intent al panel de Wi-Fi, usado por el panel tecnico. */
    fun wifiSettingsIntent(): Intent =
        Intent(Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
