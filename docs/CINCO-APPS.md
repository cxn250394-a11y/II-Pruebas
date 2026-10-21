# Alcance ratificado por el usuario — cinco productos Android

Fecha: 2026-10-21. Repositorio único de laboratorio: II-Pruebas.

El usuario solicita avanzar y terminar: propio Nicegram; propio UAPP con radio/TIDAL/Qobuz/bit-perfect; mezcla de estadísticas; mezcla OddsJam/Action/etc.; kart racer muy cercano a Beach Buggy Racing. **Centro de Alertas es auxiliar, no cumple ninguna de las cinco entregas por sí solo. No se sigue ampliando como sustituto.**

## 1. Cliente de Telegram propio

Producto: iniciar sesión en la cuenta propia, chats/canales/grupos reales, multimedia, varias cuentas, carpetas y búsqueda útiles, notificaciones fiables y configuración clara. Base propuesta: auditar y adaptar un cliente abierto mantenido (Nicegram/Telegram Android) antes que reescribir MTProto o llamar cliente a un lector de avisos. Identidad visual propia y obligaciones de las licencias conservadas.

Puerta de terminación: APK compilada; credenciales propias de desarrollador configuradas de forma privada; login y cierre de sesión reales consentidos; recepción/envío manual/multimedia, carpetas, reconexión, segundo plano y cambio de cuentas probados. No se solicitan contraseñas/códigos de acceso en el chat ni se suben sesiones a GitHub. No se usan credenciales de otra app ni se afirma solucionar todas las restricciones de HyperOS.

Estado actual: pendiente implementación del cliente y auditoría del fork. La app de alertas no cuenta como cliente. api_id/api_hash propios y aprobación de sesión son dependencias de integración, no una excusa para sustituir el producto con pantallas vacías.

## 2. Reproductor propio tipo UAPP — Aural

Producto: biblioteca local cómoda (artistas/álbumes/carátulas/listas), cola, búsqueda, favoritos, reproducción continua y segundo plano; radio por Internet con emisoras guardadas; integraciones TIDAL/Qobuz autorizadas; salida USB/Hi-Res y bit-perfect comprobado en las rutas compatibles.

Primer incremento: `apps/aural`, archivos seleccionados + radio HTTPS manual + transporte/servicio multimedia + diagnóstico honesto. No se llama versión final. TIDAL y Qobuz no están implementados en este piloto.

Dependencias externas: el SDK público TIDAL documenta previews y reglas específicas de reproducción; no equivale automáticamente al acuerdo de un fabricante con streaming completo y controlador USB. Qobuz canaliza integración por partnerships. La cuenta del usuario debe tener derechos de reproducción. Sin extracción de claves, compras de suscripciones ni publicación de secretos.

Bit-perfect: distinguir archivo, decodificación, mezclador, ruta real y DAC. Android 14+ dispone de perfiles USB opcionales; que un dispositivo anuncie uno no prueba que la app lo active ni que las muestras lleguen intactas. Se requiere implementar selección compatible/rollback y validación por teléfono + DAC. Bluetooth no se presenta como bit-perfect. Necesitamos identificar el DAC concreto antes de certificar compatibilidad.

Puerta de terminación: archivos reales en todos los formatos declarados; cola/listas y persistencia; gapless comprobado; emisoras reales/reconexión; cuenta autorizada TIDAL/Qobuz con catálogo y reproducción completa; mediciones USB de las rutas anunciadas; reproducción prolongada y lifecycle en el POCO. Integraciones no autorizadas siguen pendientes, nunca simuladas como completas.

## 3. Estadísticas deportivas propias

Producto: una experiencia combinada de Sofascore/FotMob/Flashscore: partidos, seguimiento, alineaciones y estadísticas, histórico y comparaciones, goles/tarjetas/córners y alertas configurables. No se limita a recibir notificaciones de otras apps.

Modelo: cada valor con origen, instante observado, periodo, identidad y cobertura. Ausente = desconocido, nunca cero. Mostrar retraso/caducidad y diferenciar dato histórico de proyección. La cobertura real de proveedores debe censarse antes de prometer todas las ligas y todos los eventos en vivo gratis.

Incremento actual: `shared/sports-core` implementa observaciones y frecuencias históricas sin convertir ausentes en cero. No hay aún una APK de estadísticas ni feed en vivo integrado.

Puerta de terminación: adquisición autorizada real, normalización, UI útil, seguimiento y notificaciones, prueba de correcciones y desconexión. Sin compra de APIs, extracción de datos privados o acoplamiento con Super Bet. Los datos sintéticos sirven para tests, no para aparentar partidos reales.

