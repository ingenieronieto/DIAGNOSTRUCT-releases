#!/usr/bin/env bash
#
# Convierte una tablet Android de fabrica en un equipo DIAGNOSTRUCT.
#
# Requisitos en la tablet:
#   - Recien restablecida de fabrica, SIN ninguna cuenta añadida. Android se
#     niega a asignar propietario del dispositivo si ya hay cuentas.
#   - Depuracion USB activada (Ajustes > Opciones de desarrollo).
#
# Uso: ./provisionar.sh [ruta-launcher.apk] [ruta-diagnostruct.apk]

set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
LAUNCHER_APK="${1:-$RAIZ/os/kiosk/launcher/app/build/outputs/apk/release/app-release.apk}"
APP_APK="${2:-$RAIZ/DIAGNOSTRUCT-SDK.apk}"

ADMIN="com.diagnostruct.os/com.diagnostruct.os.policy.DiagnostructDeviceAdminReceiver"
PAQUETE_LAUNCHER="com.diagnostruct.os"
PAQUETE_APP="com.ingnieto.diagnostruct"

rojo()  { printf '\033[31m%s\033[0m\n' "$*"; }
verde() { printf '\033[32m%s\033[0m\n' "$*"; }
info()  { printf '\033[36m%s\033[0m\n' "$*"; }

command -v adb >/dev/null || { rojo "Falta adb en el PATH."; exit 1; }

[[ -f "$LAUNCHER_APK" ]] || { rojo "No existe el APK del launcher: $LAUNCHER_APK"; exit 1; }
[[ -f "$APP_APK" ]]      || { rojo "No existe el APK de la aplicacion: $APP_APK"; exit 1; }

info "Esperando a la tablet…"
adb wait-for-device

DISPOSITIVOS=$(adb devices | grep -cw "device" || true)
if [[ "$DISPOSITIVOS" -ne 1 ]]; then
  rojo "Se esperaba exactamente una tablet conectada; hay $DISPOSITIVOS."
  adb devices
  exit 1
fi

# Una sola cuenta basta para que `dpm set-device-owner` falle. Es mejor
# detectarlo aqui que a mitad del proceso, con el equipo ya medio configurado.
info "Comprobando que no haya cuentas dadas de alta…"
CUENTAS=$(adb shell dumpsys account 2>/dev/null | grep -c "Account {" || true)
if [[ "${CUENTAS:-0}" -gt 0 ]]; then
  rojo "La tablet tiene $CUENTAS cuenta(s). Restablezca de fabrica y no añada ninguna."
  exit 1
fi

info "Instalando el sistema DIAGNOSTRUCT OS…"
adb install -r -g "$LAUNCHER_APK"

info "Instalando la aplicacion DIAGNOSTRUCT…"
adb install -r -g "$APP_APK"

info "Asignando propietario del dispositivo…"
if ! adb shell dpm set-device-owner "$ADMIN"; then
  rojo "No se pudo asignar el propietario del dispositivo."
  rojo "Causas habituales: hay cuentas, hay varios usuarios, o el equipo ya fue aprovisionado."
  exit 1
fi

info "Fijando la pantalla de inicio…"
adb shell cmd package set-home-activity "$PAQUETE_LAUNCHER/com.diagnostruct.os.ui.KioskLauncherActivity" || true

info "Arrancando el kiosco…"
adb shell monkey -p "$PAQUETE_LAUNCHER" -c android.intent.category.HOME 1 >/dev/null 2>&1 || true

verde ""
verde "Equipo aprovisionado."
verde "  Launcher:    $PAQUETE_LAUNCHER"
verde "  Aplicacion:  $PAQUETE_APP"
verde ""
verde "Antes de entregar la tablet:"
verde "  1. Mantenga pulsado el logotipo, entre con el PIN de fabrica (285713)"
verde "     y cambielo desde 'Cambiar el PIN'. El de fabrica es publico."
verde "  2. Compruebe el equipo con: ./diagnostico.sh"
