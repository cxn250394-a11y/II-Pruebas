# Cinco productos Android — primera implementación alpha

Estos proyectos sustituyen el rumbo del lector de notificaciones: son cinco aplicaciones independientes en `II-Pruebas`. No integran ni modifican Super Bet, RS1000, Centro de Mando u otros repositorios. `main` y el LAB anterior quedan intactos.

## Qué contiene cada producto y qué NO está terminado

| Producto | Implementación alpha | Pendiente para el objetivo completo |
| --- | --- | --- |
| CanalDeck | Cliente real TDLib, autenticación explícita con identidad propia, lista de chats/canales, historial, fotos bajo demanda, búsqueda, carpetas/favoritos locales por ID, avisos opt-in por conversación y conexión visible | Inicio de sesión real por el dueño, estados de autenticación adicionales, envío de mensajes, video/llamadas, múltiples cuentas, pruebas de entrega con HyperOS. No es Nicegram completo. |
| AudioDeck | Archivos elegidos mediante SAF, cola, pausa/salto/seek/shuffle/repetición, servicio Media3 en segundo plano, directorio Radio Browser/URL HTTPS, inspección USB y prueba WAV PCM con preferencia BIT_PERFECT anunciada por Android | TIDAL/Qobuz autorizados y reproducidos con SDK/acceso aprobado; biblioteca avanzada y DSP; FLAC por ruta USB propia; prueba física bit-perfect y compatibilidad de DAC. No incluye el controlador propietario de UAPP. |
| StatsDeck | Conector público OpenLigaDB sin clave ni pago, ligas/temporadas, caché identificada, marcadores y goles reportados, forma/H2H solo de la muestra local, importación CSV | Cobertura de otras ligas/deportes, fuente autorizada para córners/tarjetas/remates y alertas fiables. Datos faltantes son N/D, no cero. |
| OddsDeck | Comparador exacto por evento/mercado/selección/línea/periodo/reglas, registro manual/CSV con origen e instante, caducidad, EV con probabilidad introducida, margen proporcional y registro de resultados/cierre | Fuentes automáticas autorizadas de cuotas/props, cobertura y calibración. No tiene los servicios privados de OddsJam/Action, no compra apuestas y no está conectado a SB. |
| Sandrift Karts | Juego 3D procedural original, tres circuitos, tres configuraciones de vehículo, cinco colores/pilotos, conducción arcade por recorrido, derrape/mini-turbo, tres objetos, cuatro rivales, copa, contrarreloj, botones/inclinación y contador FPS | Física libre y colisiones, conducción pulida, mejores modelos/animación/sonido, más pistas/desafíos y rendimiento medido en POCO. No es una reproducción visual idéntica ni un AAA terminado. |

## Arquitectura

- Kotlin y vistas Android nativas para las cuatro apps de productividad; Media3 para audio, TDLib mediante TDL Coroutines para Telegram.
- libGDX para el juego 3D, con recursos procedurales originales.
- `logic/` comparte parsers/cálculos/simulación puros; `ui/` comparte controles nativos. Compartir código no comparte cuentas ni almacenamiento.
- applicationId separados: `mx.charly.canal`, `mx.charly.audio`, `mx.charly.stats`, `mx.charly.odds`, `mx.charly.kart`.
- Datos locales, sin analítica ni backend propio. Solo Telegram, emisoras/directorio y OpenLigaDB se consultan mediante acciones explícitas.
- Credenciales Telegram y clave de base TDLib cifradas con Android Keystore. No pegar secretos en chats/repositorios. No reutilizar credenciales extraídas de aplicaciones de terceros.
- Las carpetas Telegram son locales. No se envían mensajes, ni se reenvía contenido protegido, ni se marca leído automáticamente.

## Compilar / comprobar

JDK 17, Gradle 8.9, Android SDK 35 / build-tools 35.0.0.

```sh
cd projects
python3 check-contracts.py
gradle :logic:test :canal:testDebugUnitTest :audio:testDebugUnitTest :stats:testDebugUnitTest :odds:testDebugUnitTest
gradle :canal:assembleDebug :audio:assembleDebug :stats:assembleDebug :odds:assembleDebug :kart:assembleDebug
```

El workflow `five-products.yml` ejecuta tests y Lint, compila APK debug, verifica firma y guarda SHA-256 y commit. No hay Releases automáticas ni despliegues. Su estado debe consultarse; este README NO afirma un resultado de compilación previo.

## Pruebas y seguridad de uso

Robolectric simula parte de Android en JVM. No acredita autenticación real, reproducción real, rendimiento del juego ni recepción en segundo plano en POCO. La prueba USB solo acepta una configuración BIT_PERFECT explícita del sistema para el WAV, no cambia deliberadamente a un mezclador normal. Antes de usarla, bajar el volumen físico del DAC: esta ruta no atenúa digitalmente. La señal solo se certifica con una medición física externa.

Firma debug temporal del runner. Antes de distribución estable se debe configurar una clave privada persistente fuera del repositorio; no se promete actualización sin reinstalar entre builds aún.

## Fuentes técnicas

- Telegram TDLib: https://core.telegram.org/tdlib
- Identidad Telegram propia: https://core.telegram.org/api/obtaining_api_id
- TDL Coroutines 14.0.0: https://github.com/g000sha256/tdl-coroutines/tree/14.0.0
- Audio en segundo plano: https://developer.android.com/media/media3/session/background-playback
- Preferencia de mezclador y bit-perfect: https://source.android.com/docs/core/audio/preferred-mixer-attr
- TIDAL: https://developer.tidal.com/documentation/api-sdk/api-sdk-overview
- Qobuz: https://static.qobuz.com/apps/api/QobuzAPI-TermsofUse.pdf
- Radio Browser: https://api.radio-browser.info/
- OpenLigaDB: https://www.openligadb.de/ y https://api.openligadb.de/swagger/v1/swagger.json
- libGDX: https://libgdx.com/wiki/start/project-generation

## Atribuciones

TDL Coroutines (Apache-2.0), TDLib (Boost Software License 1.0), AndroidX Media3 (Apache-2.0), libGDX (Apache-2.0), Kotlin/coroutines (Apache-2.0), JUnit/Robolectric solo en pruebas. Se deben conservar las licencias de los componentes en cualquier redistribución estable. OpenLigaDB es una fuente comunitaria con licencia de datos ODbL; los datos importados del usuario y las emisoras conservan sus propios derechos. Los nombres Nicegram/UAPP/Beach Buggy/OddsJam/Action se usan como referencias funcionales, no como identidad de estas apps.
