# Integración de publicaciones individuales: evidencia y límites

Documento de autor del carril candidato posterior a #9. El resultado válido de
pruebas/CI es el publicado en su nuevo Draft PR y vinculado al HEAD exacto; este
archivo no anticipa cantidades ni PASS. Tampoco reutiliza como validación de este
delta las 264 JVM y 22 Android que pertenecen al HEAD congelado de #9.

Base: `6a40b2034e599a02ee801d207b9d1d4856ca70bd`. Decisión de Torre:
Info main `7ecf321bc0b5b1399e2d6d50b5495a4cc08a4f10`.

## Alcance del candidato

Preparar la integración con el **único cliente TDLib ya existente** de CanalDeck:
publicaciones nuevas individuales de texto, foto con caption y foto sola,
identidad real de fuente/destino para una futura fase y correspondencia
source→petición→temporal destino→ACK final. Las pruebas usan Fake; ninguna cuenta
se conecta para verificar este código.

El transporte y los eventos deben compartir la misma cuenta, generación de sesión
y conexión que el cliente existente. No crear otro `TdlClient`, colector de
actualizaciones o lector paralelo. Un nuevo objeto de integración no equivale a
una segunda sesión: recibe el cliente y los eventos de la ruta existente.

El contrato nuevo separa `SingleRelayController` y el puerto asíncrono
`SingleRelayPort`: los callbacks no bloquean el cliente. Reutiliza modelos,
validación y ledger de ACK de #9; el motor Fake de #9 conserva su función de
laboratorio. La admisión por baseline y generación ocurre antes de consultar
fuentes. El puerto nativo preparado debe comprobar el fusible compilado antes de
admisión, refresh, lectura o salida; las pruebas no lo convierten en una vía de
conexión real.

En este APK siguen `PICK_RELAY_REAL_ENABLED=false`,
`OFFLINE_REHEARSAL_ONLY=true`, paquete `mx.charly.canal.offline`, permiso Internet
ausente e inicio de sesión bloqueado. Preparar métodos nativos guardados por el
fusible no es ejecutar un envío ni demostrar entrega real.

Los álbumes vivos permanecen HOLD antes de ser tratados como publicaciones
individuales. No se permite partir un fragmento de álbum como foto suelta para
evitar ese HOLD. La prueba de proveedor y sus matices siguen en
[ALBUM_PROVIDER_PROOF.md](ALBUM_PROVIDER_PROOF.md).

## Dos incertidumbres distintas

| Incertidumbre | Significado y efecto |
|---|---|
| Semántica de una foto sin texto | No hay evidencia textual de que sea pick, recap, resultado, cuota o tipster. No se ejecuta OCR ni se atribuye certeza a los píxeles. La política de fuente y el consentimiento determinan si puede prepararse; no fabrican contenido o atribución. |
| UNKNOWN de entrega | Existe una intención/petición durable con resultado no confirmado. Es terminal para el despacho automático; nunca se reintenta ni se reanima por reconexión/ACK tardío. |

La semántica desconocida no debe convertirse por sí sola en una confirmación de
entrega ni en permiso para saltar la lista de doce tipsters. Un caption vacío de
UnicoRey no autoriza inventar el autor. Las políticas de Fano, familias UnicoRey y
Mazdita permanecen separadas y vinculadas a IDs, no a títulos parecidos.

La foto sola de Fano requiere un consentimiento específico que empieza apagado
y no tiene control de activación en el APK actual. El encabezado conserva un
aviso de imagen sin clasificar. UnicoRey sin caption atribuible sigue HOLD aunque
el dueño haya seleccionado esa fuente; esa selección no identifica al tipster.

## Matriz mínima de evidencia Fake

Los escenarios son requisitos de aceptación de este candidato. El PR debe
identificar qué pruebas los ejecutan, sus resultados y los artefactos del HEAD;
una fila de esta tabla no declara por sí misma que ya pasó.

| Escenario | Evidencia que debe comprobar la prueba |
|---|---|
| Texto individual | Intención antes de salida ficticia; referencia atribuida, fechada y con enlace canónico válido obligatorio; sin forward del cuerpo; pendiente/quick ACK no final; final exacto requerido. |
| Foto con caption | Source ID único, encabezado confirmado antes de solicitar un original; caption y política preservados; final de foto requerido. Protección/permiso de forward desconocido permite solo referencia con enlace válido; sin enlace válido, HOLD. |
| Foto sola | Fano requiere consentimiento específico y semántica UNKNOWN visible; sin OCR/caption/autor inventados. UnicoRey sin atribución textual verificable permanece HOLD. |
| Rechazo de encabezado | UNKNOWN; ninguna petición de foto y ningún reintento. |
| Rechazo de foto | UNKNOWN después de un encabezado posiblemente confirmado; no declarar entrega completa ni reenviar. |
| Permiso o protección cambiados | Revalidación antes de cada fase; ningún forward después de revocación. La política no autoriza copiar bytes como alternativa. |
| Configuración OFF→ON | La revisión cambia; el contexto viejo no se reutiliza aunque los valores vuelvan a coincidir. |
| Reconexión/sesión diferente | Invalida pendientes, conserva UNKNOWN y exige baseline nuevo; no vuelve a despachar backlog. |
| ACK antes de respuesta | Buffer acotado y vínculo posterior con el temporal exacto; sin confirmación por orden de llegada. |
| ACK cruzado/repetido/conflictivo | Cuenta/generación/destino/temporal correctos; repetido idéntico no duplica; conflicto no completa. |
| Caída y reapertura | Recuperación durable sin transporte; UNKNOWN/COMPLETE persistentes; cero peticiones nuevas por replay. |
| Duplicado de publicación | Una sola reserva por cuenta/fuente/mensaje, incluso si cambia destino o sesión. No deduplicar por caption/foto. |
| Álbum vivo o fragmento | HOLD; cero solicitudes de referencia/foto. Timeout, cardinalidad aparente e historial no liberan. |
| Puerta real OFF | Cero llamadas SDK de envío, cero cuenta conectada y cero mensajes reales; rechazo antes de la función nativa de salida. |

