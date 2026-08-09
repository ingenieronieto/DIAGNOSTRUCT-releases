# DIAGNOSTRUCT — actualizaciones y sistema de las tablets

Este repositorio es **público**. Contiene dos cosas: las actualizaciones de la
app DIAGNOSTRUCT y el sistema operativo de las tablets de campo.

**El código de la app no está aquí**: vive en un repositorio privado aparte.
Aquí solo se publica su instalador ya compilado.

## Qué contiene

| Archivo | Para qué |
|---|---|
| `version.json` | Le dice a la app qué versión hay publicada y de dónde bajarla. |
| `DIAGNOSTRUCT-SDK.apk` | El instalador de Android (siempre el más reciente, con este mismo nombre). |
| [`os/`](os/) | **DIAGNOSTRUCT OS**: el sistema que deja la tablet dedicada a la app y nada más. |

> Como el repositorio es público, aquí no entra ninguna clave: ni la de firma
> del sistema, ni las de la imagen de AOSP. Ver [`os/README.md`](os/README.md).

## Cómo publicar una versión nueva

1. Reemplazar `DIAGNOSTRUCT-SDK.apk` por el nuevo (mismo nombre, no cambiarlo).
2. Subir el número de `version` en `version.json` y ajustar `notas`.
3. Hacer commit y push.

La app revisa este archivo al abrirse y cada vez que vuelve la señal: si hay una
versión mayor, avisa dentro de la app con un botón para actualizar.

> La app se instala **encima** de la anterior. No se pierde ningún proyecto,
> ensayo ni foto.

En las tablets con DIAGNOSTRUCT OS no hay que avisar a nadie: el equipo lee este
mismo `version.json` cada seis horas y se actualiza solo, en silencio.

## El sistema de las tablets

[`os/`](os/) contiene DIAGNOSTRUCT OS, en dos capas que comparten el mismo
launcher:

- **Kiosco** — se despliega hoy sobre cualquier tablet Android: arranca directa
  en la app, no deja abrir nada más y se mantiene al día sola.
- **ROM AOSP** — árbol de producto para compilar una imagen propia cuando esté
  elegido el hardware.

Empiece por [`os/README.md`](os/README.md).
