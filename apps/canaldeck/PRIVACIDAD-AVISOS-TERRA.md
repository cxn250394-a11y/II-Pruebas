# Avisos · revisión de privacidad

Base: PR #15, `72db3f988523d21174fc8e9afd7856ad38a70d15`. Trabajo aislado, datos sintéticos; no usa cuentas, puentes ni mensajes reales.

El servicio anterior mostraba texto si la opción de vista previa estaba activada y todavía no existía el objeto `Chat`: la expresión `chat?.hasProtectedContent == true` evaluaba falso para metadatos ausentes. Ahora solo se muestra texto con opción activada, chat conocido y actualizado, no secreto ni protegido, mensaje guardable y sin autodestrucción. Si falta alguna condición, el aviso es genérico y se eliminan líneas previas del mismo chat para que InboxStyle no conserve texto anterior.

Una actualización de TDLib `UpdateChatHasProtectedContent` borra el aviso anterior de inmediato y bloquea texto hasta obtener de nuevo el chat; una respuesta antigua no desbloquea un cambio posterior. Al refrescar metadatos se borran también avisos de chats desconocidos o que resulten protegidos/secretos. La configuración de previews desactivada sigue siendo el valor predeterminado. `canBeSaved` y `hasProtectedContent` se contrastaron con el código fuente del paquete TDLib fijado `tdl-coroutines-android:14.0.0`.

Si una consulta devuelve contenido protegido y contradice el último estado no protegido, el sistema bloquea texto y cancela el aviso aunque no haya llegado un evento de cambio; pide una nueva consulta para corroborar la protección. Una respuesta contradictoria que diga que no está protegido no reabre texto: se requiere un evento posterior de TDLib y una consulta coincidente. Las consultas anteriores ya iniciadas no pueden desbloquear la barrera ni sobrescribir la respuesta posterior. Si falla la consulta, permanece el bloqueo.

Los nombres de canal nuevos son genéricos y el título del aviso también lo es cuando se oculta el texto. Al reiniciar el servicio se intentan cancelar avisos de chats que Android pudo conservar tras la muerte del proceso. Esto no borra automáticamente nombres de canales de notificación ya creados en versiones anteriores y su comportamiento en dispositivos aún debe probarse; un fabricante podría impedir listar avisos activos.

**P1 de interfaz:** identificado en el carril de avisos y abordado por el incremento descrito en `PRIVACIDAD-INTERFAZ-TERRA.md`. Deben verificarse por separado el commit correspondiente, las ventanas, OCR y el ciclo de vida; este documento de avisos no acredita esas pruebas.

Se ejecutaron pruebas JVM aisladas de la política y de la barrera de respuestas desfasadas, y `scripts/check-source.py`; **no** demuestran la ejecución de TDLib ni la entrega de notificaciones reales. Pendiente compilación Android, Lint, APK, emulador, verificación independiente y prueba de comportamiento en dispositivo autorizado. La latencia de entrega sigue dependiendo de Android/HyperOS y red. No hay reenvíos ni acceso a sesiones reales.
