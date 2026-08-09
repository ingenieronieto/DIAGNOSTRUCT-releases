# DIAGNOSTRUCT OS

Sistema operativo para las tablets de campo: el equipo arranca directamente en
DIAGNOSTRUCT, no deja abrir nada mas y se mantiene actualizado solo.

Se entrega en dos capas, que resuelven el mismo problema con distinto alcance y
comparten el mismo launcher:

| Capa | Que es | Cuando usarla |
|---|---|---|
| **Kiosco** (`kiosk/`) | Launcher propio + propietario del dispositivo sobre el Android de fabrica. | Ahora. Funciona en cualquier tablet, se despliega por QR o por USB en minutos y conserva Google Play Services. |
| **ROM** (`aosp/`) | Arbol de producto de AOSP que compila una imagen con solo lo necesario. | Cuando este elegido el hardware. Elimina de raiz lo que la capa de kiosco se limita a ocultar. |

## Por donde empezar

1. [`docs/01-arquitectura.md`](docs/01-arquitectura.md) — que hace cada pieza y por que.
2. [`docs/02-despliegue-kiosco.md`](docs/02-despliegue-kiosco.md) — poner una tablet en produccion.
3. [`docs/03-build-aosp.md`](docs/03-build-aosp.md) — compilar la ROM.
4. [`docs/04-requisitos-hardware.md`](docs/04-requisitos-hardware.md) — que tablet comprar.

## Estructura

```
os/
├── kiosk/
│   ├── launcher/        Aplicacion Android (Kotlin): inicio, politica y actualizador
│   ├── provisioning/    Generador del QR de alta
│   └── scripts/         Alta, baja y diagnostico por USB
└── aosp/
    ├── device/diagnostruct/tablet/   Arbol de producto
    ├── manifests/                    Manifiesto local de repo
    └── build.sh                      Compilacion de la imagen
```

## Lo que el sistema garantiza

- **Una sola aplicacion.** DIAGNOSTRUCT queda anclada a la pantalla. Inicio,
  atras y recientes no sacan de ella; el resto de aplicaciones se ocultan.
- **Sin dialogos en obra.** Camara, ubicacion y almacenamiento vienen concedidos
  de antemano.
- **Al dia sin intervencion.** El equipo consulta `version.json` cada seis horas
  y se actualiza en silencio cuando hay red.
- **Sin pantalla de bloqueo.** Se enciende y ya esta dentro de la aplicacion.
- **Una puerta de servicio.** Manteniendo pulsado el logotipo y con PIN se llega
  a Wi-Fi, actualizacion forzada y modo mantenimiento.

## Lo que hay que decidir antes de producir

- El **PIN** del panel tecnico (por defecto `285713`, hay que cambiarlo).
- La **clave de firma** del launcher, que fija la identidad del sistema.
- Si se bloquea la **depuracion USB** (`blockAdb`), que blinda mas pero deja el
  equipo sin via de rescate.
