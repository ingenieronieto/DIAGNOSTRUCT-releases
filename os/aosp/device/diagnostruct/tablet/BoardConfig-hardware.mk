# Plantilla de configuracion de placa. NO la lee el build.
#
# El nombre lleva sufijo a proposito. El build solo carga un archivo llamado
# exactamente `BoardConfig.mk`, y solo desde un directorio que se llame igual
# que `PRODUCT_DEVICE`. El producto de referencia hereda el dispositivo del
# objetivo generico de AOSP, asi que usa la placa de ese objetivo y este archivo
# queda fuera; llamarlo `BoardConfig.mk` haria creer que esta en uso.
#
# Al pasar a la tablet definitiva: parta del BoardConfig del arbol de
# dispositivo del fabricante y añada lo que sigue, que son los minimos que el
# sistema DIAGNOSTRUCT da por hechos.

# La aplicacion trae bibliotecas nativas para arm64-v8a, armeabi-v7a, x86 y
# x86_64, asi que cualquiera de esas arquitecturas sirve. En 64 bits hay que
# mantener el soporte de 32 bits: parte del codigo nativo de Capacitor y de
# Play Services todavia se carga en esa forma.
TARGET_SUPPORTS_64_BIT_APPS := true
TARGET_SUPPORTS_32_BIT_APPS := true

# El almacenamiento cifrado por archivo es lo que protege los informes y las
# fotos si la tablet se pierde en obra.
TARGET_USES_FILE_BASED_ENCRYPTION := true

# Arranque verificado: sin el, el blindaje del kiosco se salta arrancando otra
# imagen. Exige claves propias, generadas fuera de este repositorio.
BOARD_AVB_ENABLE := true

# Sin politica SELinux propia: el sistema solo añade aplicaciones normales, que
# quedan cubiertas por los dominios de AOSP. Si mas adelante se añade un
# demonio nativo, cree aqui `sepolicy/` y declare BOARD_SEPOLICY_DIRS.
