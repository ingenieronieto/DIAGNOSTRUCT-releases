# Arquitectura

## El problema real

DIAGNOSTRUCT no es una aplicacion nativa cualquiera. Del APK publicado
(`com.ingnieto.diagnostruct`, v1.85.0) salen tres hechos que condicionan todo el
diseño del sistema:

1. **Es una aplicacion Capacitor.** Toda la interfaz se dibuja sobre el WebView
   del sistema. Si el equipo no tiene proveedor de WebView, la aplicacion
   arranca y muestra una pantalla en blanco.
2. **Depende de aplicaciones ajenas.** La camara no se abre dentro de la
   aplicacion: se lanza un intent a la aplicacion de camara del equipo, y la
   seleccion de fotos abre el selector de documentos. Un kiosco que bloquee todo
   lo que no sea DIAGNOSTRUCT rompe las fotos, que es justo lo que el tecnico va
   a hacer en obra.
3. **La ubicacion pasa por Google Play Services.** `@capacitor/geolocation` usa
   el proveedor fusionado de Google. Sin Play Services no hay coordenadas, y los
   ensayos quedan sin georreferenciar.

De ahi salen las dos decisiones que mas se notan en el codigo: la lista de
paquetes autorizados se calcula **en el equipo y en tiempo de ejecucion** (no es
una lista fija escrita a mano), y la capa de kiosco sobre Android de fabrica es
la via principal, porque conserva Play Services.

## Las dos capas

```
┌──────────────────────────────────────────────────────────┐
│                    DIAGNOSTRUCT (APK)                    │
│         Capacitor · WebView · camara · GPS · red         │
└──────────────────────────────────────────────────────────┘
                            ▲
                            │ anclada en pantalla, permisos ya concedidos
┌──────────────────────────────────────────────────────────┐
│              DIAGNOSTRUCT OS  (com.diagnostruct.os)      │
│  pantalla de inicio · politica del equipo · actualizador │
└──────────────────────────────────────────────────────────┘
                            ▲
        ┌───────────────────┴───────────────────┐
        │                                       │
┌───────────────────────┐          ┌────────────────────────┐
│  Capa 1: kiosco       │          │  Capa 2: ROM AOSP      │
│  Android de fabrica   │   o      │  imagen propia         │
│  + propietario del    │          │  sin nada que no       │
│    dispositivo        │          │  haga falta            │
└───────────────────────┘          └────────────────────────┘
```

Las dos capas comparten el mismo launcher. La diferencia esta en el punto de
partida: la capa 1 **oculta** lo que sobra en un Android completo; la capa 2 ni
siquiera lo **construye**.

## Piezas del launcher

| Pieza | Cometido |
|---|---|
| `KioskLauncherActivity` | Pantalla de inicio del sistema. Abre DIAGNOSTRUCT anclada y es donde aterriza el equipo cuando la aplicacion muere o se pulsa inicio. |
| `KioskPolicy` | Traduce la politica a llamadas de `DevicePolicyManager`: anclaje, restricciones, permisos, ocultado. |
| `EssentialPackages` | Calcula que paquetes deben sobrevivir. El nucleo del diseño. |
| `PolicyWorker` | Aplica la politica fuera del hilo principal y sin necesitar red. |
| `UpdateWorker` | Consulta `version.json` cada seis horas e instala en silencio. |
| `TechnicianActivity` | Puerta de servicio con PIN. |
| `DiagnostructDeviceAdminReceiver` | Punto de entrada del alta; lee el PIN que viaja en el QR. |

## Tres decisiones que merecen explicacion

### La lista de aplicaciones autorizadas se calcula sola

Escribir a mano `["com.android.camera2", "com.android.documentsui"]` funciona en
la tablet de pruebas y falla en la siguiente marca, porque cada fabricante llama
distinto a su camara. `EssentialPackages` pregunta al sistema quien atiende
`ACTION_IMAGE_CAPTURE`, quien atiende `ACTION_GET_CONTENT` y que paquete provee
el WebView, y construye la lista con la respuesta. Funciona igual en una Samsung
que en una Rockchip.

### La pantalla de inicio se protege contra el bucle de relanzamiento

Un launcher que reabre la aplicacion sin condiciones parece razonable hasta que
la aplicacion falla al arrancar: entonces el equipo entra en un ciclo cerrado y
queda inservible, tambien para quien tiene que arreglarlo.
`KioskLauncherActivity` cuenta los regresos rapidos y, tras tres en cuatro
segundos, se detiene y muestra una pantalla de aviso con acceso al panel
tecnico.

### La depuracion USB no se bloquea por defecto

`DISALLOW_DEBUGGING_FEATURES` esta disponible pero desactivado. Activarlo antes
de haber validado el despliegue deja el equipo sin ninguna via de rescate: si el
kiosco falla, no hay ADB, no hay ajustes y no hay panel. La recomendacion es
activarlo (`blockAdb`) solo cuando el primer lote lleve semanas en obra.

## Que sostiene el blindaje

El anclaje de pantalla es la garantia principal, y los demas mecanismos son
capas de respaldo:

| Mecanismo | Que impide | Si falla |
|---|---|---|
| Anclaje (LockTask) | Salir de la aplicacion | La pantalla de inicio devuelve a ella |
| Pantalla de inicio propia | Que inicio lleve a otro sitio | El anclaje sigue puesto |
| Ocultado de aplicaciones | Abrir o ejecutar otra cosa | El anclaje sigue puesto |
| Restricciones de usuario | Restablecer, arranque seguro, cuentas | — |
| Arranque verificado (ROM) | Arrancar otra imagen | — |

Las tres primeras se solapan a proposito. En Android 8 y anteriores el anclaje
al lanzar no existe (`ActivityOptions.setLockTaskEnabled` es de Android 9), y
ahi el peso lo llevan la pantalla de inicio y el ocultado. Por eso los
requisitos de hardware piden Android 9 como minimo real, aunque la aplicacion
declare soportar Android 7.
