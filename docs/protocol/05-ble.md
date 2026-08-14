# 5. BLE protocol specification

BLE es el **único** transporte del MVP. El Core no importa estas UUIDs.

## Identificadores

Usar 128-bit propios (no hay 16-bit SIG asignado).

| Uso | UUID |
|-----|------|
| Service | `a1e00001-5244-4159-0001-726564617975` |
| META (read) | `a1e00002-5244-4159-0001-726564617975` |
| INBOX (write) | `a1e00003-5244-4159-0001-726564617975` |
| OUTBOX (notify) | `a1e00004-5244-4159-0001-726564617975` |
| SEEN (read/write) | `a1e00005-5244-4159-0001-726564617975` |

ASCII mnemónico: `RD AY` en el UUID (`5244-4159`).

Manufacturer ID del beacon: **0xFFFF** (especificación de prueba; no es Company ID oficial).
En producción hay que registrar un Company Identifier Bluetooth SIG o usar solo service data.

## Beacon (advertising legacy)

Máximo ~31 bytes de AdvData.

Contenido:

1. Flags (3 bytes típicos, los pone el stack).
2. Service UUID 128-bit incompleto o completo (18 bytes con header AD).
3. Manufacturer specific data (si cabe):

| Offset | Size | Campo |
|--------|------|-------|
| 0–1 | 2 | magic `R` `A` (0x52 0x41) |
| 2 | 1 | protocolVersion |
| 3 | 1 | role (0 civil, 1 rescue) + state en nibble alto |
| 4 | 1 | pendingCount (cap 255) |
| 5 | 1 | beaconFlags (bit0=tiene SOS, bit1=quiere intercambio) |

Si el UUID de 128-bit no deja sitio al manufacturer data, **priorizar el UUID**.
El discovery se filtra por service UUID, no por nombre.

**Nunca** poner lat/lon ni messageId en el beacon.

Advertising: connectable (ADV_IND). Intervalos recomendados Apple/Android: 152.5 ms en SOS;
en NORMAL usar duty cycle (anunciar 2–3 s cada 45–60 s).

## Scan

Siempre **filtrado** por el Service UUID. Scan sin filtro en Android se detiene al apagar pantalla.

iOS background: el UUID debe estar en `Info.plist` / `bluetooth-central`.
Scan `nil` en background = cero resultados.

## Sesión GATT (objetivo < 2 s)

```
Central descubre beacon
  → connect
  → request MTU 517 (Android) / iOS negocia solo
  → discover services
  → read META
  → write SEEN (lista compacta de messageId que ya tiene el central, máx 16 IDs)
  → read SEEN del peripheral (o notify)
  → para cada packet desconocido:
        peripheral NOTIFY OUTBOX  o  central WRITE INBOX
  → disconnect
```

Un ATT write/notify lleva **un** EmergencyPacket completo (o fragmentado si MTU bajo).

### Fragmentación GATT (MTU 23)

Header de fragmento (no es el EmergencyPacket):

```
fragFlags: u8   bit0=first, bit1=last
fragIndex: u8
messageId: 16 B
chunk: resto
```

Reensamblar por messageId. Timeout 5 s → drop. El prototipo intenta subir el MTU
y, si queda 23, fragmenta.

### META (8 bytes)

| Offset | Size | Campo |
|--------|------|-------|
| 0 | 1 | protocolVersion |
| 1 | 1 | role |
| 2 | 1 | state |
| 3 | 1 | pendingCount |
| 4 | 2 | capabilities flags (u16) |
| 6 | 2 | maxPacketsThisSession |

### SEEN

```
count: u8
ids: count × 16 bytes
```

Máximo 16 IDs por sesión para acotar tiempo.

## Dual role

Cada nodo intenta ser **Peripheral (GATT server + advertise)** y **Central (scan + connect)**.

Si `isMultipleAdvertisementSupported() == false`: solo Central.
Ese teléfono no será descubrible; solo inicia sesiones hacia quien anuncia.

## Política de conexión

- No mantener GATT abierto “por si acaso”.
- Un peer a la vez en el prototipo (evita bugs de stack).
- Backoff si un address falla 3 veces (30 s).
- Android: address puede ser random; no usar MAC como identidad de protocolo.
  La identidad es `originDeviceId` dentro del packet.

## Background

Ver matriz de compatibilidad. Resumen operativo del prototipo:

- Android: Foreground Service + notificación “Red de Ayuda activa” + scan filtrado.
- iOS: `bluetooth-central` + `bluetooth-peripheral` + restoration. SOS en foreground.

## Interoperabilidad

Mismos UUID, mismo binario little-endian, mismo orden de sesión.
Android → Android es el MVP demostrable en este repo.
Android → iOS se valida cuando exista el binario iOS.
