# CanalDeck 0.4.1 — carril aislado de ensayo offline

Este hijo de PR #8 conserva congelado el HEAD `3b32cb7`; tampoco cambia Info PR #33
`e4a99e3`. El APK de este carril usa **`mx.charly.canal.offline`**, carece de permiso
de Internet, bloquea la construcción de una sesión y conserva el fusible de salida
real en `false`. Su inicio solo ofrece ejemplos y el ensayo ficticio. No se instala
encima del cliente principal ni migra sus datos.

Ruta para el dueño: [ensayo POCO/HyperOS](docs/POCO_HYPEROS_VALIDACION_OFFLINE.md).
Contrato nuevo: [álbumes y evidencia del proveedor](docs/ALBUM_PROVIDER_PROOF.md),
[protocolo asíncrono](docs/ASYNC_RELAY_PROTOCOL.md). **Álbum vivo: HOLD;
ACK real: no verificado.** Ningún timeout ni contador de diez elementos acredita
un álbum completo. Los párrafos siguientes describen las capacidades heredadas
del cliente base, que no están habilitadas como sesión en este APK de ensayo.

Cliente Android nativo con TDLib. No es Nicegram, no está afiliado a Telegram y no es el lector Centro de Alertas. Una cuenta real con identidad propia de aplicación. Código recuperado de `projects/canal` (PR #3) y aislado aquí; ningún otro producto se modifica.

## Puente Pick$ v0.4

Candidato offline con fusible compilado OFF y configuración de salida OFF. Incluye selección por cuenta/chat real, Fano y cuatro familias UnicoRey, planificador tipado, SQLite y Fake TDLib. No envía automáticamente a Pick$ en esta versión. [Contrato, cobertura y límites](docs/PICKS_BRIDGE_V04.md), [revisión del proveedor](docs/PROVIDER_REVIEW_V04.md) y [propuesta de activación para Torre](docs/ACTIVACION_PROPUESTA_V04.md).

## Funciones implementadas

- Acceso con teléfono, código, verificación en dos pasos y los estados de correo soportados por TDLib. Requiere `api_id`/`api_hash` propios; se introducen en el teléfono, nunca en GitHub o el chat.
- Chats y canales con búsqueda, filtros por tipo/no leídos/favoritos, orden por nombre/no leídos/recientes y carga progresiva hasta 1,000 conversaciones.
- Carpetas locales con identidad estable, cambio de nombre y eliminación sin borrar mensajes. Favoritos y ajustes separados por ID real de cuenta y chat, no por título.
- Historial paginado, búsqueda de mensajes, texto completo, respuestas y envío de texto en chats privados/grupos no foros. Estado pendiente/fallido/enviado: se conserva el resultado de Telegram, sin reintentos automáticos ni pagos.
- Mensajes realmente visibles se comunican a Telegram como vistos. No ofrece modo fantasma ni altera la semántica de lectura.
- Fotos y reproducción básica de audio/video dentro de la app, descarga explícita y acotada. No se exporta, reenvía o copia contenido protegido. Contenido autodestructivo no soportado.
- Avisos propios por conversación: apagados, todas las publicaciones o palabras clave. Horario silencioso local, vista previa oculta de fábrica y controles de sonido de Android por conversación.
- Inicio guiado y demo explícito en memoria sin cuenta. Solo las pantallas sin sesión y el demo permiten capturas; las pantallas de sesión están protegidas con FLAG_SECURE.

## Privacidad y límites

TDLib guarda su base local con una clave aleatoria de 32 bytes protegida por Android Keystore. Las credenciales de aplicación se guardan con AES-GCM/Keystore. Los archivos multimedia descargados quedan en almacenamiento privado, no reciben cifrado adicional. No se confunde cifrado local con cifrado de extremo a extremo: los chats normales de Telegram son chats en la nube. Sin analítica, servidor propio, contactos importados ni uso de datos reales en CI. Backups desactivados.

La conexión en segundo plano es un servicio visible iniciado y detenido por el usuario. **No incluye FCM**, arranque automático, garantías de entrega instantánea ni exención mágica de HyperOS. Con la conexión detenida o cerrada por Android no se prometen avisos. Puede consumir batería. Todos los canales empiezan con avisos apagados; Telegram/Nicegram conservan sus propios avisos independientes.

Una cuenta activa, no varias simultáneas. No llamadas, chats secretos, registro nuevo, pago para acceso, passkeys/QR, recuperación de contraseña, stickers animados, adjuntos salientes, gestión administrativa de canales, envío a foros con temas, cliente completo de anuncios patrocinados ni paridad funcional con Nicegram. No se eluden restricciones de Telegram. Conservar el cliente oficial.

No hay credenciales propias suministradas ni pruebas con la cuenta real del dueño. Compilar y probar el motor nativo **no** prueba el inicio de sesión real, las notificaciones con pantalla apagada ni la batería en POCO/HyperOS. Las pruebas de CI y su resultado deben consultarse en la ejecución exacta; este archivo no anticipa un PASS.

## Compilación y pruebas

Java 17, Gradle 9.6.1, AGP 9.2.1, Kotlin 2.4.10, compileSdk 37 y targetSdk 35; dependencias fijadas. Biblioteca `tdl-coroutines-android:14.0.0` con TDLib 1.8.66. ABI arm64-v8a y x86_64. La selección de SDK conserva la compatibilidad de dependencias de la base y no afirma apuntar al último Android.

```sh
cd apps/canaldeck
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

El workflow dedicado genera una APK debug, checksum, identidad de commit y reportes. Ejecuta pruebas de reglas/organización/UI en JVM y pruebas de Android real emulado para Keystore, organización y carga nativa TDLib sin credenciales. Incluye captura de pantalla de bienvenida; no captura sesiones privadas.

Firma debug temporal del runner: no es una clave de producción persistente. Antes de actualizaciones permanentes debe configurarse una firma privada mantenida; no se publica ningún keystore. No se promete actualización sobre una APK de otra firma. La app coexiste con Telegram/Nicegram y Centro de Alertas.

## Activación del cliente base — no disponible en este APK offline

Para esta entrega sigue únicamente la [guía de ensayo](docs/POCO_HYPEROS_VALIDACION_OFFLINE.md).
Los pasos heredados siguientes requieren otra compilación revisada y su propia
autorización; el candidato actual no inicia sesión.

1. Registrar aplicación propia en `https://my.telegram.org/apps` (API development tools), o usar una identidad propia ya creada. Título CanalDeck, short name canaldeckpersonal, Android.
2. En CanalDeck → Empezar, introducir `api_id` y `api_hash` locales.
3. Autorizar conexión, introducir teléfono y los pasos que Telegram solicite. No compartir códigos/contraseña/identidad con terceros.
4. Mantener pulsado un canal para favorito/carpeta/avisos. Ajustes permite desconectar o cerrar solo esta sesión.

## Referencias y licencias

- https://core.telegram.org/api/obtaining_api_id
- https://core.telegram.org/tdlib/getting-started
- https://github.com/g000sha256/tdl-coroutines/tree/14.0.0
- https://developer.android.com/develop/background-work/services/fgs/service-types#remote-messaging

TDLib: Boost 1.0. TDL Coroutines y Kotlin Coroutines: Apache 2.0. Textos de licencia incluidos en assets/licenses. Código y CI solo en II-Pruebas. Main, los otros PR y repositorios externos permanecen intactos. Sin merge/deploy, apuestas, compras ni mensajes reales en pruebas.
