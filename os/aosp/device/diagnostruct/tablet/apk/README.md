# APK que entran en la imagen

Esta carpeta va vacia en el repositorio. `build.sh` deja aqui los dos binarios
justo antes de compilar:

| Archivo | Origen |
|---|---|
| `DIAGNOSTRUCT-SDK.apk` | Raiz de este repositorio (el que se publica a las tablets de fabrica). |
| `DIAGNOSTRUCT-OS.apk` | `os/kiosk/launcher/app/build/outputs/apk/release/app-release.apk`, ya firmado. |

Los dos entran como `presigned`, es decir, la imagen conserva su firma original
en vez de refirmarlos con la clave de la plataforma. Eso es lo que permite que
una tablet con esta ROM y una tablet de fabrica aprovisionada por QR acepten
exactamente la misma actualizacion publicada en `version.json`.
