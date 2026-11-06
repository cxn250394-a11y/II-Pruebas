# Calendario Familiar v0.3 Workito beta

Incremento sobre PR #26, SHA 0c8a61e69ac16ce11978fba94ab5ca247817e293. Mantiene el diseño mensual de siete columnas, fondos de día completos y tarjetas compactas de citas, dos perfiles aislados, 24 colores y uso offline. Sin cambios a proyectos vecinos, permisos o iconos dinámicos.

Navegación Mes/Semana/Día/Ajustes; contexto recordado por perfil; selector nativo de fecha/hora; borrador de cita preservado al recrear Activity; búsqueda local por título, notas y categoría; calendario mensual con nodos de accesibilidad por fecha, teclado y escala del sistema. La búsqueda usa NOCASE de SQLite (ASCII), coincidencia literal, máximo 100 resultados; no elimina acentos.

La pintura de rangos de hasta 366 días requiere revisión y confirmación. SQLite v2 guarda el lote y el último deshacer de cada perfil en una misma transacción; el historial sobrevive al cierre. Deshacer rechaza registros cuyo color cambió después. La migración v1→v2 añade una tabla y conserva datos. Una importación válida CFAM1 borra el historial de pintura; una importación fallida revierte la transacción. El formato portable no cambia.

String.isBlank se sustituye por Character.isWhitespace/codePointAt para conservar validación Unicode en Android API28–32. La contraseña de respaldo permanece solo en memoria; una recreación del selector de archivos requiere iniciar de nuevo el respaldo y muestra un aviso, sin guardar la contraseña en Bundle.

## Verificación de esta entrega

Se añadieron 31 casos @Test en cuatro clases. Antes de integrar, el parser Java con nivel de fuente 17 encontró cero errores de sintaxis en ocho archivos. Esto no comprueba tipos Android ni ejecuta pruebas. El SQL aislado de búsqueda pasó cuatro casos en SQLite de Python.

El workflow del calendario ejecutará JVM/Robolectric, Lint, assembleDebug, firma/hash y smoke en emulador API35 para este HEAD. Consultar el resultado exacto antes de afirmar PASS o entregar APK instalable. Hasta entonces: CÓDIGO_SIN_VALIDAR. Los nombres de artifact y APK son Workito-v0.3; package sigue mx.charly.calendario.terra.

Pendientes críticos: exportación v0.1→v0.3 entre dos APK por DocumentsUI, TalkBack real, foco/desplazamiento de fechas fuera del viewport, fuentes extremas, HyperOS y teléfono físico. Puede haber anuncio doble de clic del contenedor y fecha virtual; verificar con TalkBack antes de afirmar accesibilidad completa. Firma debug efímera; no prometer actualización sobre una APK con otro certificado. No desinstalar una versión con datos sin respaldo comprobado.

No merge, Release, tienda o despliegue. Revisar docs/UPGRADE-VERIFICATION.md para el ensayo entre versiones.
