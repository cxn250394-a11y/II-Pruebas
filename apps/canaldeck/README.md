# CanalDeck 0.4.3 — variantes normal y laboratorio offline

[Delta de integridad y recuperación de singles](docs/INTEGRIDAD_SINGLE_P2_Y_RECUPERACION.md):
correcciones propuestas para P2-9B, P2-9C y P2-10A sobre #11 `85c616c`,
conforme a Info `8c373cd`. Los HEAD auditados permanecen congelados. La salida
real sigue compilada OFF; álbumes vivos HOLD. No se anticipa cierre de P2 ni
ratificación; ninguna acción del dueño es necesaria ahora.

Este candidato separa la app principal del laboratorio mediante flavors reales.
Parte de PR #10 `6eb560a`; permanecen congelados #8 `3b32cb7`, #9 `6a40b20`, #10
e Info #33 `e4a99e3` conforme a Info main `91993e4`.

| Flavor | Paquete | Internet y cuenta | Salida automática |
|---|---|---|---|
| `normal` | `mx.charly.canal` | Conserva la capacidad del cliente principal; no se conecta una cuenta en pruebas | Compilada OFF |
| `offline` | `mx.charly.canal.offline` | Sin Internet y con inicio de sesión bloqueado | Compilada OFF |

La matriz se aplica a debug y release. El código conserva el único cliente TDLib
y su colector; separar variantes no crea otra sesión. Los datos privados de cada
paquete no se copian, migran o borran. Mantener el paquete principal tampoco
demuestra compatibilidad con la firma o datos del APK instalado en el teléfono.

[Identidad, datos, pruebas y ruta mínima](docs/VARIANTES_IDENTIDAD_DATOS_Y_RUTA_MINIMA.md)
es la guía de este carril. **Ninguna acción del dueño es necesaria ahora**: no se
solicita instalar ni iniciar sesión. Los artefactos `normal` no son permiso para
usar una cuenta; release no tiene firma de distribución configurada.

El controlador de singles y la pantalla Fake heredados de #10 se conservan.
[Contrato de singles](docs/SINGLE_RELAY_EVIDENCIA_Y_LIMITES.md) y
[propuesta de activación y reversa](docs/SINGLE_RELAY_ACTIVACION_Y_REVERSA.md)
describen sus antecedentes; donde hablan de un único APK offline, corresponde al
carril #10 congelado. La selección normal/offline actual se rige por la guía de
variantes anterior. **ACK real no verificado; álbum vivo HOLD; foto sin texto no
acredita un pick ni un tipster.** P2-9B/P2-9C siguen pendientes; este cambio no
ratifica el protocolo de envío.

Los párrafos siguientes describen capacidades del cliente base. La variante
offline mantiene su ensayo sin cuenta; usar capacidades reales de normal exige
la versión revisada y el OK que corresponda.

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
gradle :app:testNormalDebugUnitTest :app:testOfflineDebugUnitTest
gradle :app:assembleNormalDebug :app:assembleOfflineDebug :app:assembleNormalRelease :app:assembleOfflineRelease
```

El workflow del candidato debe vincular pruebas y artefactos de cada variante al
HEAD exacto, incluidos manifiestos debug/release y aislamiento de datos en
emulador. Los resultados válidos son los del nuevo PR/CI; no se reutiliza la CI
del HEAD congelado de #10. Las pruebas no conectan una cuenta ni envían mensajes.

Firma debug temporal del runner: no es una clave de producción persistente. Antes de actualizaciones permanentes debe configurarse una firma privada mantenida; no se publica ningún keystore. No se promete actualización sobre una APK de otra firma. La app coexiste con Telegram/Nicegram y Centro de Alertas.

## Ruta posterior

La [guía de variantes](docs/VARIANTES_IDENTIDAD_DATOS_Y_RUTA_MINIMA.md) separa
laboratorio, observación con la sesión principal y una futura prueba supervisada
de referencias/fotos. No hay procedimiento físico que ejecutar ahora. Cada fase
real necesita revisión y su OK separado; no usar `.offline` para abrir un segundo
lector ni desinstalar una app para resolver una firma incompatible.

## Referencias y licencias

- https://core.telegram.org/api/obtaining_api_id
- https://core.telegram.org/tdlib/getting-started
- https://github.com/g000sha256/tdl-coroutines/tree/14.0.0
- https://developer.android.com/develop/background-work/services/fgs/service-types#remote-messaging

TDLib: Boost 1.0. TDL Coroutines y Kotlin Coroutines: Apache 2.0. Textos de licencia incluidos en assets/licenses. Código y CI solo en II-Pruebas. Main, los otros PR y repositorios externos permanecen intactos. Sin merge/deploy, apuestas, compras ni mensajes reales en pruebas.
