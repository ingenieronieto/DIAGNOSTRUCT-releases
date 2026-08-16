# Producto de referencia de DIAGNOSTRUCT OS.
#
# Se apoya en el objetivo generico de AOSP para poder compilar y arrancar en el
# emulador sin hardware. Sirve para dos cosas: validar la capa del kiosco antes
# de elegir la tablet, y servir de plantilla del producto definitivo.
#
# Para la tablet real, copie este archivo, cambie la herencia de la primera
# seccion por el arbol de dispositivo del fabricante y deje intactas las otras
# dos. Toda la personalizacion de DIAGNOSTRUCT vive en `diagnostruct_kiosk.mk`.

# ---------------------------------------------------------------------------
# 1. Base del sistema  (esto es lo unico que cambia al pasar a hardware real)
# ---------------------------------------------------------------------------
#
# El nombre del objetivo generico cambia entre ramas de AOSP. Compruebe el
# suyo con `lunch` antes de compilar; en ramas recientes suele ser
# `aosp_x86_64` para emulador y `aosp_arm64` para ARM.

$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_x86_64.mk)

# ---------------------------------------------------------------------------
# 2. Capa DIAGNOSTRUCT
# ---------------------------------------------------------------------------

$(call inherit-product, device/diagnostruct/tablet/diagnostruct_kiosk.mk)

# ---------------------------------------------------------------------------
# 3. Identidad del producto
# ---------------------------------------------------------------------------

# `PRODUCT_DEVICE` NO se define aqui, y es a proposito.
#
# El build resuelve la configuracion de placa buscando
# `device/*/$(TARGET_DEVICE)/BoardConfig.mk`, es decir, exige que exista un
# directorio con el mismo nombre que el dispositivo. Fijarlo a
# `diagnostruct_tablet` mientras el arbol vive en `device/diagnostruct/tablet/`
# rompe el build con "No config file found for TARGET_DEVICE". Heredando el del
# objetivo generico se usa su placa, que es justo lo que se quiere en el
# producto de emulador.
#
# En el producto de hardware real, defina aqui el dispositivo del fabricante y
# use su BoardConfig; `BoardConfig-hardware.mk` es la plantilla de referencia.

PRODUCT_NAME := diagnostruct_tablet
PRODUCT_BRAND := DIAGNOSTRUCT
PRODUCT_MODEL := DIAGNOSTRUCT Field Tablet
PRODUCT_MANUFACTURER := INGNIETO

# ---------------------------------------------------------------------------
# 4. Poda final
# ---------------------------------------------------------------------------
#
# Va al final a proposito: `filter-out` solo puede quitar lo que ya esta en la
# lista, y las herencias de arriba son las que la llenan.

PRODUCT_PACKAGES := $(filter-out $(DIAGNOSTRUCT_PAQUETES_SOBRANTES),$(PRODUCT_PACKAGES))
