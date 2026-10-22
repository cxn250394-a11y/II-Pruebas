# Publicaciones individuales: ensayo y futura activación

Propuesta de autor para la Torre; **no es un OK operativo ni una ratificación**.
Sigue la decisión de Info main `7ecf321bc0b5b1399e2d6d50b5495a4cc08a4f10`.
Este carril parte de PR #9 `6a40b2034e599a02ee801d207b9d1d4856ca70bd` y no
modifica los HEAD congelados de #8, #9 o Info #33.

## A. Ensayo autorizado ahora: Fake, sin cuenta ni red

El APK de este carril conserva `mx.charly.canal.offline`, carece de permiso de
Internet, bloquea el inicio de sesión y compila `PICK_RELAY_REAL_ENABLED=false`.
El dueño debe recibir de la Torre el HEAD, ejecución CI, APK, SHA-256 y firma del
artefacto exacto. Una compilación anterior o una CI de otro HEAD no lo sustituye.

1. Conservar CanalDeck principal y sus datos. No desinstalar ni borrar datos para
   resolver una firma incompatible; detenerse y comunicarlo a la Torre.
2. Mantener modo avión, Wi-Fi y datos apagados. No introducir identidad de
   aplicación, teléfono, código o contraseña. Si el candidato ofrece conectar
   una cuenta, detener el ensayo y revisar qué APK se instaló.
3. Ejecutar las suites Fake de publicaciones individuales del candidato. Deben
   cubrir texto, foto con caption y foto sola; comparar solicitudes ficticias,
   estados durables y resultados con la matriz de
   [evidencia y límites](SINGLE_RELAY_EVIDENCIA_Y_LIMITES.md).
4. Conservar el resultado de CI y, si el dueño prueba el APK, anotar modelo POCO,
   versión Android/HyperOS, versión del candidato y comportamiento observado.
   La pantalla de ensayo heredada de #9 es una simulación de álbum; recorrerla
   por sí sola no prueba la nueva integración de publicaciones individuales.
   No se añade una pantalla para activar el conector individual en este APK:
   su integración se prueba mediante suites JVM/Android automáticas con Fake.

Aceptación de A: confirmación final exacta, rechazo/permiso cambiado/reconexión
sin nuevas solicitudes para la misma publicación, UNKNOWN terminal y cero
envíos reales. El emulador no acredita comportamiento de HyperOS con pantalla
apagada, continuidad 24/7, identidad real de los canales ni recepción en Pick$.

## B. Observación con cuenta real: otro OK y otra versión

El APK actual no puede realizar B. La Torre debe preparar una versión revisable
de observación, manteniendo salida real OFF, y obtener un OK específico de
Charly antes de conectar una cuenta. No derivarlo del desarrollo, de Ferrari,
del ensayo A ni del permiso para revisar código.

Antes de B deben quedar fijados:

| Decisión | Registro privado que debe confirmar el dueño |
|---|---|
| Identidad | Cuenta y generación de la única sesión CanalDeck existente; sin segundo cliente ni lector. |
| Fuentes | IDs reales elegidos en el teléfono: Fano y cada familia UnicoRey; Mazdita separada. No enlazar chats solo por título. |
| Alcance | Ventana de observación CDMX, métricas permitidas, responsable y STOP; salida de texto/foto todavía OFF. |
| Arranque | Baseline actual por fuente tras cargar su último mensaje; sin históricos ni cola retroactiva. |
| Custodia | IDs, estados y contadores privados; no publicar credenciales, cuerpos, fotografías ni IDs privados en GitHub/chat. |

Durante B se observan únicamente publicaciones nuevas de esa ventana. Un cambio
de conexión, autenticación, cuenta o generación invalida el trabajo pendiente;
la reconexión exige nuevo baseline y no habilita replay. En HyperOS se podrá
medir, dentro de su OK, el efecto de pérdida de red, pantalla apagada y reapertura.
No prometer que el servicio permanezca activo ni modificar batería/inicio
automático sin decisión del dueño.

## C. Referencias y foto individual: futuro OK independiente

C requiere otro cambio revisable que abra de forma expresa el fusible compilado,
auditoría ajena por HEAD exacto, decisión de la Torre y **OK literal nuevo de
Charly**. La instalación actual ni un ajuste de usuario pueden abrir ese fusible.

Antes de autorizar reenvío automático desatendido, la Torre debe cerrar los P2
pendientes descritos en `7ecf321`: recaps/resultados, falsos
negativos, paridad Kotlin/Python, familia RESELLER, cobertura de descartes,
reconexión y procedencia de enlaces. Este carril no los da por resueltos. Una prueba
supervisada futura deberá tener alcance y gates expresos de la Torre; este
documento no concede excepciones ni habilita un primer envío.

