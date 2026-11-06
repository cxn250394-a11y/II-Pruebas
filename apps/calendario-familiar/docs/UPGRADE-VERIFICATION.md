# Verificación de actualización y respaldo · Calendario Familiar v0.3

Estado de esta evidencia: CÓDIGO_SIN_VALIDAR hasta que CI ejecute el HEAD que contiene los cambios.
No se ha ejecutado aquí una migración entre dos APK, el selector de archivos de Android, TalkBack, HyperOS ni un teléfono físico.

## Base y decisiones consultadas

- Calendario base: PR #26, commit `0c8a61e69ac16ce11978fba94ab5ca247817e293`.
- APK v0.1 original: PR #22, commit `208b705cc5708bbb16d32b44ab66e8c4d87dceb7`, paquete `mx.charly.calendario`.
- Beta paralela: paquete `mx.charly.calendario.terra`. Un paquete diferente no recibe los datos privados de la instalación original.
- Misión [Terra F2](https://github.com/cxn250394-a11y/II-Pruebas/blob/aa9b0f1a6876f70c03d4030ad4ab5cf6e106e44d/coordinacion/misiones/TERRA61-II-PRUEBAS-MEGA-20261027.md).
- Misión [Workito C2](https://github.com/cxn250394-a11y/II-Pruebas/blob/0aa77bfad255be6ef465521422cf69d5212292c6/coordinacion/misiones/WORKITO-II-PRUEBAS-SIETE-APPS-20261027.md).
- [Auditoría independiente de la base](https://github.com/cxn250394-a11y/II-Pruebas/pull/26#issuecomment-6447319185): JVM, APK y emulador del HEAD base aprobados; restauración real entre instalaciones y accesibilidad pendientes. Ese PASS no se hereda a v0.3.

## Prueba añadida y alcance exacto

`BackupUpgradeTest` crea directamente una base SQLite versión 1 usando las definiciones originales de las cuatro tablas y el índice de eventos, sin llamar al `onCreate` del código nuevo. Contiene solo datos sintéticos: dos perfiles, categorías propias, citas diferentes en la misma fecha, horas/notas, colores y tamaños de fuente distintos.

1. Abre esa base con el `CalStore` nuevo, exige versión 2 y la tabla `paint_undo`, y compara los datos originales de ambos perfiles. Cierra/reabre la base y vuelve a compararlos.
2. Exporta los registros migrados, cifra, exige cabecera CFAM1, cambia citas/categoría/colores/fuentes a valores centinela, descifra e importa y exige igualdad del contenido portable original. Cierra/reabre y verifica de nuevo referencias, IDs y valores.

Es una prueba Robolectric del esquema y del circuito Java/SQLite de respaldo. Usa la misma base de laboratorio para el round trip, con valores centinela para detectar una importación sin efecto. No ejecuta la APK v0.1, no prueba instalación o actualización firmada, no navega DocumentsUI y no sustituye el gate C2 entre dos aplicaciones.

Comando de ejecución, pendiente de resultado en CI:

```sh
gradle --no-daemon :app:testDebugUnitTest --tests mx.charly.calendario.BackupUpgradeTest
```

Directorio: `apps/calendario-familiar`. El resultado válido debe citar el HEAD exacto y los XML de JUnit. La existencia del archivo de test no implica PASS.

## Gate pendiente: exportación/importación real entre dos APK

Estrategia sin cuentas, secretos ni cambios de permisos o código de producción:

1. En CI, hacer un segundo checkout del commit v0.1 fijado arriba en un subdirectorio independiente; construir su APK con Java 17/Gradle 8.9/SDK35 sin modificar sus fuentes. Compilar la APK nueva desde su propio HEAD. Registrar por separado SOURCE-COMMIT, applicationId, certificado y SHA-256 de cada APK interna.
2. Instalar ambas en un emulador desechable API35 sin cuentas. Exigir serial `emulator-*` y propiedad `ro.kernel.qemu=1` antes de cualquier borrado de datos. No borrar paquetes ni archivos en un teléfono físico.
3. Crear por controles nativos de v0.1 una categoría, cita con hora/nota, color de día y fuente distintivos para cada perfil. Las dos citas deben compartir fecha para demostrar aislamiento. Como el mes v0.1 es Canvas sin nodos individuales de día, obtener el rectángulo de la cuadrícula de UI Automator y calcular la celda del día 1 usando fecha/densidad del emulador; exigir después que el detalle muestre esa fecha.
4. Exportar desde el botón real de v0.1. Escribir una contraseña exclusivamente sintética y usar ACTION_CREATE_DOCUMENT para guardar `qa-v01-source.cfam` en el proveedor local Downloads. Seleccionar el nombre de archivo y Guardar por nodos del selector; no reemplazar este paso por una llamada directa a Backup.
5. Crear datos centinela diferentes en el destino. Desde Restaurar respaldo de la APK nueva, usar ACTION_OPEN_DOCUMENT y elegir exactamente ese archivo local. Verificar el consentimiento/aviso de sustitución de ambas agendas que ofrezca la versión nueva.
6. Force-stop de los paquetes y lectura independiente de cada SQLite debug mediante `run-as`, copiando DB/WAL/SHM cuando existan a directorios temporales separados. Comparar perfiles, categorías, IDs, fechas, títulos, horas, notas, colores y fuentes; exigir que el origen siga intacto. Relanzar destino y mostrar ambos perfiles en día/semana. Capturar XML y pantallas sintéticas.
7. Antes de la importación válida, ensayar contraseña incorrecta y archivo truncado/manipulado; exigir igualdad del estado portable del destino antes/después. Conservar los tests de referencias cruzadas autenticadas y rollback transaccional. Estos casos no se consideran cubiertos por el único round trip nuevo.

### Adaptaciones necesarias del harness

- `ci/ui_guard.py` exige actualmente paquete terra y rechaza diálogos del sistema; no puede usarse sin cambios para v0.1 o DocumentsUI. Parametrizar paquete esperado. Durante pasos explícitos del picker permitir solo el componente de ACTION_CREATE_DOCUMENT/ACTION_OPEN_DOCUMENT resuelto en ese emulador. Fuera de esos pasos mantener rechazo de HOME, apps ajenas y diálogos inesperados. No convertir PID vivo en prueba de pantalla visible.
- Resolver el componente DocumentsUI con PackageManager/ADB; no suponer siempre `com.google.android.documentsui` o `com.android.documentsui`. Navegar exclusivamente Downloads local. Fijar locale de laboratorio para etiquetas o preferir IDs del proveedor y guardar evidencia de la selección.
- Reutilizar helpers de `real_fixture.py` con package/label configurables, desplazamientos obtenidos del tamaño real de pantalla y un máximo de intentos. Guardar capturas/actividad/logcat ante cada fallo.
- v0.1 cambia aliases de fecha y tiene un defecto conocido que puede devolver a HOME. Si hace falta preparar por ADB el alias del día actual antes de lanzar la APK original, documentarlo como preparación exclusiva del emulador y registrar el estado. No parchear la APK ni atribuir a esta preparación una reparación del icono. La APK nueva mantiene el icono dinámico desactivado.
- Dar al job de emulador tiempo suficiente: el fixture actual usa sleeps y cada dump UI cuesta segundos. El circuito adicional puede consumir aproximadamente 5–12 minutos de emulación, más 2–6 minutos de build legado según cachés; son estimaciones, no medidas. La primera integración requiere tiempo para depurar diferencias de DocumentsUI y alias.

## Gate pendiente: borrador, rotación y proceso

Sobre la APK nueva, escribir título, hora, categoría, nota y fecha en un borrador sin guardar. Rotar vertical→horizontal→vertical y exigir igualdad de los campos y una sola cita después de Guardar. Repetir con segundo borrador tras HOME→`am kill`, comprobando que cambió el PID y que la tarea restaurada recupera el contenido. Separar esa prueba de `force-stop`, que tiene semántica distinta; exigir recuperación tras force-stop solo si el contrato de borradores persistentes de la versión nueva lo ofrece. En cancelación explícita, verificar que el borrador no reaparece ni produce un evento.

También hace falta una prueba de recreación mientras DocumentsUI está abierto. La base mantiene la contraseña únicamente en memoria y la borra en `onDestroy`: una actividad recreada puede perderla. La aceptación debe ser una restauración segura o una cancelación clara con reentrada de contraseña, sin pérdida de datos, exportación vacía silenciosa ni contraseña en estado persistente o logs.

## Límites de distribución

No se configuró firma privada estable. Igual applicationId e igual certificado son necesarios para actualizar conservando datos; una firma debug nueva no demuestra compatibilidad con una APK anterior. No recomendar desinstalar una agenda con datos. CFAM1 incluye ambas agendas y la restauración las sustituye: conservar respaldo verificable del destino y del origen antes de una migración real.

Este incremento no hace merge, Release, despliegue ni cambios a SB, SI o Quick Check. La aptitud para teléfono de Mamá/POCO, TalkBack y HyperOS sigue NOT_RUN hasta disponer de evidencia autorizada.
