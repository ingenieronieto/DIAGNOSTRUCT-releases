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
  ok "API $API: el anclaje de la aplicacion al lanzarla esta disponible"
elif [[ "$API" -ge 24 ]]; then
  aviso "API $API: sin anclaje al lanzar; el blindaje se apoya solo en la pantalla de inicio"
else
  error "API $API: por debajo del minimo que exige la aplicacion (24)"
fi

titulo "Propietario del dispositivo"
if adb shell dumpsys device_policy 2>/dev/null | grep -q "$PAQUETE_LAUNCHER"; then
  ok "DIAGNOSTRUCT OS es propietario del dispositivo"
else
  error "El equipo NO tiene propietario asignado; el kiosco no puede blindarlo"
fi

titulo "Paquetes"
for paquete in "$PAQUETE_LAUNCHER" "$PAQUETE_APP"; do
  if adb shell pm list packages | tr -d '\r' | grep -qx "package:$paquete"; then
    version=$(adb shell dumpsys package "$paquete" | grep -m1 versionName | tr -d '\r' | cut -d= -f2)
    ok "$paquete instalado (${version:-version desconocida})"
  else
    error "$paquete NO esta instalado"
  fi
done

titulo "Dependencias de la aplicacion"

# Capacitor dibuja toda la interfaz sobre el WebView del sistema.
WEBVIEW=$(adb shell dumpsys webviewupdate 2>/dev/null | grep -m1 "Current WebView package" | tr -d '\r')
if [[ -n "$WEBVIEW" ]]; then
  ok "WebView: ${WEBVIEW#*: }"
else
  error "No se detecto proveedor de WebView; la aplicacion no arrancara"
fi

# La captura de fotos se hace lanzando un intent a la aplicacion de camara.
if adb shell pm resolve-activity --components -a android.media.action.IMAGE_CAPTURE 2>/dev/null | grep -q "/"; then
  ok "Camara: $(adb shell pm resolve-activity --components -a android.media.action.IMAGE_CAPTURE | tr -d '\r')"
else
  error "Ninguna aplicacion atiende la captura de fotos; el modulo de fotos fallara"
fi

# La geolocalizacion de Capacitor pasa por el proveedor fusionado de Google.
if adb shell pm list packages | tr -d '\r' | grep -qx "package:com.google.android.gms"; then
  ok "Google Play Services presente (geolocalizacion fusionada)"
else
  aviso "Sin Play Services: @capacitor/geolocation no obtendra posicion sin un sustituto"
fi

if adb shell pm list features | tr -d '\r' | grep -q "android.hardware.location.gps"; then
  ok "GPS por hardware"
else
  error "El equipo no declara GPS; los ensayos quedaran sin coordenadas"
fi

if adb shell pm list features | tr -d '\r' | grep -q "android.hardware.camera"; then
  ok "Camara por hardware"
else
  error "El equipo no declara camara"
fi

titulo "Permisos concedidos a $PAQUETE_APP"
for permiso in CAMERA ACCESS_FINE_LOCATION READ_MEDIA_IMAGES; do
  linea=$(adb shell dumpsys package "$PAQUETE_APP" 2>/dev/null | grep -m1 "android.permission.$permiso: granted=" | tr -d '\r')
  case "$linea" in
    *granted=true*)  ok "$permiso concedido" ;;
    *granted=false*) error "$permiso NO concedido; saldra un dialogo en campo" ;;
    *)               aviso "$permiso no aparece (puede no aplicar en esta version de Android)" ;;
  esac
done

titulo "Almacenamiento"
LIBRE=$(adb shell df /data 2>/dev/null | tail -1 | awk '{print $4}' | tr -d '\r')
if [[ -n "${LIBRE:-}" ]]; then
  LIBRE_MB=$((LIBRE / 1024))
  if [[ "$LIBRE_MB" -lt 2048 ]]; then
    aviso "Solo quedan ${LIBRE_MB} MB libres; las fotos y los modelos 3D llenan rapido"
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
