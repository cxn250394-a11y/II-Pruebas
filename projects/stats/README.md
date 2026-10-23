# StatsDeck v0.2 · partidos seguidos, cambios observados y CSV

App Android `mx.charly.stats` en `II-Pruebas`, basada en la alfa de `feature/five-products-alpha`.
No es Sofascore, FotMob ni Flashscore oficial. No tiene anuncios propios ni suscripción de la app.

## Funciones
- OpenLigaDB público por HTTPS y liga/temporada elegidas, caché local, búsqueda, detalle y goles que el proveedor haya reportado.
- Historial por equipo y enfrentamientos de la **muestra local**. Las frecuencias no se presentan como probabilidades.
- **Seguir equipo o partido:** preferencias locales, identificadas por `liga:temporada`; equipos por nombre observado, partidos por ID exacto de fuente. Solo seguidos en pantalla principal.
- **Cambios de marcador observados al pulsar Actualizar:** compara el mismo match ID + ambos nombres contra la descarga anterior de la misma liga/temporada y fuente. Cambios de resultado, primera disponibilidad, retirada/corrección de marcador y estado final. Historial local limitado a 300 registros/30 días, sin modificar la fuente ni producir apuestas. Primera descarga sin falsa notificación.
- **Exportar CSV** de la muestra observada; comillas correctas, score faltante queda vacío, fecha UTC cuando existe; no inventar estadísticas.
- **Importar CSV** de resultados (heredado) con confirmación y validación. Al cambiar de fuente o temporada NO compara muestras diferentes.
- **Datos incompletos:** córners, tarjetas, remates, xG y alineaciones no se ofrecen ni se rellenan con 0; mostrar N/D. No se adivinan eventos de gol a partir de que suba el marcador.

## Importante
- **No es un feed de notificaciones en vivo**. El usuario pulsa Actualizar; nada garantiza actualización inmediata, latencia del proveedor o push con app cerrada. Android/HyperOS no mantiene servicio de vigilancia continua y no se solicita `POST_NOTIFICATIONS`.
- No hay APIs de pago, scraping privado, login, apuestas ni comunicación con SB, OddsDeck, Pick$ o RS1000.
- Los CSV exportados son observaciones no verificadas; el formato añade columna `finished` y los marcadores incompletos no son reimportables por el importador de resultados finales heredado.
- `WatchPrefs` y `WatchHistory` solo viven en el dispositivo; **actualizaciones automáticas requieren misma identidad Android y firma privada estable**, no prometida para APK debug efímeras. No desinstalar la versión anterior si hay datos importantes.
- El repositorio público solo contiene datos sintéticos y código; nada de historiales, credenciales o capturas personales.

## Pruebas
JUnit del núcleo compara observaciones de fuente, identidad estricta, cambios/correcciones, falta de datos, importación CSV y retención. Robolectric comprueba persistencia de seguidos por scope y que un historial dañado no se sobrescriba. Emulador API35 solo instala y abre StatsDeck, no verifica datos de competiciones en tiempo real.
`main` y otros repositorios/proyectos permanecen sin modificaciones. Draft PR sin merge.

Fuente: https://www.openligadb.de/ y https://api.openligadb.de/swagger/v1/swagger.json
