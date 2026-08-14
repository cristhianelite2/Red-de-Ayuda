# 16. Testing strategy

## Unitarias (`:domain`)

Obligatorias del plan:

- codec encode/decode roundtrip y test vectors hex
- TTL decrement y drop en 0
- expiración maxAge y timestamp futuro
- deduplicación LRU
- ACK kinds y refMessageId
- Store & Forward prioridad
- State machine transiciones legales e ilegales
- BatteryPolicy intervalos
- selección de transporte (MVP: solo BLE available)

Correr: `./gradlew :domain:test`

## Simulador (`:simulator`)

Cuatro nodos en memoria, sin BLE:

```
A SOS → B repetidor → C repetidor → D rescatista
```

Aserciones:

- D ve el SOS
- B y C no “muestran” SOS (flag `civilianUiAlerts == 0`)
- messageId único, hopCount = 3 en D
- TTL decrece
- ping de D llega a A
- segundo envío del mismo ID no duplica en D

## Integración BLE (teléfonos)

Ver [field-test-abc.md](field-test-abc.md).

Orden:

1. A y C foreground, sin B (1 hop)
2. A→B→C foreground
3. B pantalla off
4. Battery saver en B
5. Reinicio de B
6. (Futuro) Android–iOS

## Lo que este repo no puede hacer solo

La prueba de campo real necesita 3 teléfonos Android con BLE.
Este entorno de desarrollo es Windows sin emulador BLE mesh útil
(el emulador de Android **no** simula BLE peer-to-peer de forma fiable).

Por eso el simulador JVM es la prueba automatizada de protocolo.
El log de campo se rellena a mano en `docs/testing/field-results.md`.
