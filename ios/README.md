# iOS — Red de Ayuda

Este árbol **no se compila en Windows**. Es el contrato para cuando exista Mac/Xcode.

- Mínimo: iOS 14, Swift, UIKit, Core Bluetooth nativo.
- El binario `EmergencyPacket` v1 es el de [`../protocol/test-vectors`](../protocol/test-vectors).
- `RdaPacket.swift` debe pasar los mismos hex que `:domain` en Kotlin.
- Limitaciones reales: [`../docs/architecture/14-ios.md`](../docs/architecture/14-ios.md)

## Cuando haya Mac

1. Crear proyecto Xcode `RedDeAyuda` (bundle `mx.reddeayuda.app`).
2. Copiar `RedDeAyudaCore/RdaPacket.swift` al target.
3. Dual `CBCentralManager` + `CBPeripheralManager`, UUID de [`../docs/protocol/05-ble.md`](../docs/protocol/05-ble.md).
4. SOS: `idleTimerDisabled = true` y aviso de que Android no ve un iPhone en background.
5. Tests unitarios leyendo los `.hex`.

No usar Flutter ni React Native para BLE, GPS ni background.
