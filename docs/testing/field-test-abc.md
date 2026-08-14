# Prueba de campo A → B → C

## Objetivo

Demostrar, **sin Internet**, que un SOS sale de A, pasa por un repetidor silencioso B
y llega al rescatista C por BLE, con messageId, TTL, dedup, store-forward, ACK y RESCUE_PING.

## Preparación

1. Tres teléfonos Android 8+ con BLE.
2. Instalar `app-debug.apk` en los tres.
3. Modo avión **ON**, Bluetooth **ON**. Wi-Fi off. Datos off.
4. Conceder permisos de Bluetooth, ubicación, notificación, FGS.
5. En B: desactivar optimización de batería para Red de Ayuda.
6. Distancia: 2–5 m entre A-B y B-C; A y C **fuera de alcance** si es posible
   (otra habitación / >15 m) para forzar el hop por B.

## Pasos

### C — Rescatista

1. Abrir app → **RESCATE**.
2. Debe aparecer notificación persistente.
3. No debe haber SOS aún.

### B — Repetidor

1. Abrir app → **REPETIDOR**.
2. La UI no lista víctimas. Solo “Repetidor activo” + log técnico opcional
   (en prototipo el log técnico existe para debug; en civil real se oculta).
3. No debe sonar.

### A — SOS

1. Abrir app → **SOS**.
2. Esperar fix GPS o “ubicación desconocida” (válido).
3. El SOS se anuncia.

### Esperado

| Check | OK |
|-------|----|
| C muestra SOS con messageId corto | |
| hopCount ≥ 1 (2 si A no alcanza C) | |
| B no muestra ficha de víctima ni suena | |
| C pulsa CONTACTAR → A muestra “RESCATISTAS CERCA” | |
| C pulsa HACER SONAR → A reproduce BIP-BIP-BIP × 3 | |
| A muestra ACK DELIVERED o CONTACT | |
| Reenviar el mismo SOS no duplica la fila en C | |

### Variantes

| ID | Variante | Esperado | Resultado |
|----|----------|----------|-----------|
| F1 | Los tres en foreground | Debe pasar | |
| F2 | B pantalla off 2 min | Depende OEM | |
| F3 | B battery saver | Suele fallar sin exclusión | |
| F4 | Reiniciar B, no abrir app | Suele fallar | |
| F5 | A y C sin B, cerca | 1 hop | |

## Resultados

Registrar en [field-results.md](field-results.md).
Actualizar [../compatibility/15-compatibility-matrix.md](../compatibility/15-compatibility-matrix.md)
con modelo/OEM real.

## Si falla

1. ¿Bluetooth on y avión no mató BLE? (algunos OEM apagan BLE en avión: sacar de avión, datos off).
2. ¿FGS vivo? Notificación persistente.
3. ¿`isMultipleAdvertisementSupported`? Log en pantalla.
4. Logs `adb logcat -s RdaBle RdaCore`.
