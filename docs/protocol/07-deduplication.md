# 7. Deduplicación

Evita loops `A → B → C → A` y duplicados multipath (BLE + futuro Wi-Fi).

## Clave

`messageId` de 16 bytes. Un paquete = un ID de por vida.
Un `LOCATION_UPDATE` **nuevo** lleva un messageId **nuevo**.
Un reenvío del mismo SOS conserva el messageId; solo cambian `ttl` y `hopCount`.

## Seen-cache

Estructura: LRU + set.

- Capacidad prototipo: **1024** IDs.
- Al insertar uno nuevo, si está lleno se evicta el más antiguo.
- Persistente en Android (tabla `seen_ids`) para sobrevivir a muerte del proceso.
- En memoria en tests/simulador.

Operación:

```
if cache.contains(messageId):
    drop
else:
    cache.add(messageId)
    process
```

“Procesar” incluye store/forward. Un duplicado **no** se retransmite.

## ACK y ping

Cada ACK tiene su propio `messageId`.
El `refMessageId` del payload apunta al SOS original.
Dedup del ACK es por el messageId del ACK, no por el SOS.

Si dos rescatistas mandan ACK del mismo SOS, son dos messageId distintos;
ambos pueden viajar. El origen SOS muestra el mejor `ackKind` recibido.

## Multipath

El mismo SOS puede llegar por B y por C. El segundo se descarta.
Correcto: best-effort, no hay conteo de caminos en el MVP
(el dashboard futuro puede guardar `hopCount` del primero).

## Reloj

No usar timestamp como ID. Relojes mal puestos no deben fusionar mensajes.
messageId se genera con UUID v4 (o v7 si hay reloj fiable) en el origen.
