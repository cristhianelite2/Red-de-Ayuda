# 11. Security model

## Amenaza (honesta)

Una mesh BLE abierta puede ser:

- inundada de SOS falsos
- rejugada (replay)
- usada para rastrear `originDeviceId`
- leída por cualquiera en radio (MVP en claro)

El prototipo **no** es seguro contra un adversario con nRF Sniffer.
El modelo de etapa 2 se especifica ahora para no pintar al Core contra la pared.

## Principios

1. Un repetidor transporta bytes. No necesita nombre ni identidad civil.
2. Un civil no ve SOS ajenos (control de UI, no de radio: el radio es broadcast).
3. Un rescatista en producción debe autenticarse. En el prototipo, no.
4. IDs efímeros, no IMEI.
5. messageId + timestamp + TTL reducen loops y replays toscos.

## Prototipo 0 (implementado)

| Control | Estado |
|---------|--------|
| auth 16 bytes | ceros; el codec los acepta |
| cifrado payload | no |
| firma | no |
| rotación originDeviceId | se genera al instalar; no rota aún |
| replay window | timestamp ±5 min futuro, maxAge 24 h |
| messageId | UUID aleatorio |
| UI privacy | repetidor no renderiza SOS |

## Etapa 2 (diseño, no código)

- Identidad: par Ed25519 por dispositivo, clave pública enrollada.
- Packet: firma Ed25519 de la cabecera+payload (el campo auth crece o version=2).
- Payload SOS: cifrado para **rol rescue** con clave de grupo de incidentes
  o ECIES hacia gateway. El repetidor no descifra.
- Rotación de `originDeviceId` cada N horas en NORMAL; se congela en SOS
  para que el ping encuentre el mismo target.
- Anti-replay: nonce en flags/payload + ventana corta.
- Rescue auth: certificado de cuerpo de rescate; sin él la UI rescue no descifra.

## Privacidad de radio (límite)

Mientras el payload vaya en claro, un sniffer ve lat/lon.
Mitigación real = cifrado etapa 2, no “no anunciar el UUID”.
El beacon **nunca** lleva ubicación.

## Human safety

La app civil **no** dice “hay alguien atrapado aquí”.
No convierte civiles en rescatistas.
El mapa de víctimas no es público.
