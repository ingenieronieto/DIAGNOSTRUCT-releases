# Que tablet comprar

Requisitos derivados del APK publicado (`com.ingnieto.diagnostruct` v1.85.0,
minSdk 24, targetSdk 36) y de lo que exige el sistema de kiosco.

## Imprescindible

| Requisito | Por que |
|---|---|
| **Android 9 o posterior** | El anclaje al lanzar (`ActivityOptions.setLockTaskEnabled`) es de Android 9. Con Android 7-8 la aplicacion se abre pero el blindaje queda a medias. |
| **Google Play Services** | `@capacitor/geolocation` usa el proveedor fusionado. Sin el, los ensayos salen sin coordenadas. |
| **Proveedor de WebView actualizable** | La aplicacion es Capacitor: sin WebView, pantalla en blanco. Un WebView viejo y sin actualizar tambien rompe la interfaz. |
| **Aplicacion de camara** | La captura de fotos se hace por intent a la camara del sistema. |
| **GPS por hardware** | Declarado como `android.hardware.location.gps`. |
| **Camara trasera con autofoco** | Las fotos son prueba pericial: las fisuras tienen que salir nitidas. |
| **Camara trasera de 12 MP o mas** | Medido en campo: una Lenovo TB328XU da 3264x2448 (8 MP), y es el techo fisico del equipo. Ningun ajuste ni aplicacion de camara lo sube. Para una fisura fina, 8 MP obliga a acercarse tanto que se pierde el contexto de la pieza. |
| **Sin SIM al aprovisionar** | No es una propiedad de la tablet, sino del procedimiento: una SIM insertada crea cuentas de agenda (`USIM Account`, `SDN Account`) y Android las cuenta como cuentas de usuario, que impiden asignar el propietario del dispositivo. Se saca la SIM para dar de alta y se vuelve a poner despues. |
| **64 GB de almacenamiento** | Fotos y modelos 3D. El changelog de la v1.85 ya avisa de equipos llenandose. |
| **4 GB de RAM** | El WebView con fotogrametria consume bastante. |

## Muy recomendable

| Requisito | Por que |
|---|---|
| 8 GB de RAM | Margen para los modelos 3D. |
| 128 GB de almacenamiento | Una campaña larga sin cobertura acumula mucho pendiente de subir. |
| Pantalla de 10" y 500 nits o mas | En obra, a pleno sol, por debajo de eso no se ve. |
| IP54 o superior | Polvo y lluvia. |
| Bateria de 7000 mAh o mas | Una jornada completa sin enchufe. |
| Ranura microSD | Ampliar sin devolver el equipo. |
| Compatibilidad con **Zero-Touch** o **Knox** | Alta masiva sin tocar cada tablet. |

## Solo para la ROM propia (capa 2)

| Requisito | Por que |
|---|---|
| Bootloader desbloqueable | Sin esto no hay imagen propia que valga. |
| Arbol de dispositivo y blobs publicados | Sin ellos la imagen no arranca en el equipo. |
| SoC con soporte de AOSP | Rockchip RK3568/RK3588 y MediaTek suelen publicar SDK; Qualcomm en tablets de consumo, casi nunca. |

**Las Samsung Galaxy Tab quedan descartadas para esta capa**: el bootloader no
se desbloquea. Son, en cambio, una eleccion muy buena para la capa de kiosco,
porque Knox añade un modo dedicado especialmente solido.

## Tres perfiles

### Maximo control — tablet industrial Rockchip

RK3568 o RK3588, 8/128 GB. El fabricante entrega el SDK con AOSP y root, que es
lo que hace viable la ROM propia. A cambio, Play Services no viene de serie:
hay que negociarlo con el proveedor o resolver la ubicacion por otra via.

Es el perfil indicado si la ROM es un objetivo firme.

### Equilibrio — Samsung Galaxy Tab Active

Resistente, con Knox y Zero-Touch, Play Services de serie y soporte largo del
fabricante. Solo capa de kiosco, que es la recomendada de todas formas.

Es el perfil indicado si lo que se quiere es empezar a producir ya.

### Coste minimo — Lenovo Tab / Teclast de gama media

Cumple los imprescindibles y poco mas. Sirve para pilotar el despliegue con dos
o tres equipos antes de comprometer el presupuesto del parque.

## Como validar un modelo antes de comprar el lote

Compre **una** unidad y pase el diagnostico. **Saque la SIM antes**, si la lleva:

```bash
cd os/kiosk/scripts
./provisionar.sh && ./diagnostico.sh
```

El script comprueba en el equipo real lo que ninguna ficha tecnica dice:
proveedor de WebView, aplicacion que atienda la captura de fotos, presencia de
Play Services, GPS y camara declarados por hardware, y si el propietario del
dispositivo se puede asignar. Si sale conforme, el modelo sirve.
