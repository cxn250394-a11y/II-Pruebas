# CanalDeck v0.4 — puente Pick$ desarrollado offline

Estado: candidato Draft, envío real compilado OFF. Base única: PR #5, `8c385906476c627da51f040f899620088c968ce5`. Misión: Info `3cdaa047d7ef0b7034699ffc71c2c1ecf5f5de8b`, `coordinacion/misiones/TORRE-WORKITO-CANALDECK-PICKS-V04-CARRIL-OFFLINE-20261022.md`.

## Comportamiento implementado

La pantalla de preparación permite elegir destino desde los canales que la cuenta de CanalDeck ya cargó. La identidad persistida es cuenta + chat ID, nunca un nombre o username adivinado. Se exige un canal real en la selección y el adaptador comprueba de nuevo que la cuenta puede publicar: propietario miembro o administrador con permiso de publicación, sin coste en Stars.

Las fuentes se vinculan con chat ID real y política elegida por el dueño: Fano directo o una de cuatro familias UnicoRey. El título sirve para mostrar Canal, no para identificar la fuente. Mazdita, Gallito, Elite y DIRECT_OTHER no heredan la lista de Unico. `policy-source-registry.json` conserva la referencia de política con todos los IDs pendientes/null.

El planificador devuelve DROP, HOLD, REFERENCE_ONLY o FORWARD_ALLOWED_MEDIA. Valida cuenta, fuente única, rol/familia, destino distinto de toda fuente, mensaje entrante sin edición, fecha del post fuente y vigencia máxima de cuatro horas, permisos de chat y de cada mensaje. Ausencia de fecha acreditada impide probar vigencia y queda en HOLD. Una restricción de reenvío permite solo la referencia, nunca copiar el texto original o los bytes de la foto.

El encabezado tiene tres líneas: Canal o Tipster; Hora original del post fuente según TDLib Message.date, en CDMX; Link obtenido por TDLib o No disponible. Las menciones casuales no acreditan tipster; atribuciones múltiples/conflictivas o nombres no autorizados se rechazan. Los doce nombres son exactamente los de v0.3 y PR #33. No se hace OCR.

Un álbum sintético completo y coherente produce un encabezado y una operación con todos los IDs originales ordenados. Un fragmento o álbum con miembros faltantes queda en HOLD. Los originales conservan foto/caption/atribución mediante forwardMessages con sendCopy=false y removeCaption=false, sin descargar ni reconstruir medios.

## Puertas OFF y adaptador

`BuildConfig.PICK_RELAY_REAL_ENABLED=false` es un literal compilado, sin override de Gradle ni configuración remota. El interruptor del usuario también arranca OFF y no puede abrirse en esta APK. La ruta del servicio usa solamente snapshot + prepare; no llama al transporte Fake ni a operaciones reales de salida.

`TdlRelayAdapter` recibe el mismo TdlClient existente. Sus snapshots releen chats, mensajes, propiedades y enlaces; no crean otra sesión. La costura real `deliverAfterSeparateActivation` está sin llamadores y exige la puerta compilada antes de cualquier I/O. Relee permisos y consentimiento antes del encabezado y de los originales. Sus operaciones oficiales están compiladas y comprobadas contra el proveedor fijado, pero no se ejercen en esta misión.

El flujo ejecutable de entrega está en Fake TDLib + SQLite/in-memory. Solo acepta lotes marcados sintéticos y transporte sintético. Las confirmaciones Fake no incrementan confirmados reales. La BD rechaza aumentar REAL_CONFIRMED en esta versión.

## Persistencia, caídas y STOP

Las claves son cuenta + fuente + mensaje/álbum; cada miembro del álbum también se reserva atómicamente para evitar que reaparezca como mensaje suelto u otro álbum. La misma publicación con identidades diferentes en dos fuentes conserva sus dos atribuciones: no se inventa una identidad global por similitud de contenido.

SQLite conserva baselines monotónicos, observados, estados PENDING/CONFIRMED/UNKNOWN y contadores. No persiste texto, foto, caption, enlace ni payload de salida. Se confirma la reserva antes de entregar; al reiniciar, PENDING pasa a UNKNOWN, que no se reintenta. Un ACK incierto, excepción o permiso revocado detiene el flujo. Puede perderse una entrega para evitar duplicarla; no se promete entrega exactamente una vez ante incertidumbre.

