# 12. Battery / Survival strategy

Objetivo: un civil en NORMAL no debe notar la app.
Un SOS debe comunicar horas o días, no minutos.

## Duty cycles (prototipo)

Tiempos orientativos. El `BatteryPolicy` los calcula; no hardcodear en BLE.

### NORMAL (repetidor silencioso)

- Advertise: 3 s ON / 57 s OFF
- Scan: 3 s ON / 57 s OFF (desfasado 15 s del advertise)
- GPS: no periódico; last-known si el OS lo tiene
- CPU: dormir

### DISASTER (etapa 2)

- Advertise/Scan: 3 s / 17 s
- GPS: cada 10 min si hay movimiento

### SOS

Depende de batería:

| Batería | Reintento origen | Advertise | Scan |
|---------|------------------|-----------|------|
| > 50% | 5 min | casi continuo (152.5 ms) mientras FGS | 5 s / 10 s |
| 20–50% | 10 min | 3 s / 12 s | 3 s / 12 s |
| 5–20% | 20–30 min | 2 s / 28 s | 2 s / 28 s |
| < 5% | 30 min | 2 s / 58 s | 1 s / 59 s |

**Override:** si hay peer ahora, transmitir ahora. El intervalo es de reintento
del origen, no del repetidor.

### RESCUE (rol)

Scan + advertise agresivos. El rescatista acepta el gasto.
GPS periódico 15–30 s.

## Lo que no se hace

- GPS + BLE + Wi-Fi + CPU al máximo en NORMAL.
- Wi-Fi scan en MVP.
- Mantener GATT conectado.

## Android

Foreground Service con notificación. Eso ya cuesta batería.
Pedir exclusión de battery optimization en onboarding.
Doze/App Standby: el FGS ayuda; OEM puede ignorarlo.

## iOS

No hay FGS. Background BLE está throttled por diseño.
SOS: mantener app en foreground es la estrategia de supervivencia de radio,
aunque gaste pantalla. Ofrecer brillo mínimo y `idleTimerDisabled`.

## Medición

Loguear `battery` en cada SOS. No afirmar “dura 48 h” sin prueba de campo.
