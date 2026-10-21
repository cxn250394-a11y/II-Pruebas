# Aural — reproductor propio, piloto 0.1

Primer incremento del proyecto de audio solicitado. **No sustituye todavía a UAPP** y no se presenta como cliente TIDAL/Qobuz terminado. ApplicationId independiente `com.charly.aural`; no toca Centro de Alertas ni sus datos.

## Implementado

- Selección explícita de archivos con el selector de Android; persiste referencias concedidas, no escanea almacenamiento indiscriminadamente. Máximo 500 referencias / 100 por selección.
- Reproducción real con Media3, cola, pausa, anterior/siguiente, búsqueda, favoritos, repetición, aleatorio y posición cuando la fuente permite buscar.
- Servicio multimedia para reproducción en segundo plano, foco de audio y desconexión de auriculares.
- Radio por URL directa HTTPS de audio o HLS; alta manual, favoritos, consentimiento antes de conectar. No incluye catálogo de emisoras ni reconoce páginas web como streams. Máximo 100 emisoras.
- Tono original suave de ocho segundos incluido para comprobar la cadena sin música de terceros.
- Diagnóstico de capacidades anunciadas por Android y USB, separado del formato del archivo. **Bit-perfect: NO VERIFICADO; no se activa ni se implementa una ruta USB exclusiva.**

## Pendiente antes de reemplazar UAPP

Biblioteca por artista/álbum, portada y etiquetas completas, listas editables y restauración de cola, directorio de radio, pruebas de gapless, decodificadores/cobertura de formatos, TIDAL/Qobuz autorizados y salida bit-perfect medida en hardware. No basta con mostrar 192 kHz en el archivo.

La interfaz marca TIDAL y Qobuz como pendientes; no contiene un formulario ficticio de login ni obtiene claves de otras aplicaciones. La autorización de un SDK no se interpreta como permiso para reutilizar audio descifrado con un controlador externo.

## Privacidad y firma

Sin analítica, anuncios, subida de archivos o conexión a otros proyectos. Las emisoras elegidas reciben las solicitudes necesarias y la IP. El selector del sistema puede ofrecer proveedores de archivos en la nube. Permisos: Internet, servicio multimedia en primer plano y wakelock. Sin acceso general a almacenamiento/contactos/micrófono. Respaldos y tráfico HTTP desactivados. La firma debug del runner es temporal: aún no hay identidad estable privada para actualizaciones; no se publica ninguna clave.

## Construcción reproducible

Versiones fijadas por compatibilidad (no se afirman las más recientes): Java 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Media3 1.5.1, SDK 35. Desde la raíz:

```sh
python3 tools/generate-aural-demo.py
python3 scripts/check-aural.py
cd apps/aural
gradle --no-daemon :core:test :sports:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

El módulo `sports` se incluye únicamente para probar la base estadística/cuotas. **No es dependencia del APK de audio**. Las dos futuras apps deportivas son productos separados.

CI `.github/workflows/aural.yml` entrega APK, checksum, firma y commit; pruebas JVM, Robolectric y una prueba Android instrumentada de reproducción del tono, pausa y regreso de segundo plano. Su estado real debe consultarse en la ejecución, no deducirse de este README. El emulador sin audio audible verifica progreso de reproducción, no medición analógica/DAC, fidelidad ni funcionamiento en POCO. No se afirma que un stream de radio real o todos los formatos hayan sido probados.