El primer alcance propuesto es una ventana breve y supervisada. El OK debe fijar
el destino real elegido por el dueño, fuentes exactas, tipos de contenido,
ventana CDMX, tasa y máximo de publicaciones y mensajes, custodia, STOP y reversa.
No hay una tasa aprobada en este documento. Probar primero una referencia y
después una foto individual permitida limita la cantidad de fases expuestas.

| Publicación | Condición de contenido para una futura prueba |
|---|---|
| Texto individual | Solo referencia con canal/tipster, fecha original y enlace canónico válido obligatorio; no se reenvía el cuerpo del texto. Sin enlace válido, HOLD. Un callback pendiente no la confirma. |
| Foto con caption | Encabezado y después un único original permitido, conservando caption. Revalidar protección y permisos por mensaje antes de la fase de foto. Foto protegida o permiso de forward desconocido permite solo referencia si tiene enlace válido; sin ese enlace, HOLD. No descargar/copiar para eludir restricciones. |
| Foto sola de Fano | Semántica UNKNOWN y aviso visible de imagen sin clasificar; no afirmar que es un pick, acertó o tiene cuota. Requiere consentimiento específico para foto sin texto, apagado por defecto y sin control de activación en el APK actual. También requiere identidad de canal, enlace válido y permisos para la fase permitida. |
| Foto sola de UnicoRey | HOLD: falta atribución textual verificable del tipster. No inferir autor desde la imagen, el mensaje anterior o el nombre de una fuente. |
| Álbum vivo | HOLD. Un contador PUSH, silencio, timeout, diez miembros o una ventana de historial no acreditan el conjunto completo. No incluir álbumes vivos en el primer OK. |

En UnicoRey la lista de doce tipsters no se amplía por similitud. Si una foto sola
no permite verificar el tipster requerido por la política, no se inventa ese
nombre ni se hereda de otra publicación: permanece retenida. Fano se atribuye
como canal directo cuando su identidad real está vinculada.

Esqueleto para que la Torre complete **solo tras los gates**; no es autorización:

> BORRADOR NO OTORGADO: autorizo una prueba móvil supervisada del HEAD [SHA],
> cuenta/sesión local [identidad], destino [ID confirmado en el teléfono], fuentes
> [IDs y políticas], tipos [texto / foto con caption / foto sola según alcance],
> durante [inicio-fin CDMX], tasa [límite] y máximos [publicaciones/mensajes].
> Baseline actual, sin históricos ni álbumes vivos, sin gastos ni apuestas.
> Revalidar permisos antes de cada fase. Ante rechazo, cambio de permisos,
> reconexión, error o UNKNOWN: STOP sin reintento. Reversa limitada a [pasos],
> preservando SQLite y evidencias. No habilita Ferrari, RS1000, merge o deploy.

## STOP y reversa futura: conservar la incertidumbre

Esta secuencia debe implementarse y revisarse antes de C. No ejecutar una
reversa real ahora ni ampliar un permiso anterior mediante este documento.

1. Desactivar la salida para esa cuenta, revocar también la autorización de foto
   sin texto y aumentar la revisión de configuración.
   Invalidar el consentimiento y la generación de conexión vigentes antes de
   permitir cualquier nueva fase. OFF→ON posterior no vuelve válido el contexto
   anterior.
2. Detener nuevas reservas y despachos. Toda petición persistida cuyo resultado
   no quedó confirmado se conserva como UNKNOWN; no se vuelve a enviar para
   averiguar si llegó. Un ACK tardío no reanima la fase de foto.
3. Conservar SQLite, aliases de mensajes, temporales/finales, baselines, revisiones
   y contadores. No borrar checkpoints, UNKNOWN ni datos de TDLib; no reiniciar
   la deduplicación ni generar una identidad alternativa para la misma publicación.
4. Preservar evidencia privada de HEAD, hora de STOP, última fase y motivo. Un
   encabezado confirmado no acredita la foto; STOP no puede deshacer un mensaje
   que el proveedor ya aceptó. Borrar mensajes reales sería otra operación y
   requiere un alcance expreso diferente.
5. Si se propone volver a un APK anterior, comprobar antes firma, paquete y
   compatibilidad del esquema. No forzar downgrade, migración destructiva,
   desinstalación o restauración de una base antigua que pierda reservas. Si la
   compatibilidad no está demostrada, conservar la versión instalada con salida
   OFF y detener la actualización.
6. Reanudar exige revisión de la causa y el OK que corresponda. Fijar baseline
   nuevo para futuras publicaciones; las anteriores UNKNOWN no constituyen una
   cola autorizada. No hay reversas de apuestas ni apuestas en este flujo.

La entrega actual termina en código, pruebas y propuesta offline. ACK Telegram
real y prueba física POCO siguen pendientes; Workito no ratifica su propio código.