Al activar/reconectar se fija un baseline desde el último ID cargado y la hora actual; los mensajes históricos y el backlog no se retransmiten. Cerrar/reabrir no autoriza nuevas entregas. STOP apaga salida y conserva elecciones, checkpoints y UNKNOWN; no borra la BD ni programa reintentos. Un fallo de persistencia deja la configuración cerrada para todas las instancias de la cuenta. El fence de activación rechaza también el mismo segundo del corte; puede omitir un primer evento legítimo de ese segundo para evitar backlog.

## Cobertura local y privada

Por cuenta, chat ID y ventana horaria se distinguen vistos por CanalDeck, candidatos, atribución verificada, descartados, protección conocida, únicos observados, notificados localmente, preparados y confirmados reales. El diagnóstico de candidatos puede funcionar con la salida OFF sin preparar una entrega. Un selector de cobertura de otros chats permite comparar Mazdita y Unico sin otorgarles una ruta.

Estos son mensajes observados durante conexión, no publicaciones totales de Telegram. Los controles solo cuentan metadatos y no deben exportar IDs personales, contenido o capturas de chats reales al PR. Los confirmados reales permanecen en cero.

## Matriz de validación y evidencias

| Caso | Pruebas / evidencia |
|---|---|
| 12 tipsters x 4 familias y 16 nombres no permitidos | PickRelayPlannerTest |
| Fano texto, foto, álbum completo y parcial | PickRelayPlannerTest / PickRelayEngineTest |
| Fecha/link faltantes, invitaciones, mismo título distinto ID | PickRelayPlannerTest / PickBridgeRulesTest |
| Otra cuenta, fuente=destino, Mazdita sin herencia | Planner / Preferences / Selection tests |
| Replay, solapamiento de álbum y reinicio | Engine / RelayDatabaseTest / RelayPersistenceDeviceTest |
| Permiso revocado antes de encabezado o medios | Engine / RelayPersistenceDeviceTest |
| Caída antes de ACK y cola UNKNOWN sin reintento | Engine / SQLite instrumentation |
| OFF compilado y OFF del usuario, sin cliente adicional | TdlRelayGateTest / check-relay-offline.py |
| Android real emulado con Fake TDLib y SQLite | Tests instrumentados, archivo canaldeck-relay-evidence.json |
| Selección/persistencia Android sin cuenta real | RelaySettingsDeviceTest |

La CI genera JVM/lint/APK/AndroidTest y ejecuta el emulador sin credenciales. Artefactos: canaldeck-source, canaldeck-apk, canaldeck-tests, canaldeck-emulator. SOURCE-COMMIT.txt fija el HEAD realmente construido; SHA256SUMS.txt y APK-VERIFY.txt acreditan el APK. El JSON Fake lo produce la instrumentación solo después de las aserciones; no es un archivo manual que simule una ejecución. Los enlaces y resultados finales de CI se registran en la descripción del PR para no confundir evidencia de otro HEAD.

## Limitaciones de proveedor y del dispositivo

TDLib 14.0.0 no proporciona aquí un total autoritativo de miembros de un álbum mediante GetMessageAlbum. La app no convierte un timeout en evidencia de completitud: los álbumes vivos quedan HOLD. Fake sí prueba el contrato de álbum completo. Resolver y revisar esa prueba de completitud es pendiente de la activación; no se presenta como reenvío real terminado.

Un envío aceptado que todavía tiene sendingState es UNKNOWN, no una confirmación final. Antes de activar hacen falta integración y pruebas de ACK asíncronos, reconciliación sin reintento y revisión ajena. El método dormido no elimina esos pendientes.

En POCO/HyperOS se debe probar la conexión visible mediante foreground service, pantalla apagada, ahorro de energía, restricciones de batería y reconexión. El dueño decidirá en el teléfono los permisos de notificaciones/autoinicio y batería; esta APK no cambia ajustes del sistema ni promete 24/7. Hay que comprobar v0.3 con la cuenta y chats reales antes de habilitar v0.4.

## Gates de Torre

A: un Draft PR hijo aislado, sin modificar PR #5 ni main.
B: CI verde del HEAD exacto y evidencia Fake con sent_real_messages=0.
C: revisión ajena a Work2. La QA de subagentes del mismo Work2 es autoría interna, no ratificación.
D: prueba del propietario con v0.3 en POCO/HyperOS, fuentes e identidades/permisos reales.
E: nuevo PR de activación y OK operativo literal para Pick$ móvil.

No se modifican Info main productivo, Pick$ 0.2.0, Ferrari G2, M1 13:17, RS1000 ni sus tareas. Sin merge, deploy, apuestas o Telegram real. STAKE=0; BET_ENABLED=False; authorizes_market=false; E2E-A1=BLOCKED.
