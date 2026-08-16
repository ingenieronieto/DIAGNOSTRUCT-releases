#!/usr/bin/env bash
#
# Revisa que la tablet cumple todo lo que DIAGNOSTRUCT necesita para funcionar
# dentro del kiosco. Pensado para pasarlo antes de entregar cada equipo.

set -uo pipefail

PAQUETE_LAUNCHER="com.diagnostruct.os"
PAQUETE_APP="com.ingnieto.diagnostruct"

fallos=0

ok()    { printf '  \033[32mOK\033[0m    %s\n' "$*"; }
aviso() { printf '  \033[33mAVISO\033[0m %s\n' "$*"; }
error() { printf '  \033[31mFALLA\033[0m %s\n' "$*"; fallos=$((fallos + 1)); }
titulo(){ printf '\n\033[1m%s\033[0m\n' "$*"; }

command -v adb >/dev/null || { echo "Falta adb en el PATH."; exit 1; }
adb wait-for-device

titulo "Equipo"
printf '  Modelo:  %s\n' "$(adb shell getprop ro.product.model | tr -d '\r')"
printf '  Android: %s (API %s)\n' \
  "$(adb shell getprop ro.build.version.release | tr -d '\r')" \
  "$(adb shell getprop ro.build.version.sdk | tr -d '\r')"

API=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
if [[ "$API" -ge 28 ]]; then
  ok "API $API: el anclaje de la aplicación al lanzarla está disponible"
elif [[ "$API" -ge 24 ]]; then
  aviso "API $API: sin anclaje al lanzar; el blindaje se apoya solo en la pantalla de inicio"
else
  error "API $API: por debajo del mínimo que exige la aplicación (24)"
fi

titulo "Propietario del dispositivo"
# `dpm list-owners` responde justo esto y nada más. El respaldo por dumpsys se
# acota a la sección del propietario: un grep del paquete sobre todo el volcado
# da positivo por cualquier mención suelta y no prueba nada.
PROPIETARIO=$(adb shell dpm list-owners 2>/dev/null | tr -d '\r')
if [[ -z "$PROPIETARIO" ]] || echo "$PROPIETARIO" | grep -qi "unknown command"; then
  PROPIETARIO=$(adb shell dumpsys device_policy 2>/dev/null | tr -d '\r' \
    | sed -n '/Device Owner:/,/^$/p')
fi
# `dpm list-owners` responde literalmente «no owners» cuando no hay ninguno:
# tratarlo como texto no vacío haría decir «el propietario es otro», que manda
# a buscar un dueño inexistente justo en el paso donde más gente se pierde.
if echo "$PROPIETARIO" | grep -q "$PAQUETE_LAUNCHER"; then
  ok "DIAGNOSTRUCT OS es propietario del dispositivo"
elif [[ -z "$PROPIETARIO" ]] || echo "$PROPIETARIO" | grep -qi "no owners"; then
  error "El equipo NO tiene propietario asignado; el kiosco no puede blindarlo"
else
  error "El propietario es otro: $(echo "$PROPIETARIO" | head -2 | tr '\n' ' ')"
fi

titulo "Paquetes"
for paquete in "$PAQUETE_LAUNCHER" "$PAQUETE_APP"; do
  if adb shell pm list packages | tr -d '\r' | grep -qx "package:$paquete"; then
    version=$(adb shell dumpsys package "$paquete" | grep -m1 versionName | tr -d '\r' | cut -d= -f2)
    ok "$paquete instalado (${version:-versión desconocida})"
  else
    error "$paquete NO está instalado"
  fi
done

titulo "Dependencias de la aplicación"

# Capacitor dibuja toda la interfaz sobre el WebView del sistema.
WEBVIEW=$(adb shell dumpsys webviewupdate 2>/dev/null | grep -m1 "Current WebView package" | tr -d '\r')
if [[ -n "$WEBVIEW" ]]; then
  ok "WebView: ${WEBVIEW#*: }"
else
  error "No se detectó proveedor de WebView; la aplicación no arrancará"
fi

