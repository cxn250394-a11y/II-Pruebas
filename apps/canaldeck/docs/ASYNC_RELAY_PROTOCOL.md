# Protocolo de ensayo asíncrono de CanalDeck — carril aislado

**Descripción del candidato de autor; no ratificación independiente ni prueba de entrega Telegram real.**

Este documento describe los archivos nuevos `AsyncRelayModels.kt`, `AsyncRelayEngine.kt`, `AsyncRelayValidation.kt`, `AsyncRelayDatabase.kt`, `FakeAsyncRelay.kt` y `TdlAsyncAckMapper.kt`. Parte de los HEAD congelados de PR #8 / Info #33, sin modificarlos. La puerta compilada permanece `PICK_RELAY_REAL_ENABLED=false`.

## Alcance implementado

`AsyncRelayEngine` es un ensayo con `AsyncRelayFakeTransport`. Tanto el transporte como el batch deben ser sintéticos. No existe una implementación TDLib real de esa interfaz, ni conexión del motor a `TelegramSession.allUpdates`. La pantalla `AsyncOfflineDemo` usa identidades ficticias, Fake y una base de ensayo separada; no acepta una cuenta ni chats vivos.

El motor demuestra cómo persistir una intención, vincular respuestas pendientes y consumir confirmaciones individuales sin repetir una salida. No es un worker de reintentos ni promete entrega garantizada. La entrega real y su autorización permanecen fuera de este candidato.

## Identidad e invariantes durables

- `RelayKey(accountId, sourceChatId, groupId)` identifica la publicación observada. `groupId` representa un mensaje o álbum, pero no demuestra completitud.
- `AsyncRelayScope(accountId, sessionId, destinationChatId)` identifica la generación local de sesión y el destino. `sessionId` es una etiqueta local acotada, no una sesión TDLib exportada ni una credencial.
- La intención guarda la lista exacta de IDs fuente, `mediaRequired`, fecha de creación, timeout por fase y revisión monotónica de configuración. No guarda encabezado, caption, cuerpo, enlace, fotografía o credencial.
- Cada petición guarda UUID, fase, lista fuente esperada para medios, deadline fijo, temporales destino y mapa temporal→final. La petición de encabezado espera exactamente un resultado; la de medios espera todos los IDs originales.
- Los snapshots se copian defensivamente. El contexto con batch/contenido y prueba Fake solo vive en memoria; la base no lo reconstruye tras una caída.

La reserva de publicación y de cada miembro se mantiene por cuenta/fuente incluso si posteriormente cambia destino o sesión. Un miembro de álbum no puede reaparecer como mensaje individual u otro álbum para saltarse el checkpoint. La misma ID en otra fuente/cuenta conserva otra identidad: no se hace deduplicación por texto o fotografía.

## Fases, entradas y salidas

| Estado / entrada | Persistencia y condición | Resultado |
|---|---|---|
| Sin intención + `start` válido | Se reconstruye la evidencia de álbum, se releen estado/configuración/permisos y se comprueba plan, lista exacta, revisión, clock y TTL. `ledger.create` reserva intención, miembros y petición HEADER **antes** de `sendHeader`. | `HEADER_WAITING`; una sola petición Fake de encabezado. Si no hay autorización/prueba válida, no se emite petición. |
| `HEADER_WAITING` + respuesta pendiente válida | Se vincula el requestId exacto a un temporal destino, después de comprobar scope, cardinalidad, destino y estado pendiente. | Sigue esperando ACK final; no se emiten originales. |
| Cualquier fase de espera + quick ACK | Se reconoce como evento no final. | No completa la fase ni autoriza otro envío. |
| `HEADER_WAITING` + éxito final exacto | La confirmación cubre el temporal HEADER vinculado. | Para referencia sin medios: `COMPLETE`. Para medios: revalidación completa y reserva durable de la petición MEDIA antes de `forwardOriginals`. |
| `MEDIA_WAITING` + éxito de algunos miembros | Se añade temporal→final sin borrar confirmaciones anteriores; cada ID final debe ser único. | Permanece en espera hasta cubrir el conjunto exacto de miembros. |
| `MEDIA_WAITING` + todos los éxitos exactos | La petición está vinculada y cada temporal esperado tiene final válido. | `COMPLETE`. Un éxito de álbum no se deduce de un solo miembro. |
| Fallo, respuesta parcial/ambigua, colisión, timeout o STOP | Se conserva intención y evidencias; no se borra el checkpoint. | `UNKNOWN`, terminal; sin retransmisión ni continuación automática. |
| Reinicio / `recoverPending` | Las fases no terminales se convierten explícitamente a `UNKNOWN`; las terminales se conservan. | No se recrea el transporte ni se emite backlog. |
| `COMPLETE` o `UNKNOWN` + evento tardío/repetido | El motor devuelve el snapshot terminal existente. | No resucita fases ni añade nuevas salidas. |