El protocolo heredado de ACK está en
[ASYNC_RELAY_PROTOCOL.md](ASYNC_RELAY_PROTOCOL.md). Los cambios de integración
deben conservar intención y petición durables antes de I/O, revisión de estado
actual antes de cada fase, correlación exacta y UNKNOWN terminal. Una respuesta
síncrona pendiente no es confirmación final; un ACK de recepción del servidor no
acredita el mensaje final ni habilita por sí mismo la siguiente fase.

## Identidad nativa y almacenamiento preparado

TDLib no incorpora nuestra generación local en un ACK. El router preparado
conserva por vida del cliente la asociación cuenta/destino/temporal→scope y
petición originales; no etiqueta un ACK conocido con la generación actual tras
reconectar. Un final temprano solo puede vincularse por la respuesta exacta de
una petición que ya existía al recibirlo. Los eventos sin vínculo no se atribuyen
por texto, orden de llegada ni nombre de canal.

Cada vida de cliente/router queda sellada a una única cuenta positiva. Si cambia
de A a B, el carril se detiene permanentemente y no rearma aunque vuelva A. El
DTO no incluye cuenta y no se usa la cuenta actual para reinterpretar un ACK
antiguo. Cambiar de cuenta requeriría un ciclo futuro autorizado que cierre el
cliente anterior; esta entrega no inicia otro cliente.

El router es acotado: 1024 vínculos, 64 eventos tempranos y 32 respuestas todavía
sin vincular. No expulsa identidades antiguas para seguir enviando. Colisión,
desbordamiento o desconexión/I/O incierto antes de obtener temporales detiene el
router; los pendientes quedan UNKNOWN y la salida se revoca. No hay reinicio
automático del router para saltarse ese STOP. Esta política puede omitir una
entrega; no acredita entrega garantizada ni elimina los límites de ACK real.
Los tombstones del router viven en memoria; la recuperación tras proceso usa
el ledger durable y no reproduce solicitudes. La semántica del proveedor con
cuenta real continúa pendiente de demostración.

Se preparan dos bases privadas de metadatos: `canaldeck-single-observations.db`
para baseline y admisión, y `canaldeck-single-ack.db` para intenciones/ACK. Se
separan de la preparación legacy para que dos consumidores locales no compitan
por la misma reserva. Ambos reciben eventos del único stream existente; no son
dos lectores Telegram. No migrar ni borrar reservas al detener o revertir.

## Recibo que debe entregar el nuevo PR

- HEAD completo, base congelada y árbol; lista de archivos cambiados y ausencia
  de cambios en las ramas #8/#9/#33.
- CI del HEAD exacto, jobs, JUnit/Android, lint, APK y artefactos. En caso de fallo,
  registrar el fallo real en lugar de declarar el objetivo como PASS.
- `SOURCE-COMMIT.txt`, checksum del APK interno, firma y manifiesto de paquete /
  permiso Internet; distinguir SHA-256 de APK y digest del ZIP de GitHub.
- Evidencia Fake de petición/reserva, estados finales, rechazo, permisos,
  reconexión y número de solicitudes por publicación. `sent_real_messages=0`
  describe la simulación, no telemetría de Pick$ real.
- Pendientes: revisión independiente del delta, prueba física POCO, integración
  operativa y OK de observación/envío cuando procedan. La CI no ratifica al autor.

## Límites y trabajo retenido

**ACK real NO_VERIFICADO; álbum vivo HOLD; automatización real OFF.** El candidato
no demuestra comportamiento del proveedor con cuenta real, aceptación en Pick$,
continuidad con pantalla apagada, funcionamiento 24/7 ni exactamente una entrega
garantizada por Telegram. La ausencia de reintentos evita fabricar duplicados por
recuperación; no transforma un resultado incierto en éxito.

Los P2 de la decisión de Torre siguen abiertos hasta su cierre separado: recaps
`WON` / `4/4`, picks legítimos con «Resultado», diferencias Kotlin/Python,
RESELLER sin familia, contadores de descarte, reconexión y procedencia de enlaces.
La prueba de este carril no sustituye el corpus canónico ni su tasa FP/FN. Deben
resolverse antes del reenvío automático desatendido.

La [propuesta de activación y reversa](SINGLE_RELAY_ACTIVACION_Y_REVERSA.md)
distingue ensayo Fake, observación y envío supervisado; cada fase real requiere
su OK y versión revisada. No borrar SQLite ni checkpoints para reanudar una
entrega. No se modifican Pick$ productivo, Ferrari, M1, G4 o RS1000. Sin merge,
deploy, apuestas ni ratificación propia.

`STAKE=0; BET_ENABLED=False; authorizes_market=false; E2E-A1=BLOCKED;
F3_NO_HABILITADO; T0=NO_SELLADO; T7=0h`.
