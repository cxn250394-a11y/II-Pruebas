# Entrega de autor: álbumes y confirmaciones asíncronas

Orden: Info main `0cfb5e4`,
`coordinacion/decisiones/TORRE-RECONCILIACION-P1-Y-G4V2-20261022.md`.
Rama hija de II-Pruebas PR #8, basada exactamente en
`3b32cb78c525ba4a6594e8718c045b08113fbd7d`. El PR #8 y el PR #33 de Info
(`e4a99e311735e5381be44bec688f500334fc5602`) permanecen congelados; esta entrega
no modifica sus ramas ni reemplaza la auditoría de Grok.

## Resultado y límites

| Área | Resultado del código nuevo | Lo que falta demostrar |
|---|---|---|
| Álbum vivo | HOLD incluso con diez miembros o booleans de completitud; observación acotada por cuenta/generación/chat/álbum | El proveedor fijado no expone un testigo público utilizable de conjunto completo y cierre. No hay liberación automática. |
| Álbum ficticio | Reconstrucción solo con manifiesto Fake exacto, ordenado y del mismo ámbito | No acredita un álbum real. |
| Confirmaciones | Intención y fases durables, respuesta pendiente distinta de éxito, finales por cada temporal exacto | DTO normalizado; transporte y suscripción reales **no conectados**. |
| Incertidumbre | UNKNOWN terminal, recuperación sin reenvío, ACK tardío sin reanimación | Operación real aún no verificada. No promete exactly-once del proveedor. |
| POCO | APK separada, sin Internet ni inicio de sesión; ensayo Fake visible | Prueba física pendiente del dueño. Emulador Android no equivale a HyperOS. |

Fuentes primarias y sus hashes: [prueba del proveedor](ALBUM_PROVIDER_PROOF.md),
[pins](ALBUM_ACK_PROVIDER_PINS.json). Existe un contador en el sobre PUSH de TDLib;
no se usa como prueba de completitud del stream de mensajes actual.

## Evidencia para auditoría ajena

El Draft PR nuevo y su ejecución de CI identificarán HEAD, pruebas, APK y artefactos.
El workflow exige que fuente, APK y emulador correspondan al mismo HEAD; publica
JUnit XML, lint, firma, `SOURCE-COMMIT.txt`, `SHA256SUMS.txt` y manifiesto del APK.
`canaldeck-async-evidence.json` sale de assertions Android ejecutadas sobre Fake y
SQLite: intención anterior a salida ficticia, quick ACK sin completar, dos finales
exactos, reapertura sin despacho y `sent_real_messages=0`. No es telemetría Telegram.

Las regresiones cubren álbum parcial/extra/duplicado/edición/ámbito, callbacks antes
de respuesta, nulos parciales, finales repetidos o en conflicto, colisiones entre
fases y propietarios, caída, recuperación, TTL, permisos y cambios de consentimiento
durante la escritura. Dos defectos descubiertos en el código nuevo quedaron con
regresión: salida tras revocación durante el commit y bucle por colisión de IDs.
Toda revisión de este equipo es QA de autor, no Gate C ni ratificación independiente.

## Ruta mínima restante

1. Torre/Grok auditan este delta por su HEAD exacto, junto con los dos candidatos
   congelados. Resolver cualquier hallazgo antes de una versión que lea Telegram.
2. El dueño prueba ahora el [ensayo POCO/HyperOS](POCO_HYPEROS_VALIDACION_OFFLINE.md),
   sin red ni cuenta. El paquete `mx.charly.canal.offline` no sustituye la app
   principal y no permite conectar una segunda sesión. Conservar datos/checkpoints.
3. En otro cambio revisable, integrar el ledger y mapper con el único cliente ya
   existente, correlacionando su generación de sesión. No abrir otro lector.
   Definir el transporte real y su fusible; no reutilizar Fake como permiso de salida.
4. Con un OK separado, validar en el teléfono identidad real de Fano y fuentes
   UnicoRey, permisos por chat/mensaje, los doce tipsters permitidos y separación
   de Mazdita. Empezar por referencia y foto individual permitida; los álbumes
   vivos siguen HOLD mientras no exista prueba suficiente de completitud.
5. Solo después de revisión ajena y OK específico, preparar un envío real limitado
   con destino, tasa, custodia y STOP definidos; demostrar ACK finales y recuperación
   sin reintentos. No se concede esa activación mediante esta entrega.

No se han ejecutado merge, deploy, RS1000, lectura Telegram real, mensajes reales,
apuestas ni M1. No se modifican Pick$ productivo, Ferrari, G4 o paquetes sellados.

`STAKE=0; BET_ENABLED=False; authorizes_market=false; E2E-A1=BLOCKED;
F3_NO_HABILITADO; T0=NO_SELLADO; T7=0h`.
