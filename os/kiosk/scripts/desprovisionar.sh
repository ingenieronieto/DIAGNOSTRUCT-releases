#!/usr/bin/env bash
#
# Devuelve la tablet a un estado normal: levanta el kiosco, renuncia a la
# propiedad del dispositivo y desinstala el launcher.
#
# Pensado para retirar un equipo del parque o para depurar. La aplicacion
# DIAGNOSTRUCT y sus datos NO se tocan: los proyectos y las fotos siguen ahi.

set -uo pipefail

PAQUETE_LAUNCHER="com.diagnostruct.os"
ADMIN="com.diagnostruct.os/com.diagnostruct.os.policy.DiagnostructDeviceAdminReceiver"

rojo()  { printf '\033[31m%s\033[0m\n' "$*"; }
verde() { printf '\033[32m%s\033[0m\n' "$*"; }
info()  { printf '\033[36m%s\033[0m\n' "$*"; }

command -v adb >/dev/null || { rojo "Falta adb en el PATH."; exit 1; }
adb wait-for-device

info "Levantando restricciones…"
for restriccion in \
  no_factory_reset no_safe_boot no_add_user no_usb_file_transfer \
  no_install_unknown_sources no_uninstall_apps no_modify_accounts \
  no_create_windows no_set_wallpaper no_outgoing_beam \
  no_airplane_mode no_config_date_time no_debugging_features
do
  adb shell dpm clear-user-restriction "$restriccion" >/dev/null 2>&1 || true
done

info "Renunciando a la propiedad del dispositivo…"
if adb shell dpm remove-active-admin "$ADMIN" 2>&1 | grep -qi "success"; then
  verde "Administrador retirado."
else
  # `remove-active-admin` no siempre puede con un propietario de dispositivo.
  # La app expone la renuncia desde el panel tecnico, que es la via limpia.
  rojo "No se pudo retirar por ADB."
  rojo "Use el panel tecnico de la tablet, o restablezca de fabrica."
fi

info "Desinstalando el launcher…"
adb uninstall "$PAQUETE_LAUNCHER" >/dev/null 2>&1 \
  && verde "Launcher desinstalado." \
  || rojo "No se pudo desinstalar el launcher (puede seguir siendo administrador)."

verde ""
verde "Hecho. La aplicacion DIAGNOSTRUCT y sus datos siguen en el equipo."
