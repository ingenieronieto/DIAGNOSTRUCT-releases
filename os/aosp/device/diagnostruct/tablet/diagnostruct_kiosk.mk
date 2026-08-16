# Capa DIAGNOSTRUCT: todo lo que convierte un Android cualquiera en el sistema
# de las tablets de campo. No depende del hardware, de modo que el arbol de
# dispositivo del fabricante puede heredarla tal cual:
#
#     $(call inherit-product, device/diagnostruct/tablet/diagnostruct_kiosk.mk)

# ---------------------------------------------------------------------------
# Aplicaciones
# ---------------------------------------------------------------------------

# DiagnostructOS declara `overrides` sobre los launchers de AOSP, asi que al
# incluirlo desaparecen del build y este pasa a ser el unico inicio posible.
PRODUCT_PACKAGES += \
    DiagnostructOS \
    DiagnostructSDK \
    privapp-permissions-diagnostruct.xml \
    default-permissions-diagnostruct.xml

# Aplicaciones que la base de AOSP arrastra y que en una tablet de auscultacion
# solo son superficie de ataque y espacio ocupado. La lista NO se aplica aqui:
# `filter-out` solo funciona cuando ya han corrido todas las herencias, asi que
# el producto final la aplica al final de su makefile (ver diagnostruct_tablet.mk).
#
# Ajuste la lista a su rama: un nombre que no exista simplemente no filtra nada,
# pero uno que si exista y sea necesario deja el sistema cojo.
DIAGNOSTRUCT_PAQUETES_SOBRANTES := \
    Browser2 \
    Calendar \
    Contacts \
    DeskClock \
    Email \
    Gallery2 \
    Music \
    QuickSearchBox \
    Traceur \
    WallpaperPicker

# ---------------------------------------------------------------------------
# Lo que DIAGNOSTRUCT necesita para funcionar
# ---------------------------------------------------------------------------

# La aplicacion es Capacitor: toda su interfaz se dibuja sobre el WebView. Sin
# proveedor de WebView el equipo arranca pero la aplicacion muestra una pantalla
# en blanco.
PRODUCT_PACKAGES += \
    webview

# La captura de fotos se hace lanzando un intent a la aplicacion de camara: si
# no hay ninguna instalada, el modulo de fotos falla en obra. Se conserva una
# camara y el selector de documentos, que es lo que abre la galeria.
PRODUCT_PACKAGES += \
    Camera2 \
    DocumentsUI

# Teclado: sin el no se pueden rellenar los ensayos.
PRODUCT_PACKAGES += \
    LatinIME

# ---------------------------------------------------------------------------
# Comportamiento del sistema
# ---------------------------------------------------------------------------

# Estas tablets se encienden en obra, sin nadie mirando el asistente inicial.
PRODUCT_PRODUCT_PROPERTIES += \
    ro.diagnostruct.kiosk=1 \
    ro.setupwizard.mode=DISABLED \
    setupwizard.feature.skip_button_use_mobile_data=false \
    ro.com.android.dataroaming=false

# Aqui NO se toca persist.sys.usb.config: dejar el USB en `none` desde el
# producto puede quitar tambien ADB, que es la unica via de rescate si el kiosco
# falla. El acceso a archivos por USB ya lo cierra DISALLOW_USB_FILE_TRANSFER,
# que ademas se puede levantar desde el panel tecnico.

PRODUCT_PACKAGE_OVERLAYS += \
    device/diagnostruct/tablet/overlay

# ---------------------------------------------------------------------------
# Idioma
# ---------------------------------------------------------------------------

# `ro.product.locale` la deriva el build de esta lista; fijarla ademas a mano
# genera una propiedad duplicada.
PRODUCT_LOCALES := es_CO es_ES en_US
