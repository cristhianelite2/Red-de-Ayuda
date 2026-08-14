# 4. EmergencyPacket v1

Protocolo binario little-endian. **No JSON.** Optimizado para GATT BLE, no para advertising.

`protocolVersion = 1`

## Layout

Cabecera fija: **48 bytes**. Luego `payload` (`payloadLen` bytes). Luego `auth` **16 bytes**.

| Offset | Size | Campo | Tipo | Notas |
|--------|------|-------|------|-------|
| 0 | 1 | version | u8 | Debe ser 1 |
| 1 | 1 | type | u8 | ver PacketType |
| 2 | 1 | flags | u8 | capabilities resumidas |
| 3 | 1 | ttl | u8 | default 20 |
| 4 | 1 | hopCount | u8 | 0 en origen |
| 5 | 1 | eventType | u8 | ver EventType |
| 6 | 1 | status | u8 | ver OriginStatus |
| 7 | 1 | battery | u8 | 0–100; 255 = desconocido |
| 8 | 16 | messageId | UUID bytes | único global |
| 24 | 8 | originDeviceId | bytes | efímero |
| 32 | 4 | timestamp | u32 | epoch segundos UTC |
| 36 | 4 | latitude | i32 | microgrados (grados × 1e6) |
| 40 | 4 | longitude | i32 | microgrados |
| 44 | 2 | accuracyMeters | u16 | 65535 = desconocido |
| 46 | 2 | payloadLen | u16 | 0–180 |
| 48 | N | payload | bytes | opaco; claro en MVP |
| 48+N | 16 | auth | bytes | ceros en prototipo 0 |

Tamaño máximo: 48 + 180 + 16 = **244 bytes**. Cabe en un ATT write tras MTU 185+.
Si el MTU es 23 (default BLE 4.0), fragmentar a nivel GATT (ver spec BLE).

## PacketType

| Valor | Nombre | Quién origina |
|-------|--------|----------------|
| 1 | SOS | dispositivo en emergencia |
| 2 | ACK | cualquier nodo / rescatista |
| 3 | RESCUE_PING | rescatista, target único |
| 4 | RESCUE_PING_ALL | rescatista; UI desactivada por defecto |
| 5 | SAFETY_CHECK | no se inunda la mesh en MVP |
| 6 | RESPONSE | “ESTOY AQUÍ” / respuestas cortas |
| 7 | LOCATION_UPDATE | mismo origin, nuevo messageId |

Valores 0 y 8–255: reservados. Descartar si unknown.

## EventType (contexto del SOS)

| Valor | Nombre |
|-------|--------|
| 0 | UNKNOWN |
| 1 | USER_INITIATED |
| 2 | EARTHQUAKE |
| 3 | COLLAPSE |
| 4 | FLOOD |
| 5 | OTHER_DISASTER |

## OriginStatus

| Valor | Nombre |
|-------|--------|
| 0 | UNKNOWN |
| 1 | NEED_HELP |
| 2 | IM_HERE |
| 3 | CANNOT_RESPOND |
| 4 | RESOLVED |

## Flags (bitmask)

| Bit | Significado |
|-----|-------------|
| 0 | BLE |
| 1 | WIFI |
| 2 | UWB |
| 3 | WATCH |
| 4 | GATEWAY_INTERNET |
| 5 | RESCUE_NODE |
| 6 | HAS_ENCRYPTED_PAYLOAD (etapa 2) |
| 7 | reservado |

## Coordenadas

Microgrados: `19.432608` → `19432608`.
`0,0` con `accuracyMeters=65535` = ubicación no disponible (no inventar).
No enviar más precisión de la que GNSS da: `accuracyMeters` es obligatorio cuando hay fix.

## Payload

Máximo 180 bytes. Mensajes de rescate predefinidos (IDs de 1 byte) preferibles a texto libre.

Códigos de payload de texto UTF-8 corto permitidos en MVP (≤ 80 bytes):

- `LOS RESCATISTAS ESTAN CERCA`
- `ESTAMOS BUSCANDOTE`
- `MANTEN EL TELEFONO ENCENDIDO`
- `RESPONDE SI PUEDES`
- `ESTOY AQUI`

Para ACK, payload:

```
ackKind: u8
refMessageId: 16 bytes
```

Para RESCUE_PING:

```
requestId: 16 bytes
targetDeviceId: 8 bytes
action: u8   (1=CONTACT, 2=SOUND, 3=VIBRATE, 4=LOCATION_REQUEST, 5=SCREEN)
```

## Auth

Prototipo 0: 16 bytes `0x00`. El codec **acepta** ceros.
Etapa 2: HMAC-SHA256 truncado o Ed25519 (cambiar version o flag bit 6).

Los test vectors están en [`../../protocol/test-vectors/`](../../protocol/test-vectors/).

## Lo que NO va en el paquete

Nombre, teléfono, foto, IMEI, cuenta, mapa de contactos, RSSI, historial médico.
El repetidor no necesita nada de eso.
