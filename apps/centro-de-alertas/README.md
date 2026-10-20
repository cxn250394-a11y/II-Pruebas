# Centro de Alertas Android v0.1.0 — MVP aislado

**Estado:** implementación fuente inicial. Motor de reglas probado localmente. **APK aún no compilada ni instalada en un teléfono** (ver `evidence/BUILD_STATUS.md`).

Aplicación privada para tu propio teléfono Android (POCO F7 / HyperOS 3). Reorganiza **notificaciones ya publicadas por el sistema** en una bandeja local. No altera otras apps, no inicia sesión en Telegram, no consulta servidores deportivos y no promete corregir retrasos previos a la publicación del aviso.

## Funciones implementadas

- Bandeja oscura, mensajes por hora de recepción (nuevos primero), búsqueda sin acentos y filtros Todas / Telegram / Deportes / Otras.
- Fuente de notificaciones oficial Android `NotificationListenerService`; permiso manual obligatorio.
- Selector por aplicaciones (Telegram, Nicegram, Sofascore, Flashscore MX, FotMob y Novibet MX) y paquetes adicionales configurables.
- Palabras prioritarias personalizables (ej.: `gol`, `córner`, `tarjeta roja`); **un marcador no verifica el evento deportivo**.
- Historial privado SQLite, límite 2,000 registros / 14 días; eliminación manual y depuración automática.
- Evita actualizaciones idénticas de una misma notificación durante 120 segundos; respeta paquete, perfil Android y clave de notificación. No fusiona automáticamente las cuentas de Telegram y Nicegram.
- Protección **heurística** de posibles códigos de verificación; no es un detector infalible de datos sensibles.
- No solicita INTERNET, ubicación, contactos, SMS, accesibilidad, cuentas ni almacenamiento externo. Desactiva respaldo y migración de la base.

## Arquitectura

`AlertListenerService` → selección de fuentes → extracción de título/texto → detección básica de códigos → inserción/dedupe SQLite (`AlertDatabase`) → aviso interno para refrescar `MainActivity`.

`AlertRules` implementa reglas puras y está cubierto por pruebas CLI y pruebas JUnit. `AlertSettings` contiene las preferencias persistentes de fuentes y palabras clave. Más información en `docs/ARCHITECTURE.md`.

## Pruebas ejecutables sin SDK

Con Java 17+ y `kotlinc`:

```bash
bash scripts/test-core.sh
bash scripts/check-permissions.sh
```

También se incluyen pruebas JUnit de proyecto y trabajo de GitHub Actions para ejecutar desde la carpeta `apps/centro-de-alertas/`:

```bash
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Cómo compilar APK (Android Studio)

1. Abre la carpeta raíz en Android Studio (no `app/` solamente).
2. Configura JDK 17 y Gradle 8.9; el plugin Android se fija en 8.7.3 y Kotlin en 2.0.21.
3. Instala Android SDK Platform 35 + Build Tools 35.0.0 cuando el IDE lo solicite.
4. Espera sincronización de Gradle.
5. Menú **Build → Build Bundle(s) / APK(s) → Build APK(s)** (o usa `gradle :app:assembleDebug`).
6. Instalable: `app/build/outputs/apk/debug/app-debug.apk`.

**Sin Android Studio:** sube el contenido completo de **II-Pruebas** a ese repositorio; conviértelo a privado antes de publicar una Release. El flujo raíz `.github/workflows/centro-alertas-apk.yml` valida, compila la APK **debug** y publica una **pre-release privada** (solo cuando el repositorio sea privado) cuyo asset es `Centro-de-Alertas-v0.1.apk` (descarga directa, sin abrir ZIP). No realiza deploy, no publica releases ni instala nada en tu teléfono.

> Nota: no incluye un `gradlew` falso ni una `gradle-wrapper.jar` descargada de una fuente desconocida. El CI instala Gradle 8.9 mediante la acción de Gradle. En un entorno local, utiliza Gradle 8.9 o genera el wrapper con el Gradle instalado (`gradle wrapper --gradle-version 8.9`).

## Primer uso en el POCO F7

1. Instala la APK debug de origen conocido y abre **Centro de Alertas**.
2. Toca **Ajustes** y activa *solamente* las fuentes que quieres registrar (vienen todas apagadas).
3. Pulsa **Conceder acceso de Android** y activa el lector de notificaciones de Centro de Alertas.
4. Algunas versiones de Android restringen este permiso para APK instaladas manualmente; si aparece el aviso de "ajustes restringidos", revisa la información de la aplicación y habilítalo **solo si confías en esta APK compilada por ti**, tras comprobar su origen. No concedas este permiso a una APK desconocida.
5. Pide un mensaje nuevo en Telegram: aparecerá en la bandeja después de que el sistema lo publique (el historial previo **no** se importa).
6. Prueba un partido que Sofascore o Flashscore ya estén notificando. Debe aparecer como **aviso recibido**, no necesariamente como evento validado.

## Límites intencionales

- No lee conversaciones completas, mensajes de Telegram no notificados, eventos internos de Novibet ni estadísticas de corners no notificadas.
- No reordena el panel de notificaciones de HyperOS, ni arregla retrasos que ocurran antes de que Android publique un aviso.
- No añade notificaciones emergentes propias todavía (v0.1 es bandeja); no requiere permiso `POST_NOTIFICATIONS`.
- Android puede omitir/ocultar notificaciones sensibles, contenido de perfiles de trabajo u otras notificaciones. El sistema manda.
- Sin pruebas de instrumentación ni validación de instalación real en POCO F7: se necesitan Android SDK y dispositivo/emulador.
- No almacenamiento cifrado a nivel de registro (Android protege el almacenamiento privado del dispositivo); si una persona desbloquea tu teléfono y entra en la app, puede leer su historial.
- Una notificación agrupada puede contener varias líneas; esta versión registra el texto que Android efectivamente entregue, no siempre cada mensaje individual.

## Aislamiento estricto

No hay dependencia ni referencia ejecutable a `Super-Bet-3.0`, `Super-Investment-1.0`, `Info-para-SB-G-CGPT`, `Work-Work-Work` o RS1000. No implementa apuestas, operaciones financieras, Telegram API, inicio/paro de servicios ni automatizaciones externas.

## Próxima iteración (después de probar en el teléfono)

1. Validar notificaciones de Telegram normal, Telegram dual, Nicegram y Sofascore con hora real de recepción.
2. Evaluar duplicados entre distintos clientes sin perder mensajes legítimos.
3. Añadir agrupación visual por chat/canal y reglas específicas verificadas para deportes.
4. Añadir exportación local voluntaria con redacción y autorización explícita (no existe en v0.1).
