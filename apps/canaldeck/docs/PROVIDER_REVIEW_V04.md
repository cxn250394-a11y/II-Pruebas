# Revisión técnica del proveedor fijado — v0.4

Autoría: Work2. Es un examen de compatibilidad y límites del proveedor; **no es la revisión independiente GATE-C**.

Dependencia existente preservada: dev.g000sha256:tdl-coroutines-android:14.0.0. Se consultó el código generado del [tag 14.0.0 del proveedor](https://github.com/g000sha256/tdl-coroutines/tree/14.0.0/tdl-coroutines/src/commonMainGenerated/kotlin/dev/g000sha256/tdl) y la documentación primaria de [TDLib](https://core.telegram.org/tdlib). Los hashes de los archivos consultados se conservan en PROVIDER_PINS.json.

| Contrato revisado | Consecuencia |
|---|---|
| function/ForwardMessages.kt: chatId, topicId, fromChatId, messageIds, options, sendCopy, removeCaption | Operación oficial con IDs originales; no copia de medios/captions |
| dto/MessageProperties.kt: canBeForwarded y protección por usuario actual/otro | Releer por cada mensaje; desconocido no autoriza medios |
| dto/Chat.kt: hasProtectedContent | Una fuente protegida no permite medios |
| dto/Supergroup.kt y estado/rights de administrador | Publicar en canal exige estado real, no un título o los permisos generales del chat |
| dto/Message.kt: date, editDate, album ID, sendingState | Fecha de post fuente; excluir edición/salida; queued ACK no es confirmación |
| getMessageLink de la versión fijada | Parámetros checklistTaskId/pollOptionId explícitos, link validado; nunca fabricar URL |
| isFromOffline | Marca una acción programada/automática del emisor, no demuestra historial; procedencia live viene de UpdateNewMessage + baseline |
| Sin GetMessageAlbum / contador total autoritativo en esta integración | Los álbumes vivos permanecen HOLD; Fake verifica el contrato completo |

El adaptador inyecta el cliente de TelegramSession y no crea otro TdlClient. No descarga medios ni hace OCR. El método oficial de envío queda dormido tras la puerta compilada OFF. Las firmas exactas también se comprueban por compilación Android en CI. Los archivos de fuentes del proveedor incluidos como artefacto de CI conservan sus licencias.

Pendientes para un nuevo PR de activación: prueba de completitud de álbum real; ACK asíncrono final con reconciliación sin repetición; verificación de permisos al punto de entrega en la cuenta del dueño; prueba POCO/HyperOS y revisión independiente.
