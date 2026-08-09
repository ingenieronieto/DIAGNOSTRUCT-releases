# Productos que expone este arbol.
#
# `diagnostruct_tablet` es un producto de referencia sobre el emulador, pensado
# para poder probar el sistema antes de tener el hardware definitivo. Cuando la
# tablet este elegida, cree junto a el un producto propio que herede el mismo
# `diagnostruct_kiosk.mk` sobre el arbol de dispositivo del fabricante.

PRODUCT_MAKEFILES := \
    $(LOCAL_DIR)/diagnostruct_tablet.mk

# El nombre de la configuracion de release (`trunk_staging`) depende de la rama
# de AOSP. Ejecute `lunch` sin argumentos para ver las validas en la suya.
COMMON_LUNCH_CHOICES := \
    diagnostruct_tablet-trunk_staging-userdebug \
    diagnostruct_tablet-trunk_staging-user
