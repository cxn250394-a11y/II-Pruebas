# Calendario Familiar · Terra v0.2 beta (PR independiente)

Versión Android **paralela** con applicationId mx.charly.calendario.terra para que la firma debug de este runner NO sobrescriba el calendario de la v0.1 ni pierda sus datos. Icono y nombre distinguen «Calendario Familiar Terra» de «Calendario Familiar». No hay migración silenciosa: desde la versión antigua se puede exportar un archivo cifrado (contraseña de 12+ caracteres) y restaurarlo expresamente en la nueva; el formato de respaldo v1 es el mismo. No desinstales la antigua antes de confirmar una restauración exitosa.

## Cambios concretos
- Vista de 7 días en tarjetas grandes y accesibles; permite leer nombre de categoría, título, hora y nota completos con letra escalada por perfil.
- Conserva la vista mensual con cada fondo pintado y sus tarjetitas independientes, sin convertirse en bloques enormes.
- Un «Deshacer último día pintado» disponible en modo Pintar y después de pintar manualmente en la vista diaria. Es un solo nivel de deshacer **de la sesión actual**, no un historial permanente ni una restauración de todas las fechas.
- Datos de Yo y Mamá siguen separados; la vista semanal no copia ni sincroniza eventos y no requiere Internet.
- CI debe validar tests originales + nuevos, firma, captura mensual + semanal y arranque en emulador. No confundir con aceptación física de una persona mayor.

## Criterios y límites
- Lettering grande = control nativo en vista semana, no prometer texto íntegro dentro de cada una de las siete columnas estrechas.
- Se mantienen respaldos locales cifrados; no hay sincronización entre teléfonos, alarma exacta de icono ni login.
- El uso con TalkBack y la legibilidad real a escalado 200 % requieren ensayo físico. Los colores neón deben conservar contraste en la vista detallada; medición adicional pendiente.
- Firebase, Calendar Provider y otros permisos o conectores **no están incorporados**.
- APK debug de una rama distinta NO actualiza la existente, por eso nombre/paquete distintos; firma privada estable pendiente de aprobación.
- Esto no modifica v0.1, demás apps, main, Super Bet ni RS1000. Sin merge, deploy ni Releases.

## Revisión independiente de la vista semanal
- La tarjeta semanal muestra el nombre completo de la categoría y su descripción accesible incluye hora, título, categoría y nota; el estado vacío se anuncia explícitamente. Hay pruebas Robolectric para datos y navegación pendientes de ejecutar en un entorno Android.
- En el run 40461549029 del HEAD preliminar `38efbb8113cdc8ad6cbb7914d3f13d0bb55a734f`, compilación, pruebas JVM, lint y firma terminaron correctamente, pero falló el emulador: `am start` y el PID reportaron éxito y el XML capturado correspondió a HOME. No se estableció la causa ni se validó la semana. El script ahora conserva logcat, actividades y captura ante fallo para poder investigar; no implica que el fallo esté reparado.
- TalkBack, escalado del sistema, importación cifrada entre instalaciones y operación física siguen pendientes de prueba. Las pruebas descritas aquí no equivalen a aceptación de accesibilidad real.
