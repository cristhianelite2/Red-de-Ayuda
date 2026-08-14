# 8. TTL y expiración temporal

Dos límites independientes. Basta con que **uno** falle para no reenviar.

## TTL de saltos

- Campo `ttl` u8. Default **20**.
- Origen envía con `ttl = 20`, `hopCount = 0`.
- Cada forward: `ttl -= 1`, `hopCount += 1`.
- Si al recibir `ttl == 0`: no store para forward (el nodo actual puede actuar:
  Rescue UI, ACK local, sonido si el ping es para él).
- Configurables: `defaultTtl`, `maxTtl` (cap 20 en prototipo).

20 saltos en una mesh oportunista urbana es holgado para A→B→C→D.
Evita tormentas si hay cientos de teléfonos.

## Edad absoluta

- `timestamp` = epoch segundos en el **origen**.
- `maxAgeSeconds` default **86400** (24 h).
- Si `now - timestamp > maxAge` (con `now` del receptor): drop.
- Si `timestamp > now + 300` (5 min futuro): drop (reloj desfasado / replay tosco).

No se exige NTP. En desastre el reloj local puede estar mal;
el margen de 5 min hacia el futuro reduce replays groseros.
Etapa 2: ventana más estricta con firmas.

## Interacción

```
fun shouldForward(p, now): Boolean {
  if (p.ttl == 0) return false
  if (now < p.timestamp - CLOCK_SKEW) return false
  if (now - p.timestamp > maxAge) return false
  return true
}
```

Al reenviar con `ttl-1 == 0`, ese envío **sí** se hace (último salto).
El receptor lo procesa y no lo vuelve a poner en cola de forward.

## Limpieza

Cada 60 s (y al arrancar): borrar de la cola lo que no `shouldForward`.
Evict de seen-cache es LRU, no por TTL (un ID muerto no debe reaparecer
si un nodo viejo lo reinyecta dentro de maxAge).
