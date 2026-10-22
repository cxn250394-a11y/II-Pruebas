# Entrega de autor: integración individual con el cliente existente

Base exacta: II-Pruebas PR #9 `6a40b2034e599a02ee801d207b9d1d4856ca70bd`.
Orden: Info main `7ecf321bc0b5b1399e2d6d50b5495a4cc08a4f10`.
Se conservan congelados PR #8 `3b32cb78c525ba4a6594e8718c045b08113fbd7d`,
PR #9 y el PR #33 de Info `e4a99e311735e5381be44bec688f500334fc5602`.
El nuevo Draft PR identifica HEAD, árbol, CI y artefactos de esta entrega.

## Código preparado

- `SingleRelayController` usa el protocolo durable de #9 con callbacks y
  revalidación actual. El motor Fake anterior y el esquema de ACK se conservan.
- `ExistingClientRelayBridge` recibe el cliente ya creado por `TelegramSession`.
  Sus hooks antes/después de updates usan el mismo colector existente. No hay
  segunda sesión, lector, suscripción ni consulta Telegram ejecutada en pruebas.
- `ExistingClientRelayPort` y `TdlSingleRelayCalls` preparan los métodos nativos
  detrás del fusible literal OFF. Se retira del adaptador el método legacy de
  entrega síncrona para que no haya una vía alternativa de confirmación.
- Admisión por baseline durable y generación, reserva anterior a salida,
  correlación source→request→temporal→final, UNKNOWN terminal y recuperación
  sin retransmisión. ACK conocido conserva su scope tras reconexión; un ID
  reutilizado, desbordamiento o resultado sin vínculo detiene el router.

## Publicaciones y límites

| Entrada | Preparación |
|---|---|
| Texto | Referencia atribuida, fechada y con enlace válido obligatorio; HEADER solamente, sin copiar el cuerpo. |
| Foto con caption | Encabezado y un original permitido después de su ACK final. Se conserva caption, sin descargar/copiar medios. Protección o permiso desconocido: solo referencia con enlace válido. |
| Foto sola Fano | Consentimiento específico por fuente, apagado de fábrica; enlace y permisos; encabezado explícito de imagen sin clasificar. No demuestra que sea un pick. |
| Foto sola UnicoRey | HOLD sin tipster textual verificable; no hereda un autor del mensaje anterior ni de la imagen. |
| Álbum vivo | HOLD antes de reservar/consultar para despacho; no se convierte un fragmento en foto individual. |

Cuenta, chats y destino reales requieren selección privada del dueño. Las cuatro
familias UnicoRey comparten la lista autorizada de doce tipsters; no se inventa
una partición por autor ni se hereda la política para Mazdita.

## Verificación y siguiente paso

Las pruebas nuevas usan Fake y SQLite; el recibo del Draft PR y su CI deben
informar resultados reales del HEAD, sin tomar como evidencia la CI de #9.
La suite cubre éxito final, rechazo, permisos/configuración cambiados,
reconexión, callbacks tardíos y repetidos, baseline sin backlog, reapertura y
ausencia de nuevas solicitudes para una publicación ya reservada.

El APK sigue separado como `mx.charly.canal.offline`, sin INTERNET y con login
bloqueado. `PICK_RELAY_REAL_ENABLED=false`; no se proporciona una opción de UI
para abrirlo ni para conceder fotos sin texto. La pantalla Fake de #9 sigue
disponible; la nueva integración individual se valida por pruebas automáticas.

Pendientes: auditoría independiente del delta, cerrar los P2 semánticos en su
carril antes de automatización desatendida, versión/OK separados de observación,
prueba física POCO/HyperOS y autorización específica antes de enviar algo real.
ACK real NO_VERIFICADO, álbum vivo HOLD. CI no demuestra recepción en Pick$.

[Contrato, router y límites](SINGLE_RELAY_EVIDENCIA_Y_LIMITES.md).
[Procedimiento futuro de activación y reversa](SINGLE_RELAY_ACTIVACION_Y_REVERSA.md).
STOP revoca salida y consentimiento de foto sola, invalida pendientes como
UNKNOWN y conserva SQLite; no borra mensajes, checkpoints ni entrega incierta.

QA de autor, sin ratificación propia. Sin RS1000, M1, merge, deploy, apuestas ni
mensajes reales. Pick$ productivo, Ferrari y G4 permanecen fuera del cambio.

`STAKE=0; BET_ENABLED=False; authorizes_market=false; E2E-A1=BLOCKED;
F3_NO_HABILITADO; T0=NO_SELLADO; T7=0h`.