Antes de cada invocación Fake, el motor vuelve a comprobar el estado durable: si hubo cancelación, cambio de request o venció el deadline durante el commit, no llama a la salida. Una excepción después de persistir la intención produce UNKNOWN, nunca una segunda invocación para adivinar si la primera ocurrió.

## Respuestas y ACK: vinculación exacta

Una respuesta válida debe corresponder al mismo scope y UUID, contener la cantidad esperada, no tener posiciones nulas ni temporales duplicados, y mostrar mensajes salientes pendientes del destino correcto. Para un forward, el orden conserva el mapeo por posición entre IDs fuente y temporales destino. No se compacta un vector parcial para convertirlo en un álbum exitoso.

**Una respuesta síncrona con `sendingState=null` tampoco sustituye un ACK final en este protocolo.** El mapper la clasifica fuera del contrato de respuesta pendiente y el motor cierra UNKNOWN de forma conservadora. Aunque el proveedor pudiera devolver un resultado final inmediato, este candidato no inventa un temporal o una confirmación para acomodarlo. La eventual cobertura de esa variante requiere contrato y pruebas nuevos.

`TdlAsyncAckMapper` solo normaliza DTOs del SDK fijado; no crea cliente, no abre sesión, no se suscribe a updates, no llama a una función TDLib y no verifica por sí mismo qué cuenta está conectada. El futuro llamador autorizado tendría que aportar la cuenta y generación correctas.

| DTO | Normalización |
|---|---|
| `UpdateMessageSendAcknowledged` | `ACKNOWLEDGED`; el motor no lo toma como éxito. |
| `UpdateMessageSendSucceeded` | `SUCCEEDED`, scope de cuenta/generación/chat destino, `oldMessageId` temporal y mensaje final. Se exige final saliente, positivo y sin estado de envío/programación. El mapper combina `sendingState` o `schedulingState` en el indicador de final todavía no entregado. |
| `UpdateMessageSendFailed` | `FAILED` del temporal destino conocido. |
| `UpdateDeleteMessages` | Solo eliminación permanente y no de caché se normaliza como `FAILED`; una retirada de caché no prueba un fallo de envío. |
| `UpdateNewMessage` / `sendingId` | No es ACK final; no se usa para saltar la vinculación de la respuesta. |

`oldMessageId` pertenece al **destino**, no es un ID fuente. La implementación nativa fijada usa IDs ordinarios/programados positivos; no se inventan temporales negativos ni IDs consecutivos. El ledger tolera temporales opacos no cero en pruebas de robustez, sin atribuir esa variante al proveedor real.

### ACK anterior al callback

El contrato primario no ofrece una garantía pública del orden callback/update. Por ello, una intención ya reservada puede conservar hasta **64** updates finales tempranos del mismo scope. Los repetidos idénticos no aumentan el buffer; el desbordamiento cierra UNKNOWN.

Los updates tempranos **no confirman nada antes de la respuesta**. Una vez vinculados sus temporales, solo se aplican los eventos cuyo `oldMessageId` coincide con ese conjunto exacto. No se asigna un ACK por texto, orden de llegada, número de álbum ni al siguiente mensaje que aparezca. Eventos de otra cuenta/generación/destino se ignoran. Un ID no esperado después del binding tampoco completa una entrega.

El historial de HEADER permanece al empezar MEDIA. Un ACK repetido idéntico del encabezado no emite otra petición; un conflicto de identidad se cierra. RequestId, temporales y finales tienen reservas únicas dentro de cuenta/generación/destino. Si dos intenciones reclaman la misma identidad, las afectadas que aún están esperando quedan UNKNOWN; una fila COMPLETE no se sobrescribe ni se reasigna.

## Reloj, configuración y consentimiento

