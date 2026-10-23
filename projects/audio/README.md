# AudioDeck v0.2 beta — Reproductor local, radio y prueba USB

Aplicación Android independiente de UAPP y de plataformas de streaming. Incluye funciones gratuitas que recuerdan a reproductores de pago, sin desbloquear contenido, licencias o suscripciones.

## Implementado
- Reproductor Media3 local y en segundo plano (archivos seleccionados por Storage Access Framework con permiso persistente, controles de cola y repetición/aleatorio), heredado de v0.1.
- Radio Browser: búsqueda de emisoras HTTPS, visualización de códec y bitrate **reportados** por el directorio, no medidos; URL manual HTTPS.
- **Favoritos de radio:** guardar y eliminar hasta 300 estaciones, persistencia en JSON privado con escrituras atómicas, sin cuentas ni publicidad propia. Repetidos por URL se ignoran; archivo ilegible no se sobrescribe.
- **Temporizador:** 15/30/45/60/90/120 min, cancelación, pausa de Media3 desde su servicio. El temporizador está activo solo mientras el proceso del servicio esté vivo; en dispositivos con políticas agresivas puede cancelarse si Android mata el proceso. No hay AlarmManager ni reinicio oculto.
- USB: prueba explícita de WAV PCM con bandera BIT_PERFECT anunciada por Android 14+ y DAC USB compatible, con verificación de ruta y cancelación ante cambio/desconexión. No se afirma equivalencia funcional o fidelidad con el motor USB de UAPP; FLAC/Radio siguen por ruta normal.

## No implementado
- Reproducción del catálogo TIDAL y Qobuz en AudioDeck: requiere autorización de los proveedores, sus SDK y derechos/suscripción aplicables; no se copian secretos ni se sortean DRM/pagos. Spotify en la app también está fuera de alcance sin autorización; no se mezcla contenido de otros servicios.
- Certificación bit-perfect física, decodificador USB propietario, DSD nativo, gapless medido, ecualizador profesional, letras sincronizadas, importación Shazam, casting multiroom y bibliotecas gigantes auditadas.
- Captura/grabación de radio, descarga de contenido de plataformas comerciales o redistribución de emisiones.

## Acceso y privacidad
- Solo audio elegido por el usuario, directorio de emisoras Radio Browser y emisoras HTTPS elegidas por él.
- Ninguna credencial de streaming incluida; sin autenticaciones en CI, sin red de anuncios propia.
- Los favoritos locales persisten entre ejecuciones y **actualizaciones firmadas con la misma clave + applicationId**. Las APK debug de GitHub son firmas efímeras: antes de una línea estable hay que generar una firma privada persistente y guardar la clave fuera de repositorios públicos. No se garantiza actualización sobre una firma distinta.
- Trabaja solo en II-Pruebas, PR Draft hijo de la alfa. Sin tocar CanalDeck, StatsDeck, OddsDeck, Sandrift, SB, RS1000, ni otros repos.

## Calidad y pruebas
- JUnit/Robolectric: reglas de streams, duplicados, guardado/recuperación, corrupción del archivo y temporizador.
- GitHub CI: Gradle + Lint + APK debug + validación de firma/apk + SHA-256 y commit de origen.
- Emulador Android: instalar y abrir. Sin cuentas ni audio USB real.
- **La ejecución en emulador no demuestra compatibilidad ni calidad de audio en POCO F7, Poco X8 Pro Max o DAC externo.**

## Documentación
- Radio Browser: https://api.radio-browser.info/
- Android bit-perfect USB: https://source.android.com/docs/core/audio/preferred-mixer-attr
- TIDAL dev policy: https://developer.tidal.com/documentation/guidelines/guidelines-developer-terms
- Qobuz Connect partners: https://www.qobuz.com/us-en/connect
