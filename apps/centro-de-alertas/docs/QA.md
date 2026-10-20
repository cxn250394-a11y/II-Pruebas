# QA y criterios de aceptación · v0.1

## Local ejecutado

- [x] Motor Kotlin compila y ejecuta pruebas CLI en JVM.
- [x] Normalización de acentos, términos, coincidencias, falsos positivos de `gol`/`golpe`.
- [x] Fingerprint estable y separado por paquete, perfil Android y clave de notificación.
- [x] Heurística de ocultamiento de códigos y validación de ID de paquete.
- [x] Guard de permisos Android: sin Internet, contactos, SMS, accesibilidad ni respaldos.
- [x] Revisión XML del manifiesto y reglas de extracción (documentar tras validación script).

## Pendiente / no atribuir PASS

- [ ] `gradle :app:testDebugUnitTest` (falta Gradle/SDK en el entorno de esta entrega).
- [ ] `gradle :app:lintDebug` (falta Gradle/SDK).
- [ ] `gradle :app:assembleDebug` (falta Gradle/SDK).
- [ ] Instalación y permiso `NotificationListenerService` en POCO F7 HyperOS 3.
- [ ] Historial real Telegram oficial y dual / Nicegram / Sofascore / Novibet.
- [ ] Reconciliación de avisos de canales y agrupación real de Android.
- [ ] Pruebas bajo ahorro de batería, suspensión de 30-60 minutos, Wi-Fi/datos móviles.
- [ ] Recuperación del servicio ante reinicio/actualización de app y estrés >2000 registros.
- [ ] Revisión de seguridad sobre diferentes tipos de mensajes privados y OTP.

## Pruebas manuales al instalar

1. Sin permiso de Android, la bandeja permanece vacía y muestra banner.
2. Con permiso pero con 0 fuentes activadas, **no** se guarda ningún aviso.
3. Activar solo Sofascore: avisos de Telegram no se guardan.
4. Activar Telegram y Nicegram: probar ambos y verificar etiqueta por paquete.
5. Mensaje repetido con misma clave/contenido en <120s: solo uno; mensajes diferentes: ambos.
6. Filtro Deportes, Telegram y Otras con búsqueda que ignore acentos.
7. Añadir ID de paquete válido manualmente, categoría Deportes, activar; inválido se rechaza.
8. Borrar historial: confirmación, tabla vacía, nuevas notificaciones vuelven a guardarse.
9. Revocar permiso Android: no deberían almacenarse nuevos avisos; historial existente permanece hasta limpieza.
10. Comprobar que la app no aparece solicitando permiso de Internet ni tiene acceso a SMS/contactos.

## No funcional deliberado

- Sin API deportiva ni extracción de funciones privadas de otras aplicaciones.
- Sin nuevas notificaciones push, sin ordenamiento del panel Android, sin lectura retroactiva.
- Sin enlace a SB / RS1000, automatización de apuestas, modificación de Telegram o Nicegram.
