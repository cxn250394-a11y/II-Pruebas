# Reproducción offline P2-9B

Este helper compara el controlador original del PR #11,
`85c616c41d6390a294e6f7b3d3b7095867aacf8a`, con el controlador corregido del
checkout candidato que contiene este directorio. No modifica ninguno de los
checkouts. Solo escribe en un directorio externo y vacío elegido por el auditor;
rechaza evidencia previa y fuentes modificadas respecto de sus respectivos HEAD.

`BaselineHeaderMissingReproTest.kt` está fuera de los source sets Android/JVM de
Gradle: **no añade casos a sus conteos**. Declara un proveedor sintético donde el
encabezado final está ausente y contabiliza requests Fake. La interfaz privada
`HeaderProbe` permite que el mismo objeto tenga `checkHeaderExists()` incluso
cuando se compila con el `SingleRelayPort` original, que no declara ese método.
El `override` de la fixture satisface esa interfaz privada en el baseline; **no
agrega ni modifica una API del código de producción original**. En el candidato,
el mismo método satisface además el nuevo seam de `SingleRelayPort`.

Resultado semántico esperado: el baseline envía dos requests (encabezado y foto),
cuando el assert exige únicamente el primero. El candidato consulta el proveedor
sintético, observa ausencia, queda UNKNOWN y no solicita foto. El script conserva
modelos y controlador originales para RED; usa los modelos/controlador corregidos
para GREEN. Sus diez fuentes comunes deben coincidir byte por byte entre ambas
versiones. GREEN ejecuta también las trece regresiones del source set candidato:
**14 PASS del helper = 1 repro externo + 13 tests de producción de pruebas**.

Un error de compilación, clase ausente, dependencia ausente o fallo distinto del
assert `expected:<1> but was:<2>` **NO cuenta como RED**. El script exige
compilación exitosa, exit 1 de JUnit, ese assert y exactamente un caso fallido.
Para GREEN exige exit 0 y `OK (14 tests)`; un fallo no se transforma en PASS.

## Dependencias ya disponibles

Entorno Linux con Bash, Git y coreutils; Java 17 y compilador Kotlin 2.4.10.
El directorio de herramientas contiene estos archivos ya obtenidos y sus
dependencias locales: `compiler.jar`, `stdlib.jar`, `reflect.jar`, `script.jar`,
`coroutines.jar`, `annotations.jar`, `junit.jar` (4.13.2) y `hamcrest.jar`.
El script no descarga nada ni instala componentes. Registra versiones Java/Kotlin
y SHA256 de esos JAR; el compilador usa el classpath de herramientas existente.

Preparar por separado un checkout Git limpio del HEAD baseline exacto y otro del
HEAD candidato exacto publicado en el Draft. No cambiar las ramas auditadas para
preparar esos checkouts. Desde el checkout candidato:

```sh
bash apps/canaldeck/qa/inherited/repro-header.sh \
  /ruta/al/checkout-baseline \
  /ruta/a/herramientas-locales \
  /ruta/al/jdk17/bin/java \
  /ruta/externa/nueva/evidencia-header
```

Los cuatro argumentos son rutas locales del auditor. La última debe estar fuera
de ambos checkouts y vacía; no se borra ni sobrescribe material anterior. El
script comprueba el SHA Git completo del baseline, registra el HEAD candidato y
verifica el blob Git de cada fuente consumida frente a su HEAD. Registra también
los blobs de este test auxiliar, el script y este README.

Las salidas incluyen `SOURCE-HEADS.txt`, `SOURCE-BLOBS.txt`, versiones, hashes de
herramientas, `compile-red.log`, `junit-red.log`, `compile-green.log`,
`junit-green.log` y `RESULT.txt`. Los logs históricos de autor se conservan en
`../../evidence/inherited-p2/`; esta reproducción genera evidencia nueva y no los
reescribe. Este script portable se entrega para la auditoría; su presencia no
afirma que un tercero ya lo haya ejecutado.

El `BuildConfig` del helper puro es un stub explícito con fusible real `false`.
No sustituye las pruebas Gradle de las cuatro variantes, Android, SQLite nativo
o TDLib. No crea cliente, cuenta, sesión ni llamada de envío real.

**Límite de P2-9B:** una relectura seguida de un envío no es una transacción
Telegram. El helper acredita la regresión sintética de encabezado ausente y las
guardas de callbacks; no demuestra atomicidad del servidor ni ausencia absoluta
de fotos huérfanas. El caso de borrado después de queue conserva UNKNOWN y no
ejecuta rollback, encabezado sustituto ni reintento. La activación real permanece
bloqueada hasta revisión independiente y autorización propia. Álbum vivo HOLD,
salida real compilada OFF.
