# Poner una tablet en produccion

Dos caminos. El QR es el bueno para lotes; el USB es el bueno para la primera
tablet, mientras se ajusta todo.

> **Lo primero, siempre:** el equipo tiene que estar recien restablecido de
> fabrica y **sin ninguna cuenta añadida**. Android se niega a asignar
> propietario del dispositivo si ya hay una cuenta, y ese paso es el que sostiene
> todo lo demas. Si el asistente inicial pide iniciar sesion, salte ese paso.

## Antes de nada: compilar y firmar el launcher

```bash
cd os/kiosk/launcher

# La clave de firma fija la identidad del sistema: el QR de alta lleva su
# huella, y una tablet solo acepta actualizaciones firmadas con la misma clave.
# Genere UNA y guardela; perderla obliga a restablecer todo el parque.
keytool -genkeypair -v -keystore diagnostruct.jks \
        -alias diagnostruct -keyalg RSA -keysize 4096 -validity 10000

cat > keystore.properties <<'EOF'
storeFile=diagnostruct.jks
storePassword=...
keyAlias=diagnostruct
keyPassword=...
EOF

./gradlew assembleRelease
```

El APK sale en `app/build/outputs/apk/release/app-release.apk`.

`keystore.properties` y `*.jks` estan en el `.gitignore` del proyecto: la clave
no debe entrar nunca en el repositorio, que ademas es publico.

> Este proyecto no incluye el `gradle-wrapper.jar` (un binario no auditable en
> un repositorio publico). Genere el wrapper la primera vez con
> `gradle wrapper --gradle-version 8.13`, o compile con `gradle assembleRelease`
> si ya tiene Gradle 8.13 o superior instalado.

## Camino A — por QR (lotes)

Es el que conviene para poner en marcha varias tablets: no hace falta cable ni
ordenador junto a cada equipo.

```bash
cd os/kiosk/provisioning
pip install 'qrcode[pil]'

./generar-qr.py \
  --apk ../launcher/app/build/outputs/apk/release/app-release.apk \
  --url https://su-servidor/DIAGNOSTRUCT-OS.apk \
  --ssid OBRA-WIFI --clave "clave-wifi" \
  --pin 481920
```

El APK del launcher tiene que estar publicado en esa URL, accesible por HTTPS.

Despues, en cada tablet:

1. Restablecer de fabrica.
2. En la primera pantalla de bienvenida, **tocar seis veces seguidas**. Se abre
   el lector de QR.
3. Escanear el QR generado.
4. La tablet se conecta al Wi-Fi, descarga el launcher, lo instala, se asigna
   como propietario del dispositivo y aplica la politica. Termina sola en la
   pantalla de DIAGNOSTRUCT.

El PIN que se pasa con `--pin` queda guardado en cada equipo, de modo que cada
lote puede llevar el suyo sin recompilar nada.

## Camino B — por USB

```bash
cd os/kiosk/scripts
./provisionar.sh
```

El script comprueba que haya una sola tablet conectada y que no tenga cuentas,
instala los dos APK, asigna el propietario del dispositivo y arranca el kiosco.

## Comprobar el resultado

```bash
./diagnostico.sh
```

Repasa lo que de verdad importa y falla si algo no esta: propietario asignado,
los dos paquetes instalados, proveedor de WebView presente, una aplicacion que
atienda la captura de fotos, Play Services para la ubicacion, GPS y camara por
hardware, permisos ya concedidos y espacio libre.

Pasar este script antes de entregar cada equipo evita la mayoria de las
sorpresas en obra.

## Uso diario

**El tecnico** enciende la tablet y ya esta dentro de DIAGNOSTRUCT. No hay
pantalla de bloqueo, ni ajustes, ni forma de salir.

**El panel de servicio** se abre manteniendo pulsado el logotipo en la pantalla
de inicio (aparece un instante al arrancar, o de forma fija si algo falla) e
introduciendo el PIN. Desde ahi:

| Accion | Para que |
|---|---|
| Ajustes de Wi-Fi | Cambiar de red al llegar a una obra nueva |
| Buscar actualizacion ahora | Forzar la puesta al dia sin esperar las seis horas |
| Reaplicar politica | Recomponer el kiosco tras una actualizacion del fabricante |
| Entrar en mantenimiento | Levantar el kiosco entero para poder trabajar sobre el equipo |

## Actualizaciones

No hay que hacer nada. El equipo consulta `version.json` cada seis horas y,
cuando ve una version mayor que la instalada, la descarga y la instala en
silencio. El APK se transmite directamente de la red al instalador, sin
guardarse antes en disco, porque estas tablets se quedan sin espacio con
facilidad.

Publicar una version nueva sigue siendo lo de siempre: reemplazar el APK en la
raiz del repositorio y subir el numero en `version.json`.

## Retirar un equipo

```bash
./desprovisionar.sh
```

Levanta las restricciones, renuncia a la propiedad del dispositivo y desinstala
el launcher. **No toca la aplicacion ni sus datos**: los proyectos, ensayos y
fotos siguen en el equipo.

Si ADB no puede retirar el administrador, use *Entrar en mantenimiento* desde el
panel tecnico de la tablet, o restablezca de fabrica.

## Cuando algo va mal

| Sintoma | Causa habitual |
|---|---|
| `dpm set-device-owner` falla | Hay una cuenta dada de alta. Restablecer y no iniciar sesion. |
| La aplicacion abre en blanco | Falta el proveedor de WebView. `diagnostico.sh` lo detecta. |
| La camara no hace nada | El alta desactivo la aplicacion de camara. Use el QR generado por el script, que incluye `LEAVE_ALL_SYSTEM_APPS_ENABLED`. |
| Los ensayos salen sin coordenadas | Falta Play Services. Ver `04-requisitos-hardware.md`. |
| "La aplicacion no arranca" en pantalla | Tres cierres seguidos. El launcher se ha parado a proposito; revise la aplicacion desde el panel tecnico. |
