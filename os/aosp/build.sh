#!/usr/bin/env bash
#
# Compila DIAGNOSTRUCT OS dentro de un arbol de AOSP ya sincronizado.
#
#   export ANDROID_BUILD_TOP=/ruta/al/arbol/aosp
#   ./build.sh [-j numero-de-tareas]
#
# El arbol de AOSP no se descarga desde aqui: son unos 200 GB y su sincronizacion
# es un paso aparte, descrito en os/docs/03-build-aosp.md.

set -euo pipefail

RAIZ_REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
ORIGEN_ARBOL="$RAIZ_REPO/os/aosp/device/diagnostruct/tablet"
PRODUCTO="diagnostruct_tablet"
RELEASE="${DIAGNOSTRUCT_RELEASE:-trunk_staging}"
VARIANTE="${DIAGNOSTRUCT_VARIANTE:-userdebug}"
TAREAS="$(nproc 2>/dev/null || echo 8)"

while getopts "j:" opcion; do
  case "$opcion" in
    j) TAREAS="$OPTARG" ;;
    *) echo "Uso: $0 [-j tareas]" >&2; exit 1 ;;
  esac
done

rojo()  { printf '\033[31m%s\033[0m\n' "$*"; }
verde() { printf '\033[32m%s\033[0m\n' "$*"; }
info()  { printf '\033[36m%s\033[0m\n' "$*"; }

[[ -n "${ANDROID_BUILD_TOP:-}" ]] || {
  rojo "Defina ANDROID_BUILD_TOP con la ruta del arbol de AOSP."
  exit 1
}
[[ -f "$ANDROID_BUILD_TOP/build/envsetup.sh" ]] || {
  rojo "$ANDROID_BUILD_TOP no parece un arbol de AOSP (falta build/envsetup.sh)."
  exit 1
}

DESTINO_ARBOL="$ANDROID_BUILD_TOP/device/diagnostruct/tablet"

# --- Binarios que entran en la imagen ---------------------------------------

APK_SDK="$RAIZ_REPO/DIAGNOSTRUCT-SDK.apk"
APK_OS="$RAIZ_REPO/os/kiosk/launcher/app/build/outputs/apk/release/app-release.apk"

[[ -f "$APK_SDK" ]] || { rojo "Falta $APK_SDK"; exit 1; }
if [[ ! -f "$APK_OS" ]]; then
  rojo "Falta el APK del launcher: $APK_OS"
  rojo "Compilelo antes con:  cd os/kiosk/launcher && ./gradlew assembleRelease"
  exit 1
fi

info "Copiando el arbol de producto a $DESTINO_ARBOL…"
mkdir -p "$DESTINO_ARBOL"
# --delete deja el destino igual al origen: sin esto, un archivo borrado del
# repositorio seguiria vivo en el arbol de AOSP y en la imagen resultante.
rsync -a --delete --exclude 'apk/*.apk' "$ORIGEN_ARBOL/" "$DESTINO_ARBOL/"

info "Colocando los APK…"
mkdir -p "$DESTINO_ARBOL/apk"
cp "$APK_SDK" "$DESTINO_ARBOL/apk/DIAGNOSTRUCT-SDK.apk"
cp "$APK_OS"  "$DESTINO_ARBOL/apk/DIAGNOSTRUCT-OS.apk"

# --- Compilacion ------------------------------------------------------------

info "Compilando $PRODUCTO-$RELEASE-$VARIANTE con $TAREAS tareas…"
cd "$ANDROID_BUILD_TOP"

# envsetup.sh y lunch dependen de funciones de shell, no de ejecutables, asi
# que hay que cargarlos en este mismo proceso.
# shellcheck disable=SC1091
source build/envsetup.sh
lunch "$PRODUCTO-$RELEASE-$VARIANTE"
m -j"$TAREAS"

verde ""
verde "Imagen compilada."
verde "  Salida:   $ANDROID_BUILD_TOP/out/target/product/$PRODUCTO/"
verde "  Emulador: emulator -selinux permissive"
verde "  Flasheo:  fastboot flashall -w"
