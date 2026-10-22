# CanalDeck v0.4 — segunda ronda de correcciones

Entrega de desarrollo de Workito para revisión delta de Grok y ratificación de la Torre. La revisión interna y la CI son evidencia del autor; no constituyen ratificación independiente.

Mandato: Info `8004c2a`, `coordinacion/decisiones/TORRE-TRIAGE-M1-CANALDECK-G4-20261022.md`. Este delta parte de II-Pruebas Draft #8 `a102eb8433c227291b3ef1c9e2972456cc443871`. Conserva el padre #5 y la base `8c385906476c627da51f040f899620088c968ce5`.

## Correcciones para revisar

| Hallazgo | Comportamiento exigido y regresión |
| --- | --- |
| P1-5A: callbacks de enlaces invertidos | Dos mensajes posteriores a la activación deben poder notificarse en orden 102→101. El conjunto durable de identidades notificadas se mantiene separado del límite de activación; repetir cada identidad después de recrear el almacén debe rechazarse. |
| P1-5B: recap/resultado anunciado como pick | Rechazar un recap o resultado inequívoco, incluso con foto o atribución. Conservar un pick nuevo explícito que convive con un balance anterior y una mención incidental a resultados. El mismo clasificador se aplica al planner del puente. |
| Permisos locales | Antes de reservar una identidad se comprueban permiso de Android, interruptor general, canal individual y lectura del mensaje. Se revalida el contexto después del callback; un cambio de cuenta, sesión o configuración invalida el trabajo pendiente. |
| Enlaces | El enlace proviene de `getMessageLink(chatId,messageId)` de la sesión existente. La gramática solo valida la forma; nunca prueba por sí sola la procedencia. Se rechazan rutas reservadas e identificadores inválidos y se ensayan enlaces de tema. |
| Mazdita/UnicoRey | La lista UnicoRey requiere asignación explícita de familia a la cuenta/chat. Un título parecido o activar el antiguo modo genérico RESELLER no transfiere la lista a Mazdita. |

## Reproducción y evidencias

Desde `apps/canaldeck`:

```sh
python3 scripts/check-source.py
python3 scripts/check-relay-offline.py
gradle --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
python3 scripts/report-jvm.py
```

La CI publica XML JUnit, lint, log de compilación, fuente, APK y evidencia del emulador API 35. El trabajo de compilación exige que el checkout sea el HEAD solicitado; el emulador comprueba además `SOURCE-COMMIT.txt` del artefacto APK contra ese HEAD. Los enlaces al run y sus artefactos, asociados a la SHA final completa, se añaden al cuerpo del Draft PR tras completar la CI.

Las primeras regresiones ejecutadas contra el Kotlin anterior reprodujeron la pérdida del ID menor, la debilidad durable del álbum intercalado, el recap aceptado y las rutas/IDs/query inválidos. Los resultados finales y el detalle de pruebas quedan en la evidencia de esta ronda y en los artefactos del run. Las fixtures usan IDs y contenido sintéticos.

Los dos repositorios ejecutan el mismo corpus de 80 enlaces (11 aceptados, 69 rechazados), SHA-256 `09d5d1a307870f12d7700cf0e3afdedec0db0d57bc95475c3e4bad898a37f77d`. Los aceptados se devuelven byte por byte. Se admiten permalinks públicos/privados y sus rutas de tema, con IDs positivos canónicos y como única query opcional el flag exacto `?single`; las demás variantes muestran `No disponible`. Ni un enlace aceptado ni una referencia manual prueban la cuenta o fuente de Telegram.

## Uso y límites pendientes

Para avisos locales de un grupo UnicoRey, seleccionar primero la conversación real y su familia en los ajustes del puente y después activar sus avisos locales. Asignar una fuente no habilita la salida automática. Los canales directos conservan su selección local explícita por cuenta/chat. Mazdita conserva su configuración separada; no se deduce su identidad de su nombre.

No hay cola automática que reanuncie mensajes descartados durante silencio, permisos desactivados, mensaje leído o desconexión. Las condiciones de edad máxima y punto de activación continúan vigentes. El filtro de resultados trabaja con texto/caption; una foto sin texto no permite demostrar si contiene un resultado. No se ha probado OCR ni se usa para saltar protección.

La SQLite local conserva identidades por cuenta/chat/mensaje y álbum tipado. Una reserva pendiente tras caída se convierte en `UNKNOWN`; no se repite automáticamente. El punto de activación no avanza por el orden de callbacks. `NotificationManager.notify` y SQLite no forman una transacción: `POSTED/NOTIFIED` significa que la llamada local regresó, no acredita que el dueño viera el aviso ni elimina la carrera con un cambio del sistema ocurrido después de la comprobación final.

La salida real continúa compilada OFF. Álbumes vivos sin cardinalidad acreditada permanecen HOLD; ACK real asíncrono y POCO/HyperOS siguen pendientes. El módulo Python de Info Draft #33 requiere su propia revisión delta e integración futura: esta ronda no lo conecta a un lector productivo.

La siguiente decisión corresponde a Grok/Torre sobre las SHA finales y sus CI. Cualquier prueba física o activación requiere su autorización específica. M1 ya está ejecutado; no forma parte de este delta.

`STAKE=0; BET_ENABLED=False; authorizes_market=false; E2E-A1=BLOCKED; F3_NO_HABILITADO; T0=NO_SELLADO; T7=0h`.
