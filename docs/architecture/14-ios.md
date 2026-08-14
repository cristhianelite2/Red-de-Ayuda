# 14. iOS architecture

**No se compila en este Windows.** Este documento es la fuente para cuando exista Mac.

Mínimo: **iOS 14**. Swift, UIKit (no SwiftUI obligatorio; UIKit es más predecible
en background). No Flutter/RN.

## Targets futuros

```
ios/RedDeAyuda/           app iPhone
ios/RedDeAyudaCore/       packet, state machine, store-forward (Swift)
ios/RedDeAyudaTests/
```

El Core Swift debe pasar los **mismos test vectors hex** que Kotlin.
Eso es el contrato de interoperabilidad, no un módulo binario compartido.

## Info.plist

```
UIBackgroundModes:
  - bluetooth-central
  - bluetooth-peripheral
NSBluetoothAlwaysUsageDescription
NSLocationWhenInUseUsageDescription
NSLocationAlwaysAndWhenInUseUsageDescription  (SOS; Always es Review risk)
NSMotionUsageDescription                       (etapa 2)
```

Background UUID del service en `bluetooth-central` restoration keys.

## Core Bluetooth

- `CBCentralManager` + `CBPeripheralManager` dual.
- Restoration identifier `rda.ble.restore`.
- Scan en background **solo** con `[RdaUuids.service]`.
- Advertising background: UUID va a overflow → Android no ve el iPhone.
- SOS: `idleTimerDisabled = true`, UI a pantalla completa, texto:

  “Si bloqueas el teléfono, los Android cercanos pueden dejar de verte.
   Los iPhone cercanos con la app pueden seguirte encontrando.”

## Force-quit

Si el usuario mata la app desde el switcher, iOS **no** relanza por BLE.
Documentar en onboarding. No hay workaround legal.

## Audio Rescue

`AVAudioSessionCategoryPlayback` + background mode `audio` puede ser
rechazado en App Review si no hay contenido de audio real.
Plan: intentar sonido al recibir ping con la app viva;
si está suspendida, notificación crítica (entitlement) + vibración.
No prometer sirena con el teléfono bloqueado y la app muerta.

## GATT

Mismos UUID y binario que Android.
MTU lo negocia el sistema; fragmentar si el write falla por tamaño.

## Watch

watchOS etapa 2. WatchConnectivity. El iPhone es el radio.

## UWB

Nearby Interaction, iPhone 11+, etapa 2.

## Permisos y Review

Apple no ama las mesh en background. Hay que argumentar emergencia,
no tracking. No escanear sin filtro. No usar `audio` background de tapadera.
