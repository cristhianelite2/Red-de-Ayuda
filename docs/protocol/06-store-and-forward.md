# 6. Store & Forward

Algoritmo del Core. Independiente del transporte.

## Invariante

Un nodo puede recibir un paquete sin poder entregarlo ahora.
Lo guarda y lo retransmite cuando aparece **cualquier** peer compatible.
A no mantiene conexión con D.

## Cola

Persistente (SQLite en Android; memoria en simulador).
Cada entrada:

- packet bytes (o campos)
- `firstSeenAt`
- `lastSentAt`
- `sendCount`
- `priority`
- `originHop` (hopCount al recibir)

Capacidad: 64 paquetes en prototipo (FIFO + prioridad; si llena, drop del de menor
prioridad y más viejo). Emergencia real podrá subir el cap por config.

## Prioridad

| Prioridad | Tipos |
|-----------|--------|
| 0 máxima | RESCUE_PING / RESCUE_PING_ALL cuyo `targetDeviceId` es este nodo |
| 1 | SOS |
| 2 | ACK (MESSAGE_DELIVERED, RESCUE_CONTACT) |
| 3 | ACK (MESSAGE_RECEIVED), LOCATION_UPDATE, RESPONSE |
| 4 | resto |

Al elegir qué enviar en una sesión: ordenar por prioridad, luego `firstSeenAt`.

## receive(packet)

```
1. codec.decode; si falla → drop
2. version != 1 → drop (futuro: upgrade path)
3. auth: prototipo acepta ceros; etapa 2 verifica
4. si expired(timestamp, maxAge) → drop
5. si ttl == 0 → drop (no forward; puede loguearse)
6. si messageId en seen-cache → drop (no store, no forward)
7. seen-cache.add(messageId)
8. si type == RESCUE_PING y target == yo → RescueEngine.handle (sonido/UI)
9. si type == ACK y ref es mío → AckEngine.handle
10. store en cola si debe reenviarse (ttl > 0 y no expired)
11. si rol RESCUER y type == SOS → UI rescue
12. si rol CIVILIAN → no UI de terceros
```

## forward

Al descubrir un peer (o al completar GATT):

```
para cada packet en cola (orden prioridad):
  si ttl == 0 o expired → delete
  else
    clone = packet
    clone.ttl = packet.ttl - 1
    clone.hopCount = packet.hopCount + 1
    si clone.ttl == 0:
      enviar este último salto sí (el receptor lo verá con ttl 0 y no reenviará)
    send(clone)
    lastSentAt = now
```

El **origen SOS** también guarda su propio SOS y lo reenvía.
Intervalo de reintento del origen: 5 / 10 / 20–30 min según batería.
Si aparece un peer **antes** del intervalo: enviar ahora.
Los repetidores **no** esperan ese intervalo: reenvían al instante en la sesión.

## Qué no hace un repetidor

- Mostrar SOS, mapa, nombre, distancia.
- Sonar, vibrar, encender pantalla por un SOS ajeno.
- Intentar chatear con la víctima.
- Decir “hay una persona cerca”.

Solo: validar, guardar, retransmitir.

## Gateway (etapa 2)

Si hay Internet y el nodo acepta ser gateway, los SOS de la cola se POST al backend
una vez. El `messageId` evita duplicados en servidor. No bloquea el forward BLE.
