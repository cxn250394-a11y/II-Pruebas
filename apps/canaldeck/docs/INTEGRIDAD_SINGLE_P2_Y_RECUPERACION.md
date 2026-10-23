# Integridad de publicaciones individuales y recuperación

Propuesta de autor conforme a Info main
`8c373cdbd670cfe08516f05f1428c325a31cfb89`. El delta parte de PR #11
`85c616c41d6390a294e6f7b3d3b7095867aacf8a`. Permanecen congelados #8, #9, #10,
#11 e Info #33. No se cambia el dictamen de esos HEAD ni se amplía el alcance de
fuentes, contenidos o tipos de envío.

Este documento describe el contrato de las correcciones y la evidencia que debe
entregar el candidato. No anticipa resultados de CI, no cierra P2 por decisión
del autor y no sustituye la auditoría de Grok Bot o el dictamen de la Torre.
**Ninguna acción del dueño es necesaria ahora**: no se solicita instalación,
cuenta, contraseña, permiso operativo ni procedimiento físico.

## P2-9B: encabezado final borrado antes de la foto

El ACK final del encabezado solo demuestra que Telegram lo confirmó en ese
momento. No demuestra que siga existiendo cuando comienza la fase de foto.
El controlador conserva el ID final exacto del encabezado y detiene el intento
cuando observa su borrado permanente en el destino y scope correspondientes.
Una mutación del encabezado también invalida esa atribución; una actualización
ajena o una expulsión temporal de caché no se toma como borrado permanente.

Antes de despachar la foto, el puerto debe releer el encabezado exacto: destino,
ID final, mensaje saliente, ausencia de estado pendiente y texto completo de
atribución esperado. El código nativo comprueba además que el mensaje pueda
recibir respuesta. Lectura ausente, fallida, ambigua o distinta termina en
`UNKNOWN`, sin foto ni reintento. El texto esperado vive en memoria; no se añade
el cuerpo, caption, enlace o imagen a SQLite.

La consulta de existencia tiene callback único. Después de una respuesta
positiva se releen fuente, permisos, consentimiento, revisión y sesión. Un
callback tardío no revive un contexto invalidado, no prolonga su deadline y no
despacha dos veces. La implementación del puerto falla cerrada si no ofrece una
comprobación explícita; los puertos Fake deben declarar su respuesta sintética.

El proveedor nativo exige además `commandActive`, cerrado por defecto. El bridge
consulta sin mutar el controlador: contexto, key, request ID, fase, scope,
deadline e identidad/texto del encabezado deben seguir correspondiendo al
comando. La revisión durable del intento debe coincidir con las preferencias
actuales, incluso si el dueño cambió OFF y después ON durante la espera. Se
revalida después de `getMessage`, después de `getMessageProperties`
y justo antes de cada `sendMessage`, también para un HEADER diferido. Un STOP,
mutación de fuente o borrado observado mientras una lectura estaba suspendida
no deja vigente aquel comando. Esta comprobación local no convierte la lectura
y la operación remota en una transacción atómica.

El resultado nativo distingue `Queued`, `NotInvoked` y `Uncertain`. Si el
encabezado desaparece o cambian permiso/contexto antes de invocar al proveedor,
`NotInvoked` retira la reserva exacta del router mediante
`cancelBeforeInvocation`: no se conserva una cuarentena eterna de una llamada
que nunca existió. Eso no reintenta el intento rechazado ni levanta una pausa
previa. Si hubo invocación y su resultado no es demostrable, se conserva
`Uncertain`, su cuarentena y el estado UNKNOWN; no se usa el rechazo previo a una
llamada como excusa para liberar una llamada que sí pudo enviarse.

La operación individual nativa se prepara con `sendMessage`,
`InputMessageForwarded(copyOptions=null)` y `replyTo` al ID final confirmado del
encabezado. Conserva la referencia al origen y su caption; no crea una copia,
no descarga una foto protegida y no modifica el contenido original. La relación
de respuesta une el original con su atribución, pero no constituye una
transacción de Telegram.

**Límite abierto para activación real:** entre la última lectura y el envío
puede ocurrir un borrado remoto. Tampoco se puede impedir un borrado después del
envío. No se afirma que esta corrección garantice cero originales huérfanos en
todos los intercalados reales. No se inventa compensación automática, borrado de
mensajes, rollback de Telegram o envío alternativo. Ese intervalo y el
comportamiento real de respuesta a un encabezado eliminado deben recibir una
decisión expresa antes del carril vivo desatendido.

