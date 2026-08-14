# 1. Arquitectura completa

## Objetivo

Red de Ayuda es un sistema de **best-effort delivery** para desastres. No es una app SOS de un botón
que llama al 911. Es un **protocolo de emergencia** que usa teléfonos existentes como nodos
oportunistas cuando cae la infraestructura celular.

## Separación innegociable

| Capa | Sabe | No sabe |
|------|------|---------|
| **Emergency Core** | paquetes, estado, TTL, dedup, ACK, store-forward | BLE, Wi-Fi, LoRa, GPS concreto |
| **Transport Layer** | descubrir, enviar, recibir bytes | significado del SOS |
| **Positioning Layer** | lat/lon/accuracy o distancia relativa | cómo se transmite |
| **Sensor/Event Engine** | movimiento, zona, umbrales | radio |
| **Platform** | permisos, FGS, background, OEM | protocolo |
| **UI** | modos Normal / Safety Check / SOS / Rescue | bytes BLE |
| **Backend / Gateway** | Internet cuando existe | no es requisito de la mesh |

El Core solo expone:

```
createEmergency()
createPacket()
send(packet)
receive(packet)
store(packet)
forward(packet)
ack(packet)
```

`Transport` solo expone:

```
discover()
start()
stop()
send(packet)
receive(packet)
isAvailable()
getCapabilities()
```

## Emergency Opportunistic Mesh

No hay conexiones permanentes. Un nodo:

1. Anuncia un beacon corto (“soy Red de Ayuda, rol X, tengo N paquetes”).
2. Descubre un peer.
3. Abre una sesión GATT breve.
4. Intercambia paquetes desconocidos.
5. Se desconecta.
6. Guarda lo que no pudo entregar.

A no necesita un camino simultáneo hasta D:

```
SOS A  →  BLE  →  B (guarda)  →  BLE  →  C (guarda)  →  BLE  →  D rescatista
```

## Por qué BLE no es flooding de advertisements

Un `EmergencyPacket` v1 tiene cabecera de 48 bytes + payload + 16 bytes de auth.
Un advertisement BLE legacy tiene **31 bytes** de payload de usuario.

Por eso el transporte BLE es de **dos capas**:

1. Beacon (advertising) — presencia y meta mínima.
2. GATT — el paquete completo.

El Core no conoce esta decisión. `BLETransport` la implementa.

## Asimetría Android ↔ iOS (diseño, no bug)

iOS en background coloca los service UUIDs en un overflow que **solo otro iOS** lee.
Android **no descubre** un iPhone que anuncia en segundo plano.

| Origen | Destino | Background | ¿Funciona? |
|--------|---------|------------|------------|
| Android | Android | sí | Sí (con FGS + filtro UUID) |
| Android | iOS | iOS escanea | Sí (lento) |
| iOS foreground | Android | sí | Sí |
| iOS background | Android | sí | **No** |
| iOS background | iOS | sí | Parcial (overflow) |

Producto: en SOS, iOS debe pedir “mantén la app abierta”. Un iPhone force-quit deja de ser nodo.

## Degradación funcional

```
BLE disponible        → usar BLE (MVP)
BLE + Wi-Fi           → etapa 2, elegir o ambos; dedup por messageId
UWB disponible        → posicionamiento relativo, no mesh
Sin UWB               → BLE + GNSS
Sin LoRa              → irrelevante; el protocolo ya reserva el adapter
Sin Internet          → la mesh local sigue
Sin smartwatch        → el teléfono es el nodo
```

## Identidad

`originDeviceId` es de 8 bytes **efímero**, no IMEI, no nombre, no cuenta.
Se rota según política de seguridad. Un repetidor no muestra identidad.

## Roles vs estados

- **Rol:** `CIVILIAN` | `RESCUER`
- **Estado:** `NORMAL` | `DISASTER` | `SAFETY_CHECK` | `SOS` | `RESCUE_CONTACT` | `RESOLVED`

Un rescatista no entra a SOS salvo que él mismo necesite ayuda.
Un civil en NORMAL puede ser **repetidor silencioso**.

## Backend

Opcional. Si un nodo obtiene Internet, puede ser **gateway**:
sube paquetes SOS al servidor y baja `RESCUE_PING` hacia la mesh.
Si el backend desaparece, la mesh no se detiene.

## MVP vs etapas

**MVP (esta fase):** Android nativo, BLE, packet, messageId, TTL, store-forward,
repetidor silencioso, SOS, ACK, Rescue, RESCUE_PING, sonido, GPS, Safety Check UI mínima,
survival duty-cycle básico.

**Etapa 2:** iOS, Wi-Fi, watches, EarthquakeProvider real, Disaster Mode, Pre-alert,
dashboard, cifrado, UWB.

**Etapa 3:** LoRa, satélite, nodos dedicados, integración gubernamental.

## Código compartido

Especificación binaria + test vectors hex. Implementaciones nativas.
El módulo `:domain` es Kotlin JVM puro (reutilizable en tests y simulador).
Sensores, BLE, GPS y background son nativos de cada plataforma.
