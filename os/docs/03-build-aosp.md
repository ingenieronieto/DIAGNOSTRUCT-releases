# Compilar la ROM

Esta capa construye una imagen de Android que solo contiene lo que DIAGNOSTRUCT
necesita. Es el "sistema operativo nativo" en sentido estricto.

## Antes de empezar: lo que hay que aceptar

| Coste | Detalle |
|---|---|
| Espacio | ~250 GB de codigo fuente y ~150 GB de salida de compilacion. |
| Tiempo | La primera compilacion tarda entre 2 y 6 horas segun la maquina. |
| Hardware | Hace falta el **arbol de dispositivo y los blobs del fabricante**. Sin ellos no hay imagen que arranque en la tablet. |
| Play Services | AOSP no lo trae. Sin el, `@capacitor/geolocation` se queda sin coordenadas. |

Ese ultimo punto es el que decide si esta capa conviene. Ver *Que hacer con la
ubicacion* mas abajo.

## Requisitos de la maquina de compilacion

- Linux (Ubuntu 22.04 o posterior).
- 16 GB de RAM como minimo; 64 GB para que sea comodo.
- 400 GB de disco libre, preferiblemente SSD.
- `repo`, `git`, `openjdk-21-jdk`, `python3`, y el resto de dependencias que
  documenta AOSP.

## Sincronizar el arbol

```bash
mkdir -p ~/aosp && cd ~/aosp
repo init -u https://android.googlesource.com/platform/manifest -b android-latest-release
repo sync -c -j8            # varias horas la primera vez
```

Enganche el arbol de producto de DIAGNOSTRUCT:

```bash
mkdir -p .repo/local_manifests
cp /ruta/a/DIAGNOSTRUCT-releases/os/aosp/manifests/diagnostruct.xml .repo/local_manifests/
repo sync -c device/diagnostruct/releases
```

O, mas comodo mientras se itera, deje que `build.sh` lo copie con `rsync`.

## Compilar

```bash
cd os/kiosk/launcher && ./gradlew assembleRelease   # el launcher, firmado
cd -

export ANDROID_BUILD_TOP=~/aosp
./os/aosp/build.sh -j$(nproc)
```

El script copia el arbol de producto, coloca los dos APK y lanza
`lunch diagnostruct_tablet-trunk_staging-userdebug` seguido de `m`.

Salida en `~/aosp/out/target/product/diagnostruct_tablet/`.

## Probar sin hardware

El producto de referencia esta construido sobre el objetivo generico, asi que
arranca en el emulador:

```bash
cd ~/aosp && source build/envsetup.sh
lunch diagnostruct_tablet-trunk_staging-userdebug
emulator
```

Sirve para validar la capa del kiosco entera —pantalla de inicio, politica,
ocultado, actualizador— antes de tener la tablet.

## Pasar a la tablet real

Solo cambia una cosa. En `diagnostruct_tablet.mk`, la seccion 1:

```makefile
# Antes (emulador)
$(call inherit-product, $(SRC_TARGET_DIR)/product/aosp_x86_64.mk)

# Despues (tablet del fabricante)
$(call inherit-product, device/<fabricante>/<modelo>/device.mk)
```

Y `BoardConfig.mk` pasa a ser el del fabricante. Las secciones 2 y 3 y todo
`diagnostruct_kiosk.mk` se quedan igual: esa es la razon de separarlos.

## Que aporta esta capa sobre el kiosco

| | Kiosco | ROM |
|---|---|---|
| Otras aplicaciones | Ocultas | No existen en la imagen |
| Otro launcher | Desplazado | No se compila (`overrides`) |
| Asistente inicial | Se pasa | Desactivado (`ro.setupwizard.mode=DISABLED`) |
| Permisos de la aplicacion | Concedidos por politica | Concedidos en el primer arranque |
| Arranque verificado | Del fabricante | Con clave propia |
| Actualizaciones del fabricante | Llegan | Bajo su control |

## Que hacer con la ubicacion

AOSP no incluye Google Play Services, y `@capacitor/geolocation` lo usa. Hay
tres salidas, en orden de menos a mas trabajo:

1. **Quedarse en la capa de kiosco.** Conserva Play Services tal cual. Es la
   razon principal por la que esa capa es la via recomendada.
2. **Integrar microG** en la imagen, con parche de firma falsificada. Reimplanta
   el proveedor fusionado y `@capacitor/geolocation` funciona sin cambios.
3. **Cambiar la aplicacion** para que use el `LocationManager` de Android en vez
   del proveedor fusionado. Es la solucion limpia y sin dependencias de Google,
   pero toca el repositorio privado de DIAGNOSTRUCT, no este.

La tercera es la unica que deja el sistema realmente libre de Google. Si la ROM
se plantea en serio, conviene abordarla en paralelo.

## Firma de la imagen

Para produccion hay que generar claves propias y firmar la imagen; las claves de
prueba de AOSP son publicas y cualquiera puede firmar con ellas. El
procedimiento (`development/tools/make_key`, `sign_target_files_apks`) esta en la
documentacion de AOSP y sus claves **no van en este repositorio**.

Los dos APK entran como `presigned`: conservan su firma original en vez de
refirmarse con la clave de la plataforma. Es deliberado, y es lo que permite que
una tablet con ROM propia y una tablet de fabrica aprovisionada por QR acepten
exactamente la misma actualizacion publicada en `version.json`.