La API oficial distingue
[forwardMessages](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1forward_messages.html),
sin parámetro `replyTo`, de
[inputMessageForwarded](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1input_message_forwarded.html)
y
[inputMessageReplyToMessage](https://core.telegram.org/tdlib/docs/classtd_1_1td__api_1_1input_message_reply_to_message.html).
La compilación debe comprobar también las firmas del wrapper fijado
`tdl-coroutines-android:14.0.0`; una documentación más reciente no sustituye esa
comprobación. La conclusión de que dos operaciones no ofrecen atomicidad es un
límite del protocolo preparado, no una prueba de comportamiento de una cuenta.

## P2-9C: búfer de ACK temprano y tráfico ajeno

El router conserva identidad por cuenta, destino e ID temporal durante la vida
del cliente. Antes de la respuesta `Queued`, el update no contiene la identidad
de la solicitud local. Solo esa respuesta vincula el ID temporal exacto con su
scope; el orden de llegada no asigna una generación.

El búfer temprano se acota por solicitud pendiente. Una solicitud de otro
destino no consume su presupuesto; tráfico de un destino sin solicitudes se
descarta y los duplicados exactos no consumen otro slot. Se mantienen los límites
de solicitudes y de bindings, sin expulsar identidades antiguas para hacer
espacio a nuevas.

Si se pierde evidencia por desbordamiento, la solicitud afectada queda incierta
y termina en `UNKNOWN`. Esa pérdida local no envenena por sí sola todo el router
ni destruye las solicitudes de otros destinos. Una colisión de identidad,
respuesta inválida, cambio de cuenta en el mismo cliente o agotamiento de la
tabla de identidades siguen cerrando el carril de forma global.

**Límite de disponibilidad:** tráfico ajeno en el mismo destino, antes de conocer
el ID temporal, puede ser indistinguible del ACK propio. Puede provocar
`UNKNOWN` local; no se afirma disponibilidad ilimitada ante un flood. Se prefiere
un destino dedicado para una futura prueba autorizada. Ni ampliar memoria ni
descartar evidencia permite declarar un envío confirmado.

## P2-10A: desconexión con trabajo pendiente

La pérdida de transporte pausa nuevas salidas y convierte los intentos afectados
en `UNKNOWN` durable. No se reinicia la fase HEADER o MEDIA, no se envía la foto
que faltaba, no se vuelve a reservar la misma publicación y no se reintenta una
solicitud con resultado incierto.

El router conserva todos los bindings de su vida. Una llamada pendiente cuyo ID
temporal todavía se desconoce queda en cuarentena. Una respuesta `Queued` tardía
puede sellar ese ID con el scope antiguo y retirar su cuarentena, pero no emite
un ACK que complete el intento terminal ni habilita el siguiente paso. Un
timeout o una reconexión no absuelven una cuarentena sin identidad resuelta.

La pausa exige un fence durable, ligado a cuenta, scope anterior, revisión,
nonce y vida del cliente. El regreso de red solo actualiza la observación de
transporte; no limpia el fence, no activa preferencias y no concede permiso de
envío. Un fallo de persistencia mantiene el bloqueo.

Reanudar trabajo futuro exige un consentimiento explícito separado que coincida
con el token de pausa y el nuevo scope/revisión observados. Se comprueban cuenta,
sesión conectada, configuración autorizada, ausencia de cuarentenas sin resolver
y la misma vida del cliente. La sesión nueva debe ser distinta de la anterior;
la revisión coincide exactamente con la observada y no puede preceder a la
revisión de pausa. Una nueva pausa rota el nonce y revoca consentimientos viejos.
El router debe aceptar la reanudación antes de
limpiar el fence mediante comparación y cambio atómico. Si ese cambio falla,
el gate sigue cerrado aunque el router hubiera aceptado. Ningún paso escribe
`enabled=true` de forma automática.

Solo después de aceptar la reanudación explícita se renueva
`connectionStartedUnix` al momento de ese acto y se reconstruye la admisión con
el high-water actual cargado de cada fuente. El regreso de red por sí solo no
fija el inicio de una ventana de envío. Las publicaciones acumuladas durante la
pausa no se convierten en una cola retroactiva al consentir trabajo nuevo; los
intentos anteriores conservan sus reservas y su deduplicación.

Un token de otra vida de cliente, incluido después de un reinicio de proceso,
no se limpia automáticamente. Sin evidencia que resuelva esa identidad queda
retenido para revisión; no se usa el reinicio como método para saltar una pausa.
Si al arrancar se recuperan intentos pendientes de SQLite sin un fence previo,
también debe persistirse un bloqueo por identidad anterior no demostrada: los
tombstones que solo vivían en RAM no se reconstruyen adivinando el orden de ACK.
Esto incluye **todo intento UNKNOWN**, cualquiera que sea su razón previa:
`REQUEST_TIMEOUT`, excepción de despacho, desconexión u otra incertidumbre no
demuestran que el SDK hubiera dejado de trabajar antes del reinicio. Solo los
intentos `COMPLETE` quedan fuera de esa condición de recuperación. Conservar un
UNKNOWN antiguo implica revisión al reiniciar el cliente; no se libera por reloj,
por otra llamada a recuperación ni borrando/resetando su registro.
Los checkpoints, intentos y deduplicación sobreviven. `UNKNOWN` no se convierte
en `COMPLETE` por recibir un ACK antiguo.

Este candidato prepara la mecánica de consentimiento y su validación; no añade
una pantalla para concederlo ni abre el fusible. El código nativo sigue
rechazando la salida porque `PICK_RELAY_REAL_ENABLED=false` en normal y offline,
tanto debug como release. Una reanudación lógica Fake no equivale a activar la
integración real.

## Matriz de auditoría y evidencia requerida

Las filas son criterios que deben contrastarse con pruebas ejecutadas y artefactos
del HEAD entregado; no son una tabla de PASS anticipado.

| Pendiente | Intercalado o fallo que debe probarse | Evidencia esperada |
|---|---|---|
| P2-9B | Encabezado final borrado antes de MEDIA; callback positivo llega después del borrado | Intento UNKNOWN; cero solicitudes de foto nuevas; callback no revive contexto |
| P2-9B | Encabezado ausente, editado o no verificable; permiso cambia durante lectura | Rechazo cerrado; no despacho de original |
| P2-9B | Callback de existencia/fresh repetido; ID o scope ajenos | Como máximo una solicitud; evidencia ajena no confirma ni invalida el intento propio |
| P2-9B | Recorrido Fake válido de referencia y foto individual | ACK final exacto; foto asociada al encabezado; original y caption sin copiar ni alterar |
| P2-9C | Flood de otro destino con solicitud pendiente, y de un destino sin solicitud | El presupuesto propio y la identidad exacta se conservan |
| P2-9C | Flood del mismo destino y duplicados antes de Queued | Pérdida acotada y UNKNOWN local; ningún éxito supuesto ni poison global por esa pérdida |
| P2-9C | Colisión, cuenta distinta o límite de bindings | Parada global conservadora; identidades no se reciclan |
| P2-10A | Desconexión en HEADER o MEDIA, antes y después de Queued | Intento viejo UNKNOWN; cero reintentos y cero avance por callbacks tardíos |
| P2-10A | Queued/ACK antiguos tras reconexión, y cuarentena sin respuesta | Scope antiguo retenido; bloqueo mientras la identidad siga irresuelta |
| P2-10A | Reconexión sin consentimiento; token/scope/revisión incorrectos; CAS fallido | Fence persistente cerrado; configuración no se activa implícitamente |
| P2-10A | Consentimiento explícito válido para trabajo nuevo, misma vida y sin cuarentena | Solo preparación fresca elegible; la publicación vieja nunca se repite |
| P2-10A | Recreación del gate o reinicio con fence de cliente anterior | La pausa persiste; el nuevo proceso no autoriza limpiar el token antiguo |
| P2-10A | Reinicio con un UNKNOWN previo por timeout u otra razón | Fence por identidad anterior y scope exacto; UNKNOWN no se absuelve ni se elimina para rearmar |
| Invariantes | Puertos nativos con fuse OFF, álbum vivo, permiso protegido o desconocido | Ningún cliente adicional ni envío real; álbum HOLD; sin copiar medios protegidos |

El recibo final debe identificar HEAD/base, pruebas nuevas y heredadas por
variante, resultados JVM/Android reales, lint/compilaciones, logs, CI con
`head_sha` exacto, hashes de ZIP separados de hashes de APK y límites no probados.
Si se captura un fallo RED del baseline, debe describir un fallo de comportamiento
reproducible, no una falta de compilación causada por la nueva API. La revisión
interna del autor no es la auditoría independiente de Grok Bot.

## Ruta mínima que queda

1. Grok Bot revisa el delta y reproduce sus regresiones; Torre decide el cierre
   limitado de cada pendiente. El autor no ratifica su propio paquete ni la pila
   congelada.
2. Resolver o delimitar expresamente la carrera de borrado remoto y los límites
   de identidad después de un reinicio. Conservar salida OFF durante esa revisión.
3. La eventual observación normal conserva una sola sesión TDLib y un solo
   colector, con identidad/firma/datos del principal comprobados y OK propio.
   La variante offline no recibe una cuenta ni una copia de sesión.
4. Una prueba futura de referencias y fotos individuales permitidas requiere
   otro cambio revisable para abrir el fusible y un OK literal con HEAD, fuentes,
   destino, ventana, máximos, custodia, STOP y reversa. Este documento no concede
   ese permiso. Fano foto sola conserva su consentimiento específico pendiente;
   Unico sin autor verificable permanece HOLD.

**Álbumes vivos HOLD** hasta prueba autoritativa de completitud. No se amplían
tipsters ni políticas; Mazdita y UnicoRey permanecen separados. ACK Telegram real,
recepción en Pick$, POCO/HyperOS y continuidad 24/7 siguen sin verificar.

Sin RS1000, Telegram real, modificación de Pick$ productivo, merge, deploy o
apuestas. No se repiten M1 o G2.

`STAKE=0; BET_ENABLED=False; authorizes_market=false; E2E-A1=BLOCKED;
F3_NO_HABILITADO; T0=NO_SELLADO; T7=0h`.
