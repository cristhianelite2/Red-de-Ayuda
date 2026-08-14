# Changelog

## [0.4.0] — 2026-08-14

### Añadido
- App compañera **Wear OS**: SOS desde el reloj, ubicación del reloj y signos vitales (FC / SpO2 si el hardware lo permite) cada 5 minutos.
- El teléfono recibe el SOS del reloj por Wear Data Layer, activa la mesh (flag WATCH) y los SMS a contactos.
- Tarjeta «Smartwatch» en el inicio; rescatistas ven vitales y marca «reloj» en la lista SOS.

## [0.3.2] — 2026-08-14

### Eliminado
- Integración / menciones de WhatsApp (avisos de emergencia solo por SMS + mesh).

## [0.3.1] — 2026-08-14

### Cambiado
- Flujo de avisos a contactos centrado en SMS.

### Corregido
- (histórico) Detección de apps externas en Android 11+.

## [0.3.0] — 2026-08-14

### Añadido
- GPS en vivo durante SOS: updates periódicos y `LOCATION_UPDATE` en la mesh (~30 s / al moverse).
- Buscador para rescatistas: distancia en vivo + mapa (Google Maps si hay API key; si no, OpenStreetMap).
- Onboarding ampliado: permisos claros + agregar 2–3 contactos de confianza.
- Chips de estado en el inicio: Bluetooth, Wi‑Fi Direct, Internet, contactos.
- La clave de Maps se lee de `android/local.properties` (`MAPS_API_KEY`), fuera de Git.

### Cambiado
- Los rescatistas fusionan SOS + actualizaciones de ubicación por dispositivo de origen.

## [0.2.0] — 2026-08-14

### Añadido
- Red de contactos (máx. 5) desde la agenda con permisos de contactos y SMS.
- Al activar SOS: SMS automático con ubicación cada 5 minutos.
- Wi‑Fi Direct junto a BLE (origen SOS y retransmisores) cuando el teléfono lo soporta.
- Tarjeta «Red de contactos» en la pantalla principal.

### Cambiado
- Confirmación de SOS menciona SMS y Wi‑Fi Direct.

## [0.1.0] — 2026-08-14

### Añadido
- App Android MVP: mesh BLE, SOS, repetidor, modo rescatista.
- Onboarding de 3 pasos y notificación persistente de red / SOS.
- Franja roja SOS con «Estoy aquí» / «Estoy bien» sin bloquear la navegación.
- Guías de temblor, inundación y RCP con submenú por problema e imágenes.
- Pedido de Bluetooth al abrir la app; activación automática al confirmar SOS.
- Notificaciones «Activando SOS» y «Ayudando a compartir mensaje» con icono de Red de Ayuda.
- Acciones de notificación: «Estoy bien» y «Cerrar aplicación».
- Pie de página en la app: Hecho por Cristhian Ceballos → Ceballosleon.com.
- README con explicación e imágenes de funcionamiento.

### Corregido
- Botón Atrás en guías y primeros auxilios (barra fija + insets).
- «Estoy bien» ahora sí cancela el estado SOS y oculta la franja roja.