## 4. Comparador y analítica tipo OddsJam / Action / BetGPT

Producto: tablero de cuotas y mercados comparables, cambios de línea, mejor precio, probabilidades con origen y supuestos, valor esperado condicionado a una estimación validada, candidatos matemáticos de arbitraje, seguimiento de boletos/resultados/ROI/CLV. No vende victorias garantizadas ni ejecuta apuestas.

Incremento actual: `shared/sports-core` compara solo eventos/mercados/periodos/líneas/reglas idénticos; rechaza cuotas futuras, suspendidas, contradictorias o vencidas; no revive un precio anterior después de suspensión; exige todos los resultados para comparar la suma inversa. Incluye eliminación proporcional de margen como supuesto explícito y EV con probabilidad proporcionada, no inventada.

Puerta de terminación: fuentes reales autorizadas con cobertura/latencia demostradas, aplicación usable, historial y auditoría; comisiones, límites y reglas antes de clasificar arbitraje accionable. Matemática favorable no es ganancia asegurada. No se conecta a Super Bet, no importa sus claves y no apuesta dinero.

## 5. Karts — Brisa Kart, calidad cercana a Beach Buggy

Producto: conducción arcade agradable, derrape/miniturbo, cajas de objetos y aceleradores, rivales, circuitos, pilotos/vehículos diferenciados, torneos, retos, controles táctiles e inclinación, audio y presentación pulida. La referencia es experiencia y calidad, no personajes/pistas/música/logos extraídos de Vector Unit/Nintendo.

Se recuperaron los archivos previos BrisaKart y Sundrift desde la biblioteca para auditarlos y no empezar de cero. BrisaKart es una alfa Python→JavaScript/WebGL en WebView; el informe anterior no acredita instalación Android ni 60 FPS en el POCO. No se presenta como motor Python nativo ni como clon comercial terminado. No se ha importado todavía todo su código a esta rama.

Requisito anterior de Python: se conserva. Una migración a otro motor necesita explicar qué lenguaje ejecuta el juego y acordar la decisión; no llamar Python a GDScript. Primera puerta: reconstruir por Android SDK oficial, ejecutar una carrera completa, revisar controles/física/cámara y comparar visualmente. Luego completar contenido, progreso y pulido; 60 FPS se mide en hardware, no se declara mediante una constante.

## Reglas comunes y secuencia

- Cinco aplicaciones separadas, no una app contenedora con cinco botones. Se comparten solo bibliotecas pequeñas y justificadas.
- Ninguna se declara terminada por compilar o mostrar un demo. Cada recibo separa implementado, probado, no verificado y bloqueado.
- Entregas instalables en APK; el usuario no debe compilar proyectos ni instalar herramientas de desarrollo para usarlas.
- Prioridad inmediata: sacar el reproductor usable; en paralelo consolidar la base deportiva y auditar el cliente Telegram y el kart. No activar servicios persistentes ni prometer progreso autónomo después de terminar un turno.
- Sin tocar Super Bet, Super Investment, Centro de Mando, Info, RS1000 o el ordenador del usuario. Sin compras, despliegues, apuestas, mensajes reales de prueba ni publicaciones automáticas.
- Repositorio público: solo fuente y pruebas sintéticas. Nada de conversaciones, cookies, API secrets, keystores, cuentas o datos del teléfono. Firma estable privada pendiente.
- Trabajo en ramas/PR Draft. Main, alertas v0.1 y LAB v0.2 se conservan.

## Fuentes oficiales comprobadas

- Telegram API y credenciales: https://core.telegram.org/api/obtaining_api_id ; https://core.telegram.org/api/terms
- Nicegram fuente Android: https://github.com/nicegram/nicegram-android
- UAPP: https://www.extreamsd.com/index.php/products/usb-audio-player-pro
- TIDAL: https://developer.tidal.com/documentation/api-sdk/api-sdk-overview ; https://developer.tidal.com/documentation/guidelines-developer-guidelines-2_0 ; https://developer.tidal.com/documentation/guidelines/guidelines-developer-terms
- Qobuz: https://www.qobuz.com/us-en/page/annonceurs-partenariats
- Android USB mixer: https://source.android.com/docs/core/audio/preferred-mixer-attr
- Media3: https://developer.android.com/media/media3/session/background-playback
- Ejemplo de límites de datos gratuitos (no autorización de compra): https://www.football-data.org/pricing
- Referencia jugable: https://www.vectorunit.com/beach-buggy-racing-2
