# 10. Rescue protocol

## Rol RESCUER

UI distinta. Autorización real (credencial) es etapa 2.
En el prototipo: botón “RESCATE” (cualquier tester puede activarlo;
documentar que en producción esto es un riesgo y requiere auth).

## Qué ve el rescatista

Lista de SOS **recibidos por este dispositivo** (directos o reenviados):

- messageId corto (8 hex)
- última lat/lon/accuracy
- timestamp
- batería
- hopCount
- estado (NEED_HELP, etc.)
- lastAckKind local

No usa RSSI como “hay una persona aquí”. RSSI puede mostrarse como
indicador técnico de radio **opcional y etiquetado** (“señal de radio, no distancia”).

## Acciones

| UI | Packet | action u8 |
|----|--------|-----------|
| CONTACTAR | RESCUE_PING | 1 CONTACT |
| HACER SONAR | RESCUE_PING | 2 SOUND |
| VIBRAR | RESCUE_PING | 3 VIBRATE |
| SOLICITAR UBICACIÓN | RESCUE_PING | 4 LOCATION_REQUEST |
| MARCAR LOCALIZADO | ACK RESCUE_CONFIRMED | — |

CONTACT incluye vibración + mensaje en pantalla en el objetivo.
SOUND es el patrón de localización.

## RESCUE_PING payload

```
requestId: 16 B
targetDeviceId: 8 B
action: u8
```

`targetDeviceId` es el `originDeviceId` del SOS, **no** el messageId.
Solo el dispositivo cuyo id coincide ejecuta audio/UI.

`RESCUE_PING_ALL` (type 4): mismo payload con target = 8 bytes 0xFF.
**Prohibido en UI del prototipo.** El codec lo acepta para tests.

## Sonido de localización

Patrón **RESCUE**:

```
BIP BIP BIP  (3 × 150 ms on / 150 ms off)
pausa 700 ms
BIP BIP BIP
pausa 700 ms
BIP BIP BIP
```

Frecuencia ~ 1 kHz, volumen máximo del stream de alarma.
No es el patrón de SAFETY_CHECK (un solo bip largo) ni el de SOS crítico
(continuo 2 s — no usar en repetidores).

Restricciones: ver matriz. En Android el prototipo usa `USAGE_ALARM`
desde el FGS. Con pantalla bloqueada puede fallar en OEM; fallback vibración
+ full-screen intent.

## Respuesta de la víctima

Botón `ESTOY AQUÍ` → packet RESPONSE, status IM_HERE, payload corto.
Viaja store-forward hacia quien sea (el rescatista lo verá si la mesh vuelve).

## Smartwatch

Etapa 2. El ping puede reenviarse al watch como canal extra.
El teléfono sigue siendo el nodo de radio.

## UWB

Etapa 2. Si ambos tienen UWB: distancia relativa en UI rescue.
Sin UWB: GNSS del packet + “hacer sonar”. Nunca RSSI como detector de personas.
