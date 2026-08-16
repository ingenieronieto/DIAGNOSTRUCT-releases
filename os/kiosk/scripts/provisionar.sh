#!/usr/bin/env bash
#
# Convierte una tablet Android de fábrica en un equipo DIAGNOSTRUCT.
#
# Requisitos en la tablet:
#   - Recién restablecida de fábrica, SIN ninguna cuenta añadida. Android se
#     niega a asignar propietario del dispositivo si ya hay cuentas.
#   - Depuración USB activada (Ajustes > Opciones de desarrollo).
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
aviso() { printf '\033[33m%s\033[0m\n' "$*"; }

# `adb` en Windows es un ejecutable nativo: no entiende las rutas estilo
# /c/dev/... que usa Git Bash, y falla con «failed to stat». Bash sí las
# entiende, así que las comprobaciones de existencia van sobre la ruta tal
# cual y solo se traduce lo que se le pasa a adb.
ruta_nativa() {
  if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else printf '%s' "$1"; fi
}

command -v adb >/dev/null || { rojo "Falta adb en el PATH."; exit 1; }

[[ -f "$LAUNCHER_APK" ]] || { rojo "No existe el APK del launcher: $LAUNCHER_APK"; exit 1; }
[[ -f "$APP_APK" ]]      || { rojo "No existe el APK de la aplicación: $APP_APK"; exit 1; }

info "Esperando a la tablet…"
adb wait-for-device

DISPOSITIVOS=$(adb devices | grep -cw "device" || true)
if [[ "$DISPOSITIVOS" -ne 1 ]]; then
  rojo "Se esperaba exactamente una tablet conectada; hay $DISPOSITIVOS."
  adb devices
  exit 1
fi

# Una sola cuenta basta para que `dpm set-device-owner` falle. Es mejor
# detectarlo aquí que a mitad del proceso, con el equipo ya medio configurado.
#
# Pero no todo lo que Android llama «cuenta» lo es: con una SIM puesta, el
# proveedor de contactos crea `USIM Account` y `SDN Account` (la agenda de la
# tarjeta y los números de servicio de la operadora). Mandar a restablecer de
# fábrica por eso es un consejo equivocado y caro: lo que hay que hacer es
# sacar la SIM. Se distinguen porque estas no las quita nadie desde Ajustes.
info "Comprobando que no haya cuentas dadas de alta…"
CUENTAS=$(adb shell dumpsys account 2>/dev/null | tr -d '\r' | grep "Account {" || true)
SIM_RE='type=(USIM|SDN|SIM|Preload) Account'
CUENTAS_SIM=$(echo "$CUENTAS" | grep -cE "$SIM_RE" || true)
CUENTAS_REALES=$(echo "$CUENTAS" | grep "Account {" | grep -vcE "$SIM_RE" || true)

if [[ "${CUENTAS_REALES:-0}" -gt 0 ]]; then
  rojo "La tablet tiene $CUENTAS_REALES cuenta(s) de usuario:"
  echo "$CUENTAS" | grep -vE "$SIM_RE" | sed 's/^/    /'
  rojo ""
  rojo "Quítelas desde Ajustes > Cuentas, o restablezca de fábrica y no añada"
  rojo "ninguna: Android no asigna propietario del dispositivo si hay alguna."
  exit 1
fi

if [[ "${CUENTAS_SIM:-0}" -gt 0 ]]; then
  # No se aborta: Android no siempre las rechaza, y prohibirlo aquí impediría
  # dar de alta un equipo que quizá sí admite el alta. Se avisa y se sigue; si
  # rechaza, el mensaje de más abajo dice qué hacer.
  aviso "Hay $CUENTAS_SIM cuenta(s) de la tarjeta SIM (agenda y números de la"
  aviso "operadora). Si el alta falla más abajo, saque la SIM, reinicie y"
  aviso "repita. Puede volver a ponerla en cuanto el equipo esté aprovisionado."
fi

info "Instalando el sistema DIAGNOSTRUCT OS…"
adb install -r -g "$(ruta_nativa "$LAUNCHER_APK")"

info "Instalando la aplicación DIAGNOSTRUCT…"
adb install -r -g "$(ruta_nativa "$APP_APK")"

info "Asignando propietario del dispositivo…"
if ! adb shell dpm set-device-owner "$ADMIN"; then
  rojo "No se pudo asignar el propietario del dispositivo."
  rojo ""
  if [[ "${CUENTAS_SIM:-0}" -gt 0 ]]; then
    rojo "Lo más probable, en este equipo: las $CUENTAS_SIM cuenta(s) de la tarjeta SIM."
    rojo "Saque la SIM, reinicie la tablet y repita. Vuelva a ponerla al terminar."
  else
    rojo "Causas habituales: hay cuentas, hay varios usuarios, o el equipo ya fue"
    rojo "aprovisionado (el asistente inicial ya se completó). En el último caso"
    rojo "hay que restablecer de fábrica y saltarse el inicio de sesión."
  fi
  exit 1
fi

info "Fijando la pantalla de inicio…"
adb shell cmd package set-home-activity "$PAQUETE_LAUNCHER/com.diagnostruct.os.ui.KioskLauncherActivity" || true

info "Arrancando el kiosco…"
adb shell monkey -p "$PAQUETE_LAUNCHER" -c android.intent.category.HOME 1 >/dev/null 2>&1 || true

verde ""
verde "Equipo aprovisionado."
verde "  Launcher:    $PAQUETE_LAUNCHER"
verde "  Aplicación: $PAQUETE_APP"
verde ""
verde "Antes de entregar la tablet:"
verde "  1. Apague y encienda la tablet. Durante la cuenta atrás de 3 s,"
verde "     toque «Panel técnico», entre con el PIN de fábrica (285713) y"
verde "     cámbielo desde «Cambiar el PIN». El de fábrica es público."
verde "  2. Compruebe el equipo con: ./diagnostico.sh"