# La captura de fotos se hace lanzando un intent a la aplicación de cámara.
if adb shell pm resolve-activity --components -a android.media.action.IMAGE_CAPTURE 2>/dev/null | grep -q "/"; then
  ok "Cámara: $(adb shell pm resolve-activity --components -a android.media.action.IMAGE_CAPTURE | tr -d '\r')"
else
  error "Ninguna aplicación atiende la captura de fotos; el módulo de fotos fallará"
fi

# La geolocalización de Capacitor pasa por el proveedor fusionado de Google.
if adb shell pm list packages | tr -d '\r' | grep -qx "package:com.google.android.gms"; then
  ok "Google Play Services presente (geolocalización fusionada)"
else
  aviso "Sin Play Services: @capacitor/geolocation no obtendrá posición sin un sustituto"
fi

if adb shell pm list features | tr -d '\r' | grep -q "android.hardware.location.gps"; then
  ok "GPS por hardware"
else
  error "El equipo no declara GPS; los ensayos quedarán sin coordenadas"
fi

if adb shell pm list features | tr -d '\r' | grep -q "android.hardware.camera"; then
  ok "Cámara por hardware"
else
  error "El equipo no declara cámara"
fi

# Resolución del sensor. Es el techo físico del equipo: ninguna aplicación de
# cámara ni ajuste lo sube. Se avisa por debajo de 12 MP, pero no se marca
# fallo: un equipo con menos sigue siendo apto, solo obliga a acercarse más de
# la cuenta para que una fisura fina salga legible.
RESOLUCION=$(adb shell dumpsys media.camera 2>/dev/null | tr -d '\r' \
  | grep -A1 "android.sensor.info.pixelArraySize" \
  | sed -n 's/^ *\[\([0-9]\{1,\}\) \([0-9]\{1,\}\) *\]$/\1 \2/p' \
  | awk '{ if ($1 * $2 > max) { max = $1 * $2; mejor = $1 " " $2 } } END { print mejor }')
if [[ -n "$RESOLUCION" ]]; then
  ANCHO=${RESOLUCION% *}
  ALTO=${RESOLUCION#* }
  # Redondeo, no truncado: 3264x2448 son 7,99 MP y truncar los mostraria como
  # «7 MP», que no es lo que dice la ficha de ninguna tablet de 8 MP.
  MP=$(( (ANCHO * ALTO + 500000) / 1000000 ))
  if [[ "$MP" -ge 12 ]]; then
    ok "Sensor de ${MP} MP (${ANCHO}x${ALTO})"
  else
    aviso "Sensor de ${MP} MP (${ANCHO}x${ALTO}): justo para fisuras finas; se recomiendan 12 MP"
  fi
fi

titulo "Permisos concedidos a $PAQUETE_APP"
for permiso in CAMERA ACCESS_FINE_LOCATION READ_MEDIA_IMAGES; do
  linea=$(adb shell dumpsys package "$PAQUETE_APP" 2>/dev/null | grep -m1 "android.permission.$permiso: granted=" | tr -d '\r')
  case "$linea" in
    *granted=true*)  ok "$permiso concedido" ;;
    *granted=false*) error "$permiso NO concedido; saldrá un diálogo en campo" ;;
    *)               aviso "$permiso no aparece (puede no aplicar en esta versión de Android)" ;;
  esac
done

titulo "Almacenamiento"
LIBRE=$(adb shell df /data 2>/dev/null | tail -1 | awk '{print $4}' | tr -d '\r')
if [[ -n "${LIBRE:-}" ]]; then
  LIBRE_MB=$((LIBRE / 1024))
  if [[ "$LIBRE_MB" -lt 2048 ]]; then
    aviso "Solo quedan ${LIBRE_MB} MB libres; las fotos y los modelos 3D llenan rápido"
  else
    ok "${LIBRE_MB} MB libres en /data"
  fi
fi

printf '\n'
if [[ "$fallos" -eq 0 ]]; then
  printf '\033[32mEquipo conforme: 0 fallos.\033[0m\n'
  exit 0
fi
printf '\033[31mEquipo NO conforme: %s fallo(s).\033[0m\n' "$fallos"
exit 1
