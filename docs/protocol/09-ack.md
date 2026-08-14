# 9. ACK protocol

Un ACK **no** significa “la persona fue encontrada”.

## ackKind

| Valor | Nombre | Significado | UI en el SOS |
|-------|--------|-------------|--------------|
| 1 | MESSAGE_RECEIVED | Un nodo (repetidor o rescue) recibió el SOS | “Tu mensaje viaja por la red” |
| 2 | MESSAGE_DELIVERED | Un nodo con rol RESCUER lo tiene | “Llegó a un rescatista” |
| 3 | RESCUE_CONTACT | El rescatista envió ping / contacto | “Los rescatistas están cerca” |
| 4 | RESCUE_CONFIRMED | Marcado localizado (humano) | “Marcado como localizado” |

## Packet ACK

- `type = ACK`
- `originDeviceId` = quien genera el ACK (repetidor o rescatista)
- `lat/lon` = del **ack-er**, no de la víctima (puede omitirse: accuracy 65535)
- payload:

```
offset 0: ackKind u8
offset 1–16: refMessageId (el SOS)
```

`ttl` de ACK: **10** (más bajo que SOS; no necesita 20 saltos de eco).

## Quién genera qué

| Evento | ackKind | Quién |
|--------|---------|-------|
| Repetidor recibe SOS nuevo | MESSAGE_RECEIVED | Opcional en prototipo (off por defecto para no inundar). Config `repeaterAck=false`. |
| Rescatista recibe SOS nuevo | MESSAGE_DELIVERED | Sí, automático |
| Rescatista pulsa CONTACTAR | además va RESCUE_PING; ACK RESCUE_CONTACT | Sí |
| Rescatista pulsa LOCALIZADO | RESCUE_CONFIRMED | Sí |

El prototipo: **solo el rescatista** genera ACK (DELIVERED al ver el SOS,
CONTACT al ping). Los repetidores no ACK para ahorrar radio.

## En el origen SOS

```
on ACK:
  if refMessageId no es mío → store-forward como cualquier packet
  if refMessageId es mío:
    actualizar lastAckKind = max(kind)
    UI según tabla
    NO interpretar DELIVERED como “ya me vieron físicamente”
```

## Dedup de ACK

Cada ACK tiene messageId propio. Varios DELIVERED del mismo SOS se aceptan;
la UI se queda con el mayor ackKind.