La elegibilidad se recalcula con el **reloj actual** y el planificador antes de cada salida. El timeout predeterminado es 30 segundos por fase; el modelo admite valores entre 1 y 300000 ms. No es una garantía de latencia global. `expire(key)` es una llamada explícita del temporizador local: solo terminaliza lo vencido y no reenvía. Los callbacks/updates también comprueban el deadline fijo vigente.

Además del timeout de la petición, el planificador vuelve a evaluar la vigencia original del pick, permisos de cada mensaje/chat, destino, tipo de contenido, atribución y la lista de miembros. El estado fresco debe coincidir con el contexto inicial y con `configurationRevision`. `RelayPreferences` incrementa su revisión en commits de configuración, incluso OFF→ON; restaurar los mismos valores no devuelve vigencia a una intención antigua. Overflow, reloj inválido, sesión diferente, permiso revocado, edición o contexto transitorio ausente no pueden promover MEDIA.

La comprobación no amplía permisos: los valores proceden del proveedor/contexto autorizado o de un fixture Fake. Una confirmación de HEADER acredita solamente ese encabezado; nunca permite afirmar que sus originales se entregaron.

## Persistencia y recuperación

`AsyncRelayDatabase` usa SQLite con transacciones y compare-and-set versionado. Sus tablas separan:

- `async_deliveries`: intención, scope, fase, versión y metadatos de correlación.
- `async_members`: reserva duradera de cada miembro por cuenta/fuente.
- `async_ids`: identidad de peticiones, temporales y finales dentro del scope, con propietario y fase.

Las transiciones preservan intención/request/deadline y todos los finales anteriores. UNKNOWN y COMPLETE son terminales. La base y el ledger Fake aplican las mismas validaciones de forma defensiva; un conflicto de CAS no autoriza emitir otra petición.

`recoverPending(accountId, sessionId?)` es la operación explícita de recuperación: convierte espera no resuelta en UNKNOWN y no ejecuta transporte. La demo la invoca al restaurar/reabrir. No hay un arranque del nuevo motor conectado a cuentas reales. Corrupción o migración no contemplada detiene el acceso; no se borra ni resetea la base para fabricar un estado limpio.

## Álbumes: límite intencional

`AlbumCompletenessGate` elimina los booleanos/cardinalidades suministrados por el caller. Un álbum vivo conserva `ProviderUnknown` y HOLD. Solo un `FakeManifest` exacto, con batch sintético, cuenta/fuente/albumId y miembros únicos concordantes permite el ensayo completo. `LiveAlbumObservationIndex` es un índice acotado de IDs observados: no posee una función de timeout/liberación ni declara `complete=true`.

La evidencia primaria y sus matices están en [ALBUM_PROVIDER_PROOF.md](ALBUM_PROVIDER_PROOF.md) y [ALBUM_ACK_PROVIDER_PINS.json](ALBUM_ACK_PROVIDER_PINS.json). TDLib tiene un contador de álbum PUSH, pero no se convierte aquí en prueba del conjunto recibido por `UpdateNewMessage`.

## Qué se puede afirmar tras las pruebas

Las suites del candidato comprueban respuestas pendientes, quick ACK, ACK invertidos/tempranos/duplicados, cardinalidad parcial, scopes cruzados, colisiones, fallos, caída/reapertura, permisos/configuración cambiados y los doce tipsters mediante Fake. Los resultados y el HEAD exacto deben tomarse de la evidencia de build/CI del candidato; este documento no declara resultados futuros ni reutiliza CI de un HEAD anterior.

Un ensayo COMPLETE demuestra únicamente el contrato del fixture y sus transiciones. **ACK Telegram real NO_VERIFICADO; álbum vivo HOLD; `sent_real_messages=0` en Fake; puerta real OFF.** Habilitar requiere implementación/custodia del transporte real, prueba del propietario, revisión ajena y OK operativo separados. Nada de este protocolo modifica Pick$ productivo, los HEAD bajo auditoría, Ferrari, la tarea M1, servicios, apuestas, merge o deploy.

Fuentes primarias: wrapper `g000sha256/tdl-coroutines` commit `e4408ddf785ddd74b7605af9569563133c2dc78c`, gitlink TDLib oficial `a9966eb3704a3351568c28013fed67d797c17828`; rutas y hashes individuales en el manifiesto enlazado. Autoría Work2; no ratificación propia.
