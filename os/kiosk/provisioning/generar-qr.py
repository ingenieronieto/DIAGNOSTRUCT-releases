#!/usr/bin/env python3
"""Genera el QR de alta de DIAGNOSTRUCT OS.

Se escanea desde la pantalla de bienvenida de una tablet recién restablecida
(seis toques sobre la pantalla de bienvenida abren el lector). El equipo se
descarga el launcher, lo instala, lo deja como propietario del dispositivo y
aplica la política del kiosco sin que nadie toque nada más.

Uso:
    ./generar-qr.py --apk ../launcher/app/build/outputs/apk/release/app-release.apk \\
                    --url https://ejemplo.com/DIAGNOSTRUCT-OS.apk \\
                    --ssid OBRA-WIFI --clave "secreto" --pin 481920

El checksum de firma se calcula con `apksigner` (Android SDK) o, si no está,
con `keytool` (JDK). Sin él, el equipo rechaza el APK descargado.
"""

from __future__ import annotations

import argparse
import base64
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

COMPONENT = (
    "com.diagnostruct.os/"
    "com.diagnostruct.os.policy.DiagnostructDeviceAdminReceiver"
)


def ejecutar(comando: list[str]) -> str:
    resultado = subprocess.run(comando, capture_output=True, text=True)
    if resultado.returncode != 0:
        raise RuntimeError(
            f"Fallo `{comando[0]}`:\n{resultado.stderr.strip() or resultado.stdout.strip()}"
        )
    return resultado.stdout


def sha256_del_certificado(apk: Path) -> str:
    """Devuelve el SHA-256 del certificado de firma en hexadecimal."""
    if shutil.which("apksigner"):
        salida = ejecutar(["apksigner", "verify", "--print-certs", str(apk)])
        # "Signer #1 certificate SHA-256 digest: a1b2c3..."
        encontrado = re.search(
            r"certificate SHA-256 digest:\s*([0-9a-fA-F]{64})", salida
        )
        if encontrado:
            return encontrado.group(1)

    if shutil.which("keytool"):
        salida = ejecutar(["keytool", "-printcert", "-jarfile", str(apk)])
        encontrado = re.search(
            r"SHA256:\s*((?:[0-9A-Fa-f]{2}:){31}[0-9A-Fa-f]{2})", salida
        )
        if encontrado:
            return encontrado.group(1).replace(":", "")

    raise RuntimeError(
        "No se encontró `apksigner` ni `keytool`. Instale el SDK de Android o un JDK."
    )


def checksum_base64url(hex_digest: str) -> str:
    """El alta espera el digest en base64 seguro para URL y sin relleno."""
    crudo = bytes.fromhex(hex_digest)
    return base64.urlsafe_b64encode(crudo).decode("ascii").rstrip("=")


def construir_payload(args: argparse.Namespace, checksum: str) -> dict:
    payload: dict[str, object] = {
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": COMPONENT,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": args.url,
        "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": checksum,
        # Sin esto el alta desactiva casi todas las aplicaciones de sistema,
        # incluidas la cámara y el WebView, y DIAGNOSTRUCT se queda sin poder
        # hacer fotos ni dibujar su interfaz.
        "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
        "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": False,
    }

    if args.ssid:
        payload["android.app.extra.PROVISIONING_WIFI_SSID"] = args.ssid
        payload["android.app.extra.PROVISIONING_WIFI_SECURITY_TYPE"] = args.seguridad
        if args.clave:
            payload["android.app.extra.PROVISIONING_WIFI_PASSWORD"] = args.clave

    if args.pin:
        payload["android.app.extra.PROVISIONING_ADMIN_EXTRAS_BUNDLE"] = {
            "pin_tecnico": args.pin
        }

    return payload


def escribir_qr(texto: str, destino: Path) -> bool:
    try:
        import qrcode  # type: ignore
    except ImportError:
        return False

    # Corrección de errores alta: estos QR se imprimen y se manejan en obra.
    codigo = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_H, box_size=8, border=4)
    codigo.add_data(texto)
    codigo.make(fit=True)
    codigo.make_image(fill_color="black", back_color="white").save(destino)
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--apk", required=True, type=Path, help="APK firmado del launcher")
    parser.add_argument("--url", required=True, help="URL pública desde la que la tablet lo descargará")
    parser.add_argument("--ssid", help="Red Wi-Fi a la que conectarse durante el alta")
    parser.add_argument("--clave", help="Clave de esa red")
    parser.add_argument("--seguridad", default="WPA", choices=["NONE", "WPA", "WEP", "EAP"])
    parser.add_argument("--pin", help="PIN del panel técnico")
    parser.add_argument("--salida", type=Path, default=Path("alta-diagnostruct"),
                        help="Ruta base de salida, sin extensión")
    args = parser.parse_args()

    if not args.apk.is_file():
        print(f"No existe el APK: {args.apk}", file=sys.stderr)
        return 1

    if not args.url.startswith("https://"):
        # El equipo descarga este APK y lo convierte en dueño de sí mismo:
        # servirlo por HTTP permitiría sustituirlo en tránsito.
        print("La URL de descarga debe ser HTTPS.", file=sys.stderr)
        return 1

    try:
        checksum = checksum_base64url(sha256_del_certificado(args.apk))
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        return 1

    payload = construir_payload(args, checksum)
    texto = json.dumps(payload, separators=(",", ":"), ensure_ascii=False)

    destino_json = args.salida.with_suffix(".json")
    destino_json.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    destino_png = args.salida.with_suffix(".png")
    if escribir_qr(texto, destino_png):
        print(f"QR escrito en   {destino_png}")
    else:
        print("Falta el paquete `qrcode` (pip install 'qrcode[pil]'); solo se generó el JSON.")

    print(f"JSON escrito en {destino_json}")
    print(f"Checksum firma  {checksum}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
