# 13. Android architecture

Mínimo: **API 26 (Android 8.0)**. Target: API 35.

## Módulos

```
android/
  domain/          Kotlin JVM, sin android.jar
  data/            Android library, SQLite
  transport-ble/   Android library, BLE
  platform/        Android library, FGS, permisos, GNSS, audio
  app/             Application, UI mínima
  simulator/       JVM, no Android
```

Clean: UI → EmergencyEngine (domain) → puertos. Adapters en data/transport/platform.

## Permisos

### API 26–30

- `BLUETOOTH`, `BLUETOOTH_ADMIN`
- `ACCESS_FINE_LOCATION` (el scan BLE la exige)
- `ACCESS_COARSE_LOCATION`
- `FOREGROUND_SERVICE`
- `VIBRATE`
- `WAKE_LOCK`

### API 31+

- `BLUETOOTH_SCAN` (sin `neverForLocation`: necesitamos GNSS)
- `BLUETOOTH_ADVERTISE`
- `BLUETOOTH_CONNECT`
- `ACCESS_FINE_LOCATION`
- API 29+: `ACCESS_BACKGROUND_LOCATION` solo si se justifica; el prototipo
  pide location en foreground y last-known. Background location = etapa 2.

### API 34+

- `FOREGROUND_SERVICE_CONNECTED_DEVICE`
- `FOREGROUND_SERVICE_LOCATION` si se usa GPS en el FGS

`usesFeature bluetooth_le required=true` para el prototipo.
Dispositivos sin BLE no son el target.

## Foreground Service

Tipo `connectedDevice` (API 29+). Notificación persistente:

“Red de Ayuda activa — repetidor de emergencia”

Esto **rompe** el silencio visual total. Es un límite de Android, no un olvido.
La notificación no lista SOS ajenos.

Android 12+: no arrancar FGS desde background arbitrario.
Arrancar el servicio cuando el usuario elige modo (SOS / repetidor / rescate).

Android 14–15: declarar el tipo o el sistema mata el FGS.

## BLE

- Scan siempre con `ScanFilter` service UUID.
- Background: `PendingIntent` scan como complemento del FGS.
- Advertising: comprobar `isMultipleAdvertisementSupported()`.
- GATT server (peripheral) + GATT client (central).
- `requestMtu(517)`.

## UI prototipo

Una Activity:

- Tres modos: SOS / REPETIDOR / RESCATE
- Log técnico (messageId, hops, eventos) — no mapa público
- En Rescue: lista SOS + CONTACTAR / SONAR / ACK
- En SOS: estado, último ACK, batería, coords

Sin diseño pulido. Sin Vue. XML + Kotlin (la regla de Blade/jQuery es para web;
esta app es nativa Android).

## OEM

Pantalla de “ignorar optimización de batería” con deep-link cuando exista.
Samsung/Xiaomi/Huawei: documentar pasos manuales en la matriz.

## Build

Gradle Kotlin DSL, JDK 17 para compilar.
`minSdk 26`, `compileSdk 35`.
