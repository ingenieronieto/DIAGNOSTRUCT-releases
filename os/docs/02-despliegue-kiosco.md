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

**El panel de servicio** se abre desde la pantalla de inicio, que aparece
**tres segundos cada vez que se enciende la tablet** con un boton «Panel
tecnico» visible. Esa ventana es la via prevista: una vez la aplicacion queda
anclada, el boton de inicio no responde y no hay forma de volver a la pantalla
de inicio. Si algo falla, la pantalla se queda fija y el acceso deja de tener
prisa.

> Para entrar en una tablet ya en marcha: apagarla y encenderla, y tocar «Panel
> tecnico» durante la cuenta atras. Tambien vale mantener pulsado el logotipo.

El PIN **frena los intentos**: a partir del cuarto fallo hay que esperar, y la
espera se dobla en cada intento hasta un cuarto de hora. Reiniciar el equipo la
levanta. Desde el panel:

| Accion | Para que |
|---|---|
| Ajustes de Wi-Fi | Cambiar de red al llegar a una obra nueva. Abre una tregua de 10 minutos durante la que el kiosco no reancla nada; se cierra con «Volver a DIAGNOSTRUCT». |
| Buscar actualizacion ahora | Forzar la puesta al dia sin esperar las seis horas |
| Reaplicar politica | Recomponer el kiosco tras una actualizacion del fabricante |
| Entrar en mantenimiento | Levantar el kiosco entero para poder trabajar sobre el equipo |
| Cambiar el PIN | Sustituir el PIN de fabrica, que esta publicado en este repositorio |

**El PIN de fabrica (`285713`) no protege nada**: esta escrito en un repositorio
publico. En el alta por QR se fija con `--pin`; en el alta por USB hay que
cambiarlo desde el propio panel antes de entregar el equipo. Mientras siga
puesto, el panel lo avisa en azul, debajo de las acciones.

## Actualizaciones

No hay que hacer nada. El equipo consulta `version.json` cada seis horas y,
cuando ve una version mayor que la instalada, la descarga y la instala en
silencio. El APK se transmite directamente de la red al instalador, sin
guardarse antes en disco, porque estas tablets se quedan sin espacio con
facilidad.

Antes de conceder permisos o abrir nada, el equipo **comprueba que lo instalado
lleva el certificado de firma esperado**; si no coincide, lo retira. Importa en
la primera instalacion: al actualizar, Android ya exige que la firma case con la
de lo que hay puesto, pero cuando no hay nada instalado no existe esa referencia.

> Si algun dia se cambia la clave con la que se firma DIAGNOSTRUCT, hay que
> actualizar `APP_SIGNATURE_SHA256` en `app/build.gradle.kts` **y** desplegar el
> launcher nuevo antes de publicar el APK firmado con la clave nueva. En otro
> caso las tablets rechazaran la actualizacion.

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
