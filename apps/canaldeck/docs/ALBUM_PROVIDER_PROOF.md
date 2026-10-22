# Álbumes vivos y ACK TDLib — prueba del contrato del proveedor

**Carril aislado de autor, 22-oct-2026. No ratificación independiente ni validación con cuenta real.**

Este examen corresponde al encargo `TORRE-RECONCILIACION-P1-Y-G4V2-20261022.md` de Info `0cfb5e4`. No modifica los HEAD congelados de Android PR #8 `3b32cb78c525ba4a6594e8718c045b08113fbd7d` ni de Info PR #33 `e4a99e311735e5381be44bec688f500334fc5602`. No se ejecutó ningún cliente, sesión, envío, servidor o paso físico.

## Resultado para la arquitectura

**Con el contrato público del proveedor fijado, este puente no puede emitir una prueba autoritativa de completitud de un álbum vivo recibido. Debe conservar HOLD.** Es una conclusión acotada a esta integración y versión: no afirma que Telegram jamás disponga internamente de esa información.

El ACK asíncrono sí tiene eventos documentados suficientes para desarrollar una máquina de correlación offline. Que esa máquina pase pruebas Fake no acredita entrega real ni autoriza conectar el transporte.

## Identidad de las fuentes examinadas

- Dependencia preservada: `dev.g000sha256:tdl-coroutines-android:14.0.0`.
- [Tag del wrapper](https://github.com/g000sha256/tdl-coroutines/tree/14.0.0): objeto anotado `675fa3b5cebfc7eaf55d0a2172b2279b73431005`, resuelto al commit inmutable **`e4408ddf785ddd74b7605af9569563133c2dc78c`**.
- El gitlink `td` de ese commit fija **`a9966eb3704a3351568c28013fed67d797c17828`**, comprobado también en el repositorio primario [tdlib/td](https://github.com/tdlib/td/commit/a9966eb3704a3351568c28013fed67d797c17828).
- Esquema público [td_api.tl](https://github.com/tdlib/td/blob/a9966eb3704a3351568c28013fed67d797c17828/td/generate/scheme/td_api.tl): blob Git `df3e3a051a00aa86ef9ad0433e555872eb5df2dd`, SHA-256 `b8b6a863746fc580815c6d04e9e34728b654b15a12940087f06757b2097ce4c5`.
- Los archivos descargados de las URLs inmutables se verificaron calculando su blob Git y SHA-256. La evidencia reproducible de rutas, tamaños y hashes está en [ALBUM_ACK_PROVIDER_PINS.json](ALBUM_ACK_PROVIDER_PINS.json). Las fuentes completas quedan fuera del candidato; el documento no constituye atestación del binario nativo de un teléfono.

## Por qué la observación de miembros no prueba el total

| Evidencia primaria | Contrato y consecuencia |
|---|---|
| `td_api.tl`, definición `Message`, líneas3131–3138; DTO `Message.kt` del wrapper | `mediaAlbumId` identifica el grupo; no aporta conjunto de miembros, cardinalidad ni marcador de cierre. |
| `td_api.tl`, `UpdateNewMessage`, líneas10296–10297; DTO `UpdateNewMessage.kt` | La actualización contiene un mensaje. No expone una transacción pública de álbum ni un evento final por álbum. |
| `MessagesManager.cpp`, `send_update_new_message`, líneas25572–25579 | La implementación genera el evento público a partir de un mensaje individual; no añade un cierre de álbum a ese evento. |
| `td_api.tl`, `getChatHistory`, líneas11680–11689 | El límite es un máximo y TDLib elige cuántos mensajes devolver; puede devolver menos. Una página corta no acredita haber agotado miembros del álbum. |
| `MessagesManager.cpp`, `get_dialog_history`, líneas17538–17602 | La implementación obtiene historia de la estructura local y puede precargar/cargar más. El resultado no incorpora un total de álbum; usa `-1` para el total de historia en ese camino. No publica un snapshot atómico del álbum. |
| `td_api.tl`, definición `Messages`, líneas3141–3142 | El total de resultados es aproximado y no está definido como cardinalidad de un álbum fuente. |
| Árbol generado completo del wrapper, no truncado | No existe función pública `GetMessageAlbum` en esa versión. Sí hay métodos de enviar álbumes, de historias, enlaces y previsualizaciones; no se deben confundir con un enumerador autoritativo de álbum recibido. |
| `td_api.tl`, `sendMessageAlbum`, líneas12062–12069; `forwardMessages`, líneas12088–12097 | El límite de una operación de creación o el vector de IDs solicitado para reenviar describe esa operación. No acredita que los IDs observados sean todos los miembros originales. |

No se elevarán a prueba de completitud: un timeout sin novedades; diez miembros observados por coincidir con el límite de creación; IDs consecutivos; el orden de llegada; un vecino de distinto álbum; una página de historia corta; una conexión en estado Ready; notificaciones sin pendientes; una lista elegida por el caller; ni un resultado de forward con todos los IDs que el propio caller solicitó.

`getMessageLink(forAlbum=true)` acredita un enlace de álbum, no entrega un manifiesto de miembros. `getMessageThreadHistory` trata hilos, que tienen otro contrato. Ninguno sustituye la prueba ausente.

## El matiz que debe conservarse: sí existe un contador de álbum PUSH

El esquema público sí define `PushMessageContentMediaAlbum.total_count` (líneas8704–8710). Por tanto, es incorrecto decir que no existe ningún contador de álbum en TDLib.

Ese DTO pertenece a `NotificationTypeNewPushMessage`, cuya envoltura no contiene `mediaAlbumId` ni el conjunto completo de IDs fuente (líneas8724–8730). El contrato del identificador de push no proporciona aquí una prueba documentada de que corresponda al conjunto verificable de mensajes de historia. El código `NotificationType.cpp` crea esa representación a partir del argumento del payload; no la convierte en un manifiesto del stream `UpdateNewMessage`.

Además, el campo `total_count` de `UpdateNotificationGroup` cuenta notificaciones no leídas, no miembros de un álbum. `UpdateHavePendingNotifications` informa del estado de notificaciones, sin un cierre por `account/source/albumId` (líneas10564–10580).

**No se incorpora FCM, no se registra otro dispositivo, no se obtienen nuevas credenciales y no se usa la notificación Android como éxito E2E.** Un estudio futuro de push tendría que demostrar primero su correlación exacta, alcance y vigencia; el simple nombre del DTO o su número no abre el carril actual.

## Productor de prueba admisible para un candidato offline

Usar un tipo de evidencia que distinga la procedencia, en vez de aceptar un booleano del caller:

- `ProviderUnknown`: resultado del adaptador real fijado; siempre HOLD para álbum vivo.
- `SyntheticManifest`: fixture Fake con cuenta, fuente, albumId, IDs de miembros únicos y cardinalidad exacta; permite probar el algoritmo, nunca un envío real.
- Una futura prueba de proveedor real solo sería admisible tras un contrato documentado que vincule identidad, conjunto completo, cierre y vigencia. No hay productor de esa prueba en este candidato.

La prueba debe quedar vinculada a cuenta, generación de sesión/configuración, fuente, albumId y conjunto exacto de miembros. Mensaje añadido, eliminado, no recuperable, duplicado, permiso cambiado o prueba antigua implica HOLD/UNKNOWN según la fase. Un álbum que cruza el baseline de activación o reconexión tampoco permite retransmitir su subconjunto nuevo.

Pruebas mínimas: páginas truncadas antes/dentro/después del álbum; miembros retrasados; evento extra después del timeout; mismo albumId en otra fuente/cuenta; falso contador10; miembro duplicado/eliminado; edición o revocación antes de entrega; manifiesto Fake incompleto, con miembro extra o de generación anterior. Cada caso sin prueba exacta debe permanecer cerrado.

## ACK asíncrono: contrato utilizable y límites

Fuentes: el mismo `td_api.tl` (líneas3015–3016,5851–5853,10299–10316,12088–12097) y los DTO fijados del wrapper incluidos en el manifiesto.

| Dato/evento | Uso correcto |
|---|---|
| `MessageSendOptions.sendingId` / `MessageSendingStatePending.sendingId` | Token Int no persistente para ayudar a vincular la solicitud con actualizaciones de mensajes pendientes. No es idempotencia durable ni confirmación. Puede ser compartido por los miembros de una operación de álbum/forward. |
| Respuesta de envío con `sendingState` pendiente | Se conserva la correlación; no se marca CONFIRMED ni se avanza a la siguiente fase. |
| `UpdateMessageSendSucceeded` | `oldMessageId` une el temporal del **chat destino** con el mensaje final. Validar cuenta/generación, destino, fase, temporal y mensaje final; el contrato permite cambiar casi cualquier campo. No confundirlo con el ID fuente. |
| `UpdateMessageSendAcknowledged` | Solo llegada de la petición al servidor; puede repetirse. No sirve como ACK final. |
| `UpdateMessageSendFailed` / `UpdateDeleteMessages` | Error o eliminación irreversible del pendiente. El evento Failed no está garantizado si el mensaje se elimina; no interpretar su ausencia como éxito. |
| Resultado `forwardMessages` | El vector sigue el orden de los IDs fuente estrictamente crecientes y admite entradas nulas. Persistir el mapeo fuente→temporal destino por posición y exigir todos los éxitos esperados, sin inventar IDs ni un ACK global de álbum. |

El esquema expone estos IDs como `int53`/`Long`. La implementación nativa fijada sí valida IDs ordinarios y programados como positivos (`MessageId.cpp`, `is_valid` / `is_valid_scheduled`), y codifica tipos con bits. No usar la convención inventada de que un temporal TDLib deba ser negativo. La correlación debe conservar el valor opaco emitido por el proveedor y compararlo exactamente dentro de su scope, sin fabricar IDs ni deducir adyacencia. Si un modelo Fake admite valores signed no cero para robustez, no presentarlos como evidencia de IDs negativos válidos en esta versión nativa.

La normalización de éxito debe conservar también si el mensaje final sigue teniendo `sendingState` o `schedulingState`. Un mensaje que quedó programado no acredita la entrega inmediata requerida por este puente, aunque haya llegado un evento Succeeded. No avanzar a originales ni marcar entrega completa con un estado final pendiente/programado o ambiguo.

No se encontró una garantía contractual del orden entre callback y actualización. El candidato debe tolerar ACK anteriores al callback con un buffer acotado de metadatos, enlazando solo una intención ya conocida en la misma cuenta/generación/destino. Falta o ambigüedad del vínculo implica UNKNOWN, no búsqueda por texto ni atribución al próximo envío.

La intención y cada fase se registran antes de invocar el transporte. El estado incluye revisión monotónica de configuración y generación de sesión: OFF→ON con los mismos valores no debe reactivar una intención antigua. Releer reloj/TTL, permisos, destino y consentimiento antes de la siguiente fase.

Tras caída o estado UNKNOWN no se reenvía automáticamente ni se reanuda la fase de medios por un ACK tardío. Un éxito tardío puede anotarse como observación sin resucitar la entrega. Si solo se confirmó el encabezado, no se declara entrega completa del álbum. Las pruebas Fake deben incluir callback/ACK invertidos, repetidos, cruzados entre cuenta/fase/destino, forward parcial, fallo de miembro y reinicio en cada transición.

## Decisión de este carril

Se puede desarrollar y probar offline el protocolo de ACK y un contrato explícito de evidencia de álbum. **Los álbumes vivos siguen HOLD y el ACK real sigue NO_VERIFICADO.** La compilación `PICK_RELAY_REAL_ENABLED=false`, cero envíos reales y las puertas de revisión/OK independientes permanecen intactas. Este documento no ratifica los HEAD congelados, no cambia sus CI y no habilita producción.
