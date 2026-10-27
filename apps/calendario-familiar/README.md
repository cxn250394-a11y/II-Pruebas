# Calendario Familiar v0.1 beta

APK **offline**, dos perfiles (Yo y Mamá), días enteros coloreables y tarjetas discretas para citas/recordatorios. Pensado para facilidad de lectura, especialmente en vista diaria de letra grande. No sincroniza con cuentas ni envía datos a un servidor.

## Diseño
- Vista mensual de 7 columnas: fondo de cada día usa color elegido. Las tarjetas cortas llevan color propio de su categoría. Tocar día abre el detalle grande, completo y legible. Pulsar “Pintar varios días” activa modo brochazo para colorear fechas rápidamente.
- 24 colores (pastel, alegres, neón) con contraste calculado automáticamente; las categorías tienen nombre completo y etiqueta corta, por ejemplo «ISSSTE Balbuena» → «ISSSTE».
- Dos calendarios separados en una sola app; no se crean eventos ni días coloreados artificialmente. Categorías de ejemplo editables para IMSS/ISSSTE y Oficina/Personal.
- El icono es genérico y fijo: no muestra el día. El cambio automático de fecha queda bloqueado mientras se corrige el cierre inesperado de la aplicación.
- Respaldo local cifrado con contraseña (PBKDF2-HMAC-SHA256 + AES/GCM). No contiene contraseñas, cuentas ni servicios externos. Las importaciones se validan antes de modificar datos.

## Reglas
- Sin INTERNET, CALENDAR_PROVIDER, READ_CONTACTS, SCHEDULE_EXACT_ALARM, anuncios ni telemetría.
- SQLite privado, Android backup desactivado; usa Exportar respaldo para migrar entre teléfonos o firmas de prueba.
- No es clon del código o recursos de Business Calendar; se toma la imagen únicamente como referencia de UX.
- APK debug de CI tiene clave efímera; para actualizaciones instalables sin perder datos se requiere misma firma privada estable. **Guardar respaldos antes de reemplazar o desinstalar**.

## Pruebas y entrega
Java 17, AGP 8.7.3, Gradle 8.9, API 35. Workflow separado `calendario-familiar.yml` ejecuta pruebas JVM, Lint, APK firmada debug, SHA-256 y emulador API35 con capturas. No afirma verificaciones en POCO físico. Sin merge ni deploy, todo en PR Draft.
