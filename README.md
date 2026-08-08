# DIAGNOSTRUCT — actualizaciones

Este repositorio es **público** y solo sirve para distribuir las actualizaciones
de la app DIAGNOSTRUCT. **Aquí no hay código fuente**: el código vive en un
repositorio privado aparte.

## Qué contiene

| Archivo | Para qué |
|---|---|
| `version.json` | Le dice a la app qué versión hay publicada y de dónde bajarla. |
| `DIAGNOSTRUCT-SDK.apk` | El instalador de Android (siempre el más reciente, con este mismo nombre). |

## Cómo publicar una versión nueva

1. Reemplazar `DIAGNOSTRUCT-SDK.apk` por el nuevo (mismo nombre, no cambiarlo).
2. Subir el número de `version` en `version.json` y ajustar `notas`.
3. Hacer commit y push.

La app revisa este archivo al abrirse y cada vez que vuelve la señal: si hay una
versión mayor, avisa dentro de la app con un botón para actualizar.

> La app se instala **encima** de la anterior. No se pierde ningún proyecto,
> ensayo ni foto.
