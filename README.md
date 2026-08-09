# DIAGNOSTRUCT — actualizaciones

Este repositorio es **público** y solo sirve para distribuir las actualizaciones
de la app DIAGNOSTRUCT. **Aquí no hay código fuente**: el código vive en un
repositorio privado aparte.

## Qué contiene

| Archivo | Para qué |
|---|---|
| `version.json` | Le dice a la app qué versión hay publicada y de dónde bajarla. |
| `DIAGNOSTRUCT-SDK.apk` | El instalador de Android (siempre el más reciente, con este mismo nombre). |
| `HISTORIAL-VERSIONES.md` | Copia de seguridad: commit y SHA-256 de cada versión publicada, y cómo volver a una anterior. |

## Cómo publicar una versión nueva

1. Reemplazar `DIAGNOSTRUCT-SDK.apk` por el nuevo (mismo nombre, no cambiarlo).
2. Subir el número de `version` en `version.json` y ajustar `notas`.
3. Hacer commit y push.

La app revisa este archivo al abrirse y cada vez que vuelve la señal: si hay una
versión mayor, avisa dentro de la app con un botón para actualizar.

> La app se instala **encima** de la anterior. No se pierde ningún proyecto,
> ensayo ni foto.

## Copia de seguridad

Todas las versiones publicadas quedan guardadas en el historial de este
repositorio. En [`HISTORIAL-VERSIONES.md`](HISTORIAL-VERSIONES.md) está el
commit y el SHA-256 de cada una, con el enlace de descarga directa para volver
a cualquier versión anterior.

> ⚠️ Si hay ensayos sin subir, **nunca desinstalar la app**: Android borra los
> datos de la aplicación al desinstalar y lo pendiente se pierde. Para volver a
> una versión anterior, instalar el APK **encima** de la actual.
