# Cinco productos Android — implementación alpha

Son cinco aplicaciones independientes en `II-Pruebas`, no otra versión del lector de notificaciones. No integran ni modifican Super Bet, RS1000, Centro de Mando u otros repositorios. `main` y el LAB anterior quedan intactos. Estas alphas NO completan todavía el alcance comercial pedido.

## Producto, implementación y aceptación pendiente

| Producto | Implementación alpha | Pendiente para el objetivo completo |
| --- | --- | --- |
| CanalDeck | Cliente real TDLib, autenticación explícita con identidad propia, lista de chats/canales, historial, fotos bajo demanda, búsqueda, carpetas/favoritos locales por ID, avisos opt-in por conversación y conexión visible | Inicio de sesión real por el dueño, estados de autenticación adicionales, envío de mensajes, video/llamadas, múltiples cuentas, entrega con HyperOS. No es Nicegram completo. |
| AudioDeck | Archivos mediante SAF, cola, pausa/salto/seek/shuffle/repetición, servicio Media3 en segundo plano, Radio Browser/URL HTTPS, inspección USB y prueba WAV PCM con preferencia BIT_PERFECT anunciada por Android | TIDAL/Qobuz autorizados y reproducidos con SDK/acceso aprobado; biblioteca avanzada y DSP; FLAC por ruta USB propia; prueba física bit-perfect y compatibilidad DAC. No incluye el controlador propietario de UAPP. |
| StatsDeck | OpenLigaDB sin clave ni pago, ligas/temporadas, caché con procedencia, marcadores y goles reportados, forma/H2H de la muestra local, CSV | Otras ligas/deportes, fuente autorizada para córners/tarjetas/remates y alertas fiables. Datos faltantes son N/D, no cero. |
| OddsDeck | Comparador exacto por evento/mercado/selección/línea/periodo/reglas, manual/CSV con fuente e instante, caducidad, EV con probabilidad introducida, margen proporcional y registro de resultados/cierre | Fuentes automáticas autorizadas de cuotas/props, cobertura y calibración. No tiene los servicios privados de OddsJam/Action, no compra apuestas y no está conectado a SB. |
| Sandrift Karts | Juego 3D procedural original, tres circuitos, tres configuraciones de vehículo, cinco colores/pilotos, conducción arcade guiada por recorrido, derrape/mini-turbo, tres objetos, cuatro rivales, copa, contrarreloj, botones/inclinación y contador FPS | Física libre y colisiones, manejo pulido, mejores modelos/animación/sonido, pistas/desafíos y rendimiento medido en POCO. No es una copia visual idéntica ni un AAA terminado. |

## Arquitectura y privacidad

Kotlin y vistas nativas para productividad; Media3 para audio; TDLib mediante TDL Coroutines para Telegram; libGDX para el juego. `logic/` comparte parsers/cálculos/simulación puros; `ui/` comparte controles. Compartir código no comparte cuentas ni almacenamiento.

ApplicationId: `mx.charly.canal`, `mx.charly.audio`, `mx.charly.stats`, `mx.charly.odds`, `mx.charly.kart`. No hay analítica ni backend propio. Solo Telegram, emisoras/directorio y OpenLigaDB se consultan mediante acciones explícitas. Odds y el juego no tienen permiso INTERNET.

Credenciales Telegram y clave de base TDLib cifradas con Android Keystore. No pegar secretos en chats/repositorios. No se reutilizan credenciales extraídas de otras aplicaciones. Las carpetas Telegram son locales; este alpha no envía mensajes, reenvía contenido ni marca leído automáticamente.

## Compilación

Toolchain probado por CI: JDK 17, Gradle 9.6.1, AGP 9.2.1, Kotlin 2.4.10, compileSdk 37, paquete SDK `platforms;android-37.0`, build-tools 37.0.0. Se conserva targetSdk 35 y minSdk 28. La diferencia entre el número de API y el nombre de paquete SDK es deliberada. Los flags `android.builtInKotlin=false` y `android.newDsl=false` mantienen el DSL legado durante este alpha.

```sh
cd projects
python3 check-contracts.py
gradle :logic:test :canal:testDebugUnitTest :audio:testDebugUnitTest :stats:testDebugUnitTest :odds:testDebugUnitTest
gradle :canal:lintDebug :audio:lintDebug :stats:lintDebug :odds:lintDebug :kart:lintDebug
gradle :canal:assembleDebug :audio:assembleDebug :stats:assembleDebug :odds:assembleDebug :kart:assembleDebug
```

`five-products.yml` ejecuta pruebas y Lint, compila APK debug, verifica firma, SHA-256 y commit. Un segundo job instala las cinco APK en emulador API 35, comprueba arranque y captura pantallas; entra al juego mediante un toque de prueba. Hay que consultar el resultado real: este documento no acredita por sí mismo que todos los jobs pasaron. No hay Releases automáticas ni despliegues.

## Qué prueban las comprobaciones

JUnit verifica cálculos, parsers y simulación, incluyendo casos deterministas de propiedades. Robolectric simula parte de Android en JVM. El smoke de emulador comprueba instalación/arranque y la entrada al juego. Ninguno acredita autenticación real con la cuenta del usuario, audio bit-perfect físico, latencia de notificaciones o 60 FPS en el POCO.

La prueba USB requiere una configuración BIT_PERFECT explícita para el WAV y verifica la ruta USB antes de enviar muestras. Se detiene ante cambio/desconexión observada, no opta deliberadamente por el mezclador normal y espera al vaciado de audio antes de terminar. El sistema aún controla la ruta: no se promete impedir cualquier transitorio de hardware. Bajar primero el volumen físico del DAC, pues no hay atenuación digital en esa prueba. Solo una medición externa certifica la señal. Radio y FLAC siguen por el reproductor normal.

Firma debug temporal del runner. Antes de distribución estable se debe configurar una clave privada persistente fuera del repositorio; no se promete actualización sin reinstalar entre builds.

## Fuentes y atribuciones

- TDLib / identidad propia: https://core.telegram.org/tdlib y https://core.telegram.org/api/obtaining_api_id
- TDL Coroutines 14.0.0: https://github.com/g000sha256/tdl-coroutines/tree/14.0.0
- Media3: https://developer.android.com/media/media3/session/background-playback
- Mezclador / bit-perfect: https://source.android.com/docs/core/audio/preferred-mixer-attr
- TIDAL: https://developer.tidal.com/documentation/api-sdk/api-sdk-overview
- Qobuz: https://static.qobuz.com/apps/api/QobuzAPI-TermsofUse.pdf
- Radio Browser: https://api.radio-browser.info/
- OpenLigaDB: https://www.openligadb.de/ y https://api.openligadb.de/swagger/v1/swagger.json
- libGDX: https://libgdx.com/wiki/start/project-generation

TDL Coroutines, Media3, libGDX, Kotlin y coroutines usan Apache-2.0; TDLib usa Boost Software License 1.0. Las APK incluyen textos de licencia y NOTICE en assets. OpenLigaDB es comunitario y sus datos usan ODbL; CSV del usuario y emisoras mantienen sus derechos. Los nombres de productos comerciales son referencias funcionales, no la identidad de estas apps.
