# Regresiones heredadas: evidencia de autor

Base exacta: PR #11 `85c616c41d6390a294e6f7b3d3b7095867aacf8a`.
Entorno local offline, Kotlin CLI + JUnit 4; sin TDLib cliente/cuenta, host RS1000 o envíos.
No es auditoría independiente ni prueba de Telegram real.

| Captura | Qué demuestra | Resultado literal |
|---|---|---|
| `header-red.log` | Controller/modelos originales: el Fake declara encabezado final ausente, pero sale solicitud de foto | 1 prueba, 1 fallo; esperaba 1 solicitud y observó 2 |
| `header-green.log` | Controller corregido con el mismo repro más 13 regresiones del repo | 14 PASS |
| `router-red.log` | Router original: desbordamiento de tráfico ajeno entre destinos y del mismo destino produce STOP global | 12 pruebas, 2 fallos |
| `router-green.log` | Reparación inicial del router: cuota por pendiente, incertidumbre local y recuperación explícita | 17 PASS |

La adición final de `cancelBeforeInvocation` lleva el router a 19 pruebas. No se
atribuye ese conteo a `router-green.log`: la compilación CLI de esos dos casos se
interrumpió sin resultado; su ejecución pertenece al XML JUnit de la compilación
final y a la CI del nuevo HEAD. Las pruebas completas por flavor tampoco deben
sumarse como si fueran casos distintos.

El repro del encabezado y su script portable están en `qa/inherited/`. Un error de
compilación no cuenta como RED. El método sintético de lectura del Fake existe
mediante una interfaz auxiliar compatible con ambas versiones; no se parchea el
controller ni los modelos originales para conseguir el fallo.

Las pruebas del router en `ExistingClientRelayAckRouterTest.kt` identifican
`foreignFloodAtOnePendingDestinationDoesNotPoisonAnotherDestination` y
`sameDestinationLossIsLocalUnknownAndDoesNotContaminateAFutureRequest`. Para reproducir el RED
del router, usar sus tres casos de saturación sobre el router original y el
mapper/DTO fijados, antes de aplicar los casos nuevos de recuperación. Los logs
preservan los nombres efectivos de los dos fallos.

La entrega final debe vincular HEAD, CI, XML/JSON Android y hashes de artefactos.
Estos logs son subconjuntos locales y no sustituyen esa evidencia. Los límites
de atomicidad, tráfico indistinguible y recuperación de otro proceso siguen en
`docs/INTEGRIDAD_SINGLE_P2_Y_RECUPERACION.md`.
