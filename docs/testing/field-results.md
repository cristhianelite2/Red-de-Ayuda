# Resultados de prueba

## Simulador JVM (sin radio) — 2026-08-14

Ejecutado con `:simulator:run` y `:domain:test`. **PASS.**

| Check | Resultado |
|-------|-----------|
| A SOS → B → C → D | OK |
| hopCount en D | 3 |
| B y C no muestran SOS al civil | OK |
| Dedup (no duplica en D) | OK |
| RESCUE_PING SOUND solo en A | OK |
| ACK DELIVERED/CONTACT en A | OK |
| Test vectors hex v1 | OK (codec roundtrip) |
| TTL / LRU / state machine / battery | OK |

No sustituye la prueba BLE en hardware. El emulador Android no simula mesh BLE P2P.

## Campo en teléfonos reales

Hace falta 3 Android con BLE. Procedimiento: [field-test-abc.md](field-test-abc.md).

| Fecha | A modelo / Android | B | C | Variante | hopCount | ACK | Ping sonido | Notas OEM |
|-------|--------------------|---|---|----------|----------|-----|-------------|-----------|
| — | — | — | — | F1 foreground | — | — | — | Pendiente de 3 teléfonos |

## Fallos conocidos de plataforma (pre-campo)

Ver matriz §15. No son bugs de protocolo:

- iOS background → Android: no descubrible
- Xiaomi/Huawei: FGS muerto a los segundos
- Emulador Android: BLE P2P no fiable
