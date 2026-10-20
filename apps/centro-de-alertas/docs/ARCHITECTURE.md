# Diseño técnico · Centro de Alertas v0.1

## Objetivo

Bandeja auxiliar de avisos para Android; NO cliente de Telegram, NO aggregator de APIs deportivas. Datos fuente: **NotificationListenerService**, que solo observa eventos que Android publica y permite tras consentimiento explícito.

```text
Telegram / Nicegram / Sofascore / Flashscore / FotMob / Novibet
                  │  (Android NotificationManager)
                  ▼
        NotificationListenerService
                  │ filtro allowlist (apagado por defecto)
                  ▼
        EXTRA_TITLE, EXTRA_TEXT/BIG_TEXT
                  │ excluye resúmenes de grupo
                  ▼
      Sanitización / límites de texto / OTP heurístico
                  │ dedupe SHA-256 con paquete+usuario+clave+contenido
                  ▼
                SQLite privado
                  │ max 2000 entradas, 14 días
                  ▼
       Broadcast interno (sin texto del mensaje)
                  ▼
 MainActivity: Todas / Telegram / Deportes / Otras
        búsqueda, hora, bandera de palabras
```

## Trust boundary

- El servicio recibe metadatos de **todas** las apps publicadas a nivel de acceso del sistema, pero descarta las que el usuario no autorizó en la allowlist *antes de extraer el título/texto*.
- Incluso un servicio bien intencionado tiene acceso técnicamente amplio: esto se comunica en el onboarding. El usuario puede revocar el permiso en Android.
- La app no tiene `INTERNET`; no hace llamadas a otros procesos con datos de usuarios ni registra contenido de notificaciones en `Logcat`.
- Backups desactivados. Base solo en `Context.getDatabasePath()`.
- Los registros pueden contener mensajes privados. Recomendado: mantener bloqueo de pantalla y permitir solamente canales deportivos si no se desea registrar chats privados.

## Datos

| Campo | Origen | Descripción |
|---|---|---|
| package_id | Android | Paquete de fuente |
| android_user_id | Android | Usuario/perfil donde se publicó, si Android lo entrega |
| notif_key | Android | ID de notificación |
| title / body | Extras | Texto recortado, redactado si se detectan patrones típicos de OTP |
| posted_at | Android | Hora que Android asigna a la notificación (NO hora real del servidor) |
| received_at | App | Hora de recepción del lector |
| fingerprint | SHA-256 | Deduplicación de actualizaciones idénticas |
| priority | Keywords | Indicación de coincidencia textual, NO verificación de evento |

### Límites de seguridad

- Dedupe basado en clave de notificación **no garantiza** colapsar alertas entre Telegram y Nicegram (son emisores distintos), a propósito para evitar perder avisos.
- Las reglas de OTP son heurísticas: texto sensible sin marcador puede almacenarse. Las fuentes están apagadas por defecto.
- Si una app no emitió un aviso, no hay evento que capturar.
- Si el fabricante mata al lector, los avisos producidos durante ese intervalo pueden perderse; necesita pruebas de suspensión largas.
- Sin notificaciones flotantes nuevas ni superposición de pantalla; así se minimizan permisos.

## Compatibilidad

- compileSdk / targetSdk = 35, minSdk = 28.
- JDK 17, AGP 8.7.3, Kotlin 2.0.21, Gradle 8.9.
- Kotlin UI nativa sin bibliotecas de terceros; pruebas JVM en Gradle y CLI aislado.
- HyperOS 3 / POCO F7 es el objetivo de validación, todavía sin pruebas de campo.

## Fase v0.2 opcional

- Sincronización de opciones con perfiles de trabajo donde Android lo permita.
- Normalización por tipo de evento con estructura `raw_text / parser_version / confidence`, sin declarar eventos verificados.
- Pantallas agrupadas por hilo, indicadores de retraso entre publicación Android/recepción del lector, sin confundirlos con retraso Telegram→Android.
