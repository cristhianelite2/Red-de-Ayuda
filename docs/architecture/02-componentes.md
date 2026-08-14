# 2. Diagrama de componentes

## Vista lógica

```
┌─────────────────────────────────────────────────────────────┐
│ UI                                                          │
│  Normal  │  SafetyCheck  │  SOS  │  Rescue                  │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│ Emergency Core                                              │
│  EmergencyEngine  PacketFactory  StoreForwardEngine         │
│  AckEngine  RescueEngine  MessageCache  StateMachine        │
└─┬────────────┬────────────┬────────────┬────────────┬───────┘
  │            │            │            │            │
  ▼            ▼            ▼            ▼            ▼
Transport   Positioning  Sensors     Security     Storage
 Router       Layer      Engine
  │            │            │            │            │
  ├ BLE        ├ GNSS       ├ Motion     ├ Crypto     ├ PacketRepo
  ├ Wi-Fi*     └ UWB*       ├ Fall*      ├ Identity   └ SeenCache
  └ LoRa*                   └ Quake*     └ Replay
                                          │
                               Platform Android / iOS
```

`*` = no implementado en el prototipo; la interfaz existe o está documentada.

## Módulos de código (Android / JVM)

| Módulo | Tipo | Responsabilidad |
|--------|------|-----------------|
| `:domain` | Kotlin JVM | Packet, codec, state machine, store-forward, interfaces |
| `:data` | Android library | Persistencia SQLite de cola y seen-cache |
| `:transport-ble` | Android library | Advertising, scan, GATT server/client |
| `:platform` | Android library | Permisos, FGS, batería, audio, vibración, GNSS |
| `:app` | Android application | UI mínima de 3 modos |
| `:simulator` | Kotlin JVM | Nodos en memoria A→B→C→D |

## Contratos entre componentes

### EmergencyEngine → Transport

El engine llama `transport.send(packet)`. El transport no interpreta `eventType`.
El engine se suscribe a `transport.incoming` y llama `receive(packet)`.

### StoreForwardEngine

Única autoridad para decidir si un paquete se guarda, se descarta o se reenvía.
Usa MessageCache (dedup), TTL, maxAge y prioridad.

### TransportRouter (futuro)

En el MVP hay un solo transport (BLE). El router se limita a delegar.
Cuando existan Wi-Fi/LoRa, el mismo packet puede salir por varios caminos.
El receptor deduplica por `messageId`.

### PositioningProvider

```
getLastKnown()
requestUpdate()
isAvailable()
```

Implementación MVP: GNSS. UWB es adapter futuro, no entra al Core.

### EarthquakeProvider (stub)

```
getRecentEvents()
getEvent(id)
getAffectedArea(event)
getMagnitude()
getEpicenter()
getTimestamp()
```

No acoplar el engine a USGS, EMSC o SASMEX. SASMEX no tiene API pública oficial.

## Flujo SOS (prototipo)

```
Usuario pulsa SOS
  → StateMachine.transition(ENTER_SOS)
  → GNSS.getLastKnown()
  → PacketFactory.create(SOS)
  → store(packet)
  → BLETransport.start(role=SOS)
  → al descubrir peer: forward inmediato
  → reintento periódico según batería
```

## Flujo repetidor

```
BLE scan encuentra beacon RA
  → GATT sesión < 2 s
  → receive(packets)
  → validate + TTL + dedup
  → store
  → re-advertise pendingCount++
  → UI: no muestra nada del SOS
```

## Flujo rescue

```
Rescatista pulsa RESCATE
  → rol RESCUER, scan agresivo
  → UI lista SOS por messageId (no nombre)
  → CONTACTAR → RESCUE_PING(target=originDeviceId)
  → ACK MESSAGE_DELIVERED hacia el origen
```

## Simulador

No usa BLE. Cada `SimNode` tiene un `InMemoryTransport` que entrega al “aire”
cuando dos nodos están en el mismo `RadioWorld` y en rango.
Valida el Core antes de probar radios reales.
