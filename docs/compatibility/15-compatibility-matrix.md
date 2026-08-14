# 15. Matriz de compatibilidad

Leyenda: **Sí** / **Parcial** / **No** / **Depende de OEM-SoC** / **N/A**.

No ocultar límites. Si una fila es No, hay fallback.

## Versiones objetivo

| Plataforma | Mínimo | Notas |
|------------|--------|-------|
| Android | 8.0 API 26 | Permisos BLE clásicos + location |
| Android 12+ | API 31 | BLUETOOTH_SCAN/ADVERTISE/CONNECT |
| Android 14+ | API 34 | FGS types obligatorios |
| iOS | 14 | Core Bluetooth background ya restringido |

## BLE

| Capacidad | A8–11 | A12+ | iOS FG | iOS BG / lock | Fallback |
|-----------|-------|------|--------|---------------|----------|
| Scan filtrado UUID | Sí | Sí | Sí | Parcial, lento | Esperar; no flood |
| Scan sin filtro con pantalla off | No (OS para) | No | Sí en FG | No | Siempre filtrar |
| Advertise UUID visible | Depende SoC | Depende | Sí | No hacia Android | iOS SOS en FG; Android solo-central |
| Dual role | Depende | Mejor | Sí | Parcial | Solo central |
| GATT datos 244 B | Sí con MTU | Sí | Sí | Sí si conectado | Fragmentar |
| Scan con app muerta | Parcial PendingIntent | FGS | N/A | Restoration; force-quit No | Pedir no matar la app |
| Extended adv >31 B | No asumir | Depende | No base | No | Beacon corto + GATT |

## Background / batería

| Tema | Android | iOS | Fallback |
|------|---------|-----|----------|
| Repetidor con pantalla off | FGS + OEM | Throttle | Guía OEM; iOS “abre la app” |
| Battery saver | Depende OEM | Low Power Mode empeora BLE | Pedir exclusión |
| Reinicio del teléfono | BOOT_COMPLETED + usuario | No auto | Usuario abre la app |
| Doze | FGS ayuda | N/A | Duty cycle |

## OEM Android conocidos (campo; rellenar tras pruebas)

| OEM | Riesgo | Acción del usuario |
|-----|--------|--------------------|
| AOSP / Pixel | Bajo | Excluir batería |
| Samsung | Sleeping apps | Never sleeping |
| Xiaomi / HyperOS | Alto | Autostart + no restrictions |
| Huawei / Honor | Alto | Gestión manual |
| Otros | Depende | Matriz viva en field-log |

## Posicionamiento

| | Android | iOS | Fallback |
|--|---------|-----|----------|
| GNSS | Sí | Sí | last-known; accuracy 65535 |
| Background GNSS | Permiso extra / Play policy | Always + barra | No en prototipo |
| UWB | Pocos modelos | iPhone 11+ | No requerido |
| RSSI como detector personas | **Prohibido en producto** | Igual | Sonido + GNSS |

## Wi-Fi P2P

| | Android | iOS |
|--|---------|-----|
| Wi-Fi Direct | Heterogéneo | No como Android |
| Wi-Fi Aware | API 26+ hardware raro | No |
| Multipeer | No | Solo iOS–iOS |
| MVP | No implementar | No |

Fallback: BLE.

## Audio / watch / sismos

| | Android | iOS | Fallback |
|--|---------|-----|----------|
| Sonido lock screen | Parcial FGS ALARM | Parcial; Review | Vibración + UI SOS |
| Wear OS / watchOS | Etapa 2 | Etapa 2 | Teléfono |
| SASMEX API oficial | No existe pública | No | USGS/EMSC; no decir “oficial SASMEX” |
| EarthquakeProvider | Stub | Stub | Manual SOS |

## Interop mesh

| Camino | FG | BG |
|--------|----|----|
| Android → Android | Sí | Parcial OEM |
| Android → iOS | Sí | iOS escanea: Parcial lento |
| iOS → Android | Sí si iOS FG | **No** |
| iOS → iOS | Sí | Parcial overflow |

## Qué necesita interacción del usuario

1. Conceder Bluetooth y ubicación.
2. Elegir modo (arranca FGS).
3. En Android: desactivar optimización de batería.
4. En iOS SOS: dejar la app abierta.
5. No force-quit.

## Qué necesita la app activa / FGS

- Advertising continuo Android.
- Sonido de rescate fiable.
- GPS fresco.

Sin FGS / sin app iOS viva: best-effort degradado, no prometido.
