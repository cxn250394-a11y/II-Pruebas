# P2-11A: fusible OFF antes de construir el puente

Corrección de autor conforme a Info main `f5ba9c5aecde15915237e2ba7013d85955a4c009`,
cuya decisión se comprobó también en main. Padre: Draft #12
`df295fca5933c4249011c94c5d9ecdfae31084b6`. Los HEAD de #8, #9, #10, #11, #12
e Info #33 permanecen congelados. Este candidato no ratifica esos paquetes ni
cierra el pendiente por auditoría independiente.

## Cambio mínimo

`CompiledRelayLane` evalúa el fusible compilado antes de ejecutar una fábrica,
una operación o un cierre. No recibe un flag habilitable por preferencias o
pruebas. La sesión principal usa ese contenedor para `ExistingClientRelayBridge`;
el servicio lo usa para `RelayCoordinator`. Cerrar un contenedor sin usarlo no
construye el puente. Una fábrica fallida no se reevalúa desde cleanup.

Ambos constructores rechazan OFF antes de consultar Context o crear helpers
SQLite. La sesión no programa su tarea de expiración de relay bajo OFF. Así no
hay recuperación de pendientes, checkpoints, cobertura, pausa de recuperación
ni `prefs.stop()` originados en esos carriles.

La pantalla del puente muestra una explicación OFF y vuelve antes de consultar
configuración, cobertura o sesión conectada. No ofrece destino, fuentes, STOP
ni otros controles que alteren preferencias. No borra, migra, repara ni desactiva
la configuración anterior: incluso un `enabled=true` guardado se conserva, pero
no vence el fusible compilado. Los métodos privados mutadores/cobertura también
rechazan OFF. No se habilita el alta de nuevas fuentes desde esa pantalla.

El cliente Telegram normal, su único colector, lectura, UI y avisos locales
continúan fuera del contenedor. Los avisos de picks conservan consultas de solo
lectura de políticas/revisión preexistentes, necesarias para UnicoRey y su
separación de Mazdita. No se bloquea globalmente `RelayPreferences`: los Fake
usan preferencias sintéticas explícitas y los avisos necesitan esas lecturas.
Sus reservas locales de `Organization` son datos de los avisos, independientes
de SQLite/checkpoints del puente. Esta corrección no agrega otro almacenamiento
ni una nueva ruta para configurar UnicoRey.

## Evidencia y reproducción

La CI del Draft hijo identifica HEAD exacto antes de compilar y antes del
emulador. Ejecuta las dos suites JVM, lint y compilación normal/offline debug y
release, inspección de identidad/Internet/fusibles/firma de cuatro APK y pruebas
Android en emulador API 35. Solo se publica el APK offline debug. No se instala
una app en el dispositivo del dueño ni se conecta una cuenta.

`check-compiled-relay-off.py` contrasta el cableado de ambos puntos de fábrica,
teardown, temporizador y ajustes, además de conservar los hooks locales y el
único cliente/colector. La misma comprobación sobre el código padre falla por
la construcción sin gate; `evidence/compiled-off-wiring.json` contiene stdout,
stderr y códigos literales. Es **RED estático de cableado**, no una ejecución de
TDLib ni una prueba nativa de sesión del padre. Complementa las pruebas de
comportamiento; no las sustituye.

Las regresiones compartidas prueban fábricas espía nunca evaluadas, operaciones
y cierres nulos, recreaciones, configuración previa, datos inválidos conservados,
ausencia de archivos nuevos y UI OFF. Los fixtures contienen enabled, revisión,
consentimiento de foto, pausa, checkpoints e intentos pendientes/UNKNOWN. Las
pruebas de avisos locales usan datos sintéticos y Fake; no crean TdlClient.

Los stores de prueba usan Context con prefijo UUID. La pantalla se ejecuta sin
sesión conectada y usa su Context normal: su aislamiento de una configuración
conectada se sustenta en el retorno previo a preferencias verificado en código
y en el gate ejercitado, no en una cuenta nativa activa. Las pruebas de avisos
comprueban la publicación local Fake y deduplicación; no la recepción de una
notificación Android originada en Telegram real.

Android comprueba preferencias y archivos SQLite sin cambios y vuelve a abrir
la pantalla. Una prueba adicional se ejecuta en dos fases por cada variante:
seed guarda fixtures propios y sus hashes; se fuerza la detención **del paquete
del emulador**; verify abre otro proceso de instrumentación, exige PID distinto
y contrasta la misma evidencia antes/después de los hooks OFF. Solo se limpian
fixtures sintéticos de esa prueba. Esto prueba persistencia a través de un
reinicio de proceso del laboratorio, sin iniciar el motor TDLib.

Los logs, XML JUnit, resultados de variantes y JSON del dispositivo son la fuente
de los conteos finales. El recibo del Draft identifica resultados observados,
run/HEAD, artefactos y hashes. Los tests compartidos se cuentan por ejecución de
variante, no como casos distintos duplicados. Un fallo de entorno/compilación
no se presenta como RED de comportamiento.

## Límites y ruta restante

- La revisión interna del autor no sustituye a Grok Bot. #12 sigue pendiente de
  su auditoría de P2-9B/9C/10A; este delta solo aborda P2-11A.
- Se comprueban composición de código, UI, stores y hooks con Fake. No se prueba
  una cuenta Telegram, recepción en Pick$, ACK real ni funcionamiento 24/7.
- La identidad normal sigue `mx.charly.canal`, con Internet; offline sigue
  `mx.charly.canal.offline`, sin Internet y sin inicio de sesión. Release queda
  sin firma final; debug usa firma temporal de CI. La compatibilidad de firma
  con la instalación POCO no se ha establecido.
- Los álbumes vivos permanecen HOLD, y la carrera remota de borrado de encabezado
  del #12 conserva su límite. No se altera el protocolo ACK ni se agrega reintento.
- Después de auditoría y dictamen de Torre, el dueño necesitará una propuesta
  separada de identidad/firma/datos para POCO. Una futura prueba de referencias
  o fotos individuales exige autorización independiente con HEAD, destino,
  fuentes, ventana, máximos, STOP y reversa. Este candidato no la habilita.
- Para retirar este candidato no se instala ni fusiona; se conserva el padre
  congelado. No se necesita tocar ni limpiar configuración existente.

Salida real compilada OFF en las cuatro variantes. Una sola sesión/colector
previstos; sin cuenta en pruebas. Sin RS1000, Telegram real, Pick$ productivo,
merge, deploy, apuestas ni repetición de M1/G2.

`STAKE=0; BET_ENABLED=False; authorizes_market=false; E2E-A1=BLOCKED;
F3_NO_HABILITADO; T0=NO_SELLADO; T7=0h`.
