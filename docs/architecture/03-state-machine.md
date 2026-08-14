# 3. State Machine

Fuente de verdad del estado del dispositivo. **No** usar booleanos sueltos
(`isSos`, `inDisaster`) como autoridad.

## Rol (ortogonal al estado)

| Rol | Valor u8 | UI |
|-----|----------|-----|
| CIVILIAN | 0 | Normal / Safety / SOS. Repetidor silencioso. |
| RESCUER | 1 | Dashboard de rescate. |

Cambiar de rol es una acción explícita (prototipo: botón RESCATE).

## Estados

| Estado | Valor u8 | Quién lo ve |
|--------|----------|-------------|
| NORMAL | 0 | Civil en calma. Repetidor si el usuario lo permite. |
| DISASTER | 1 | Alerta externa en zona. Sensibilidad alta. |
| SAFETY_CHECK | 2 | Pregunta “¿ESTÁS BIEN?” |
| SOS | 3 | Emergencia activa. Transmite. |
| RESCUE_CONTACT | 4 | Llegó RESCUE_PING / contacto. |
| RESOLVED | 5 | Emergencia cerrada. Vuelve a NORMAL. |

`ORANGE / DISASTER MODE` del brief = estado `DISASTER`.
`YELLOW / SAFETY CHECK` = `SAFETY_CHECK`.
`RED / SOS` = `SOS`.
`GREEN / NORMAL` = `NORMAL`.
`RESCUE` es **rol**, no estado.

## Transiciones permitidas

```
NORMAL ─────────────► DISASTER          (alerta zona / pre-alert)
NORMAL ─────────────► SAFETY_CHECK      (motion + contexto)
NORMAL ─────────────► SOS               (usuario: NECESITO AYUDA)
DISASTER ───────────► SAFETY_CHECK
DISASTER ───────────► SOS
DISASTER ───────────► NORMAL            (all-clear / timeout configurable)
SAFETY_CHECK ───────► NORMAL            (ESTOY BIEN)
SAFETY_CHECK ───────► SOS               (NECESITO AYUDA / timeout+severo)
SOS ────────────────► RESCUE_CONTACT    (RESCUE_PING dirigido a este device)
SOS ────────────────► RESOLVED          (usuario o RESCUE_CONFIRMED local)
RESCUE_CONTACT ─────► SOS               (se pierde contacto; sigue SOS)
RESCUE_CONTACT ─────► RESOLVED
RESOLVED ───────────► NORMAL            (inmediato tras resolver)
```

Cualquier otra transición se rechaza y se registra.

## Eventos de entrada

| Evento | Origen MVP | Origen futuro |
|--------|------------|---------------|
| `USER_NEED_HELP` | Botón SOS | Watch, Safety Check |
| `USER_IM_OK` | Botón | Watch |
| `USER_RESOLVE` | Botón | Rescatista |
| `ENTER_RESCUE_ROLE` | Botón RESCATE | Auth rescatista |
| `LEAVE_RESCUE_ROLE` | Botón | — |
| `PACKET_RESCUE_PING` | BLE | cualquier transport |
| `DISASTER_ALERT` | — | EarthquakeProvider |
| `SAFETY_TIMEOUT` | — | Event Engine |
| `SEVERE_NO_RESPONSE` | — | Event Engine |

El prototipo implementa transiciones de usuario + `PACKET_RESCUE_PING`.
El resto está en la máquina para no rediseñarla en etapa 2.

## Efectos al entrar

| Destino | BLE duty | GPS | UI | Audio |
|---------|----------|-----|----|-------|
| NORMAL | bajo | last-known | nada de terceros | no |
| DISASTER | medio | más frecuente | banner zona | no (salvo pre-alert) |
| SAFETY_CHECK | medio | update | modal 3 botones | patrón SAFETY |
| SOS | alto / según batería | update al transmitir | pantalla SOS | no continuo |
| RESCUE_CONTACT | alto | update | “RESCATISTAS CERCA” | patrón RESCUE |
| RESOLVED | bajo | off | confirmación breve | no |

Rol RESCUER: duty máximo independientemente del estado civil.

## Safety Check — opciones

- `ESTOY BIEN` → NORMAL
- `NECESITO AYUDA` → SOS inmediato
- `NO PUEDO RESPONDER` → incrementa riesgo (etapa 2); MVP trata como no respuesta
- Timeout sin respuesta + contexto severo → SOS (etapa 2; en prototipo no auto-SOS)

Filosofía: falso positivo preferible a falso negativo **en emergencia**,
no en NORMAL cotidiano.

## Implementación

`EmergencyStateMachine` en `:domain`, pura, testeable.
La UI y Platform observan `StateSnapshot(role, state)`.
