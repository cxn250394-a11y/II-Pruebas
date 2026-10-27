# F0 independiente — inventario parcial verificable (27-oct-2026)

Repositorio `cxn250394-a11y/II-Pruebas`, público. Fuente: API GitHub PRs y ejecuciones de Actions consultadas sobre SHA exacto; lectura de README, manifiesto, workflow y lista de cambios de cada línea. No se ha ejecutado código de estas líneas; los ZIP de APK y reportes de firma fueron descargados y revisados según la sección posterior. Los nombres de los tests en los workflows describen lo configurado; los trabajos reportan su conclusión, no prueban cada funcionalidad o uso real. Main consultado directamente mediante fetch_commit(main) y compare contra `12cc1c7ea0d2717190ac9a2eed2a0bd9bc4ed4e1`: idéntico, 0 commits de diferencia al momento de consulta. Repetir al cierre de la ejecución completa.

| Producto | PR / HEAD actual consultado | Ejecuciones encontradas en ese HEAD | Evaluación y próximo gate |
|---|---|---|---|
| Alertas LAB | #1 `123c355c51a730381c6c5fbc7a887093426b0a20` | [39632897569](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/39632897569) éxito, build/test/lint; sin job de emulador | P1: recepción real HyperOS/dual y agrupación de títulos ambiguos sin validar; privacidad: filas no cifradas, sin exportación. Offline local; falta prueba real, no APTO_DISPOSITIVO. |
| CanalDeck Personal | #15 `72db3f988523d21174fc8e9afd7856ad38a70d15` | [40018123189](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40018123189) éxito, build y device-tests emulados | P1: sesión, bloqueo/latencia/medios protegidos reales sin evidencia; backup de organización excluye sesión y exige misma cuenta. No confundir conexión con mensaje. Firma estable pendiente. Base cliente #4 `613fb47d62793daaf28277fac58d3da764f0b865`, run [39698478007](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/39698478007) éxito; no demuestra funcionamiento de #15 en dispositivo. Puente #5–#13 OFF. |
| AudioDeck | #16 `368438fce0a4daced729ceb4907eb4810e3520f5` | [40021310468](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40021310468) éxito build/emulador; [40021310470](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40021310470) Five Products falló emulador | P1: reproducción USB bit-perfect no medida en DAC/teléfono; servicios comerciales dependen de autorización. El fallo del segundo run ocurrió al instalar system-image (`Error on ZipFile unknown archive`) antes de ejecutar smoke; no es evidencia de defecto de audio. |
| StatsDeck | #18 `6377082f2e5f6a01ad0a6faec940eba912f58015` | [40025268638](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40025268638) éxito build/emulador; [40025268665](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40025268665) Five Products éxito | P1: eventos con cobertura y timestamp, y fuentes adicionales por verificar; cambios de marcador son observaciones, no goles confirmados. Smoke solo arranque; datos reales no comprobados. |
| OddsDeck alfa | #3 `98cff714738cbff71f67a3b4eac24dc7fe597ac5` | [39684481482](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/39684481482) éxito build/emulador | P1: comparación y valor dependen de fuente, vigencia y probabilidad válida; no hay conexión de cuotas automática probada. #21 `1df45ded714793d83cbd06d8903673578a69ab0f` es misión, no v0.2 entregada. |
| Sandrift productor | #19 `f6118bf9b449a760664f3b5d621735f273c00ddd` | [40034889480](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40034889480) y [40034889256](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40034889256) éxito | P1: QA independiente cuestiona overlay de pantalla completa y falta de verificación de interacción; 60 FPS no medidos en POCO. |
| Sandrift QA | #20 `1c40a4ad5263033005ee0b1b90e9c5925819bda6` | [40039666002](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40039666002) build éxito / emulador falla; [40039665949](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40039665949) Five Products éxito | BLOQUEADO en gate interacción/pausa. La auditoría de #23 atribuye al anterior run `40036035578` menu-tap-did-not-enter-race y al run exacto pause-did-not-stop-race; examinar evidencia para distinguir defecto del juego y del harness. No borrar aserciones. |
| Calendario Familiar | #22 `208b705cc5708bbb16d32b44ab66e8c4d87dceb7` | [40139330895](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40139330895) éxito build/emulador | P1: conservación de datos ante cambio de firma requiere respaldo ensayado; P2: legibilidad real, TalkBack y caché de icono en HyperOS pendientes. CI no prueba uso por persona de 70 años. |

## Lo leído y límites

- LAB: `apps/centro-alertas-lab/README.md`, `app/src/main/AndroidManifest.xml`, `.github/workflows/alertas-lab.yml`. Declara `allowBackup=false`, servicio protegido `BIND_NOTIFICATION_LISTENER_SERVICE`; workflow corre core/JVM, lint, APK y firma, no emulador.
- CanalDeck: `apps/canaldeck/README.md`, `app/src/main/AndroidManifest.xml`, `.github/workflows/canaldeck.yml` a #15. INTERNET, notificaciones y servicio foreground; `allowBackup=false`; pipeline APK + instrumentación emulada sin credenciales. El README contiene nombre de paquete general histórico y aclara después ID exacto v032: verificar Gradle al preparar migración.
- Audio: `projects/audio/README.md`, `projects/audio/src/main/AndroidManifest.xml`, `.github/workflows/audiodeck-v02.yml`. INTERNET, servicio Media3 exportado y foreground; backup Android desactivado. Favoritos JSON, timer acotado a vida del servicio; bandera USB no certifica fidelidad.
- Stats: `projects/stats/README.md`, `projects/stats/src/main/AndroidManifest.xml`, `.github/workflows/statsdeck-v02.yml`. INTERNET; sin backup Android; refresh manual de misma fuente/liga/temporada, N/D; emulador comprueba instalación y apertura, no cobertura en vivo.
- Odds: `projects/README.md`, `projects/odds/src/main/AndroidManifest.xml`, `.github/workflows/five-products.yml` en alfa. Manifiesto sin INTERNET y backup Android desactivado; precios manuales/CSV, EV condicionado. Workflow compila cinco apps y smoke, no convierte misión #21 en producto.
- Kart: `projects/kart/docs/SANDRIFT-V02-ENTREGA.md`, `projects/kart/src/main/AndroidManifest.xml`, `.github/workflows/sandrift-karts-v02.yml` de #20. GLES2, orientación horizontal, backup Android desactivado; workflow prueba lógica/kart, Lint, firma APK y smoke. Afirmaciones de la entrega del productor son autoinforme; el run #20 es rojo.
- Calendario: `apps/calendario-familiar/README.md`, `app/src/main/AndroidManifest.xml`, `.github/workflows/calendario-familiar.yml`. Solo RECEIVE_BOOT_COMPLETED, backup Android desactivado, 31 aliases, cifrado de respaldo declarado; workflow compila, lint, firma y emula. Test de actualización/copia con datos reales y accesibilidad no demostrado por estos pasos.

## Ejecuciones anteriores identificadas fuera del HEAD

- Stats: [40024581943](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40024581943), commit `588ab8ce0de51d18a255873d55b37ee61ae93897`, build éxito y emulador fallido: log `/usr/bin/sh: 1: set: Illegal option -o pipefail`. El run dedicado de HEAD actual 40025268638 pasó ambos trabajos.
- Sandrift QA: [40036035578](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40036035578), commit `7a3f07cadb8e99f8d5bf0e37867f3a86fe4203f8`, build éxito, emulador falló `menu-tap-did-not-enter-race`. [40039666002](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40039666002), commit `1c40a4ad5263033005ee0b1b90e9c5925819bda6`, build éxito, emulador falló `pause-did-not-stop-race`. Estas líneas constan en los logs de jobs 120696556758 y 120708452966. No se atribuye causa de fondo sin prueba adicional.
- Audio: comentario #16 reporta ejecución dedicada anterior fallida por `pipefail` en `/bin/sh`, sin ID en el comentario; identificador exacto pendiente, no se atribuye al run Five Products 40021310470 (descarga de imagen fallida).

No hay herramienta disponible para listar directamente ejecuciones por rama en orden y sin filtro de commit; los dos ejemplos previos fueron identificados en documentación y comprobados con jobs/logs, no se asegura que sean los dos últimos de su rama. El resto sigue pendiente.

## Seguridad y firma

P1 transversal: los workflows producen APK debug y verifican certificados; no se ha comprobado certificado persistente o compatibilidad entre APK previas. Actualización con datos requiere mismo applicationId y certificado, o respaldo cifrado/restore verificado para migración compatible. No pedir claves en chat; no crear ni modificar secrets sin autorización expresa. No recomendar desinstalar sin respaldo comprobado. Solo datos sintéticos en el repo público. Sin modificaciones a producción, reenvíos ni acceso a cuentas.

## Alcance incompleto del F0

El conector de runs consultado devuelve solo ejecuciones asociadas al commit y solo la primera página de triggers PR: cuando aparece una sola, **no se dispone de la segunda ejecución histórica de la línea**; no confundir dos workflows sobre el mismo HEAD con los dos últimos runs históricos. Faltan lectura detallada de diffs y logs/reportes de todos los jobs, inspección de certificados/APK internos ; se confirmó main actual pero se debe repetir al final. No se asignan P0 sin evidencia; prioridades P1/P2 arriba reflejan riesgos pendientes, no defectos reproducidos. Este inventario es PARCIAL_RECUPERABLE, no dictamen independiente de aptitud.

## Comparaciones de alcance por SHA (API compare)

- LAB vs main: 26 archivos, todos añadidos, en `apps/centro-alertas-lab`, `shared/alerts-core`, scripts, docs/evidence y workflow; no cambios a archivos preexistentes.
- Canal #15 vs `6500123fe912ab913c80f6f79839934c2ab04c9e`: 16 archivos, en `apps/canaldeck/**` y su workflow. Cinco commits por delante.
- Audio vs alfa `98cff714738cbff71f67a3b4eac24dc7fe597ac5`: ocho rutas, audio + workflow + una línea añadida en `projects/build.gradle`.
- Stats vs alfa: siete rutas, stats + workflow + una línea añadida en `projects/build.gradle`.
- Alfa Odds/#3 vs main: 47 rutas añadidas de cinco productos, lógica y UI compartidas y workflow; no es un diff exclusivo de Odds.
- Kart #19 vs alfa: 14 rutas en kart, lógica de carrera y workflow. #20 (`1c40a4ad`) vs productor `f6118bf9`: exactamente un archivo modificado, `projects/kart/ci/sandrift-smoke.sh`, 88 adiciones / 11 eliminaciones. Afirmación solo de alcance, no de corrección.
- Calendario #22 vs main: 50 archivos todos añadidos, únicamente `apps/calendario-familiar/**` y workflow.

Estas cifras son inspección de metadatos de diffs, no auditoría de cada línea de código. Los riesgos funcionales listados antes siguen pendientes de pruebas.

## Segundo run anterior encontrado mediante primer padre

El conector consulta runs asociados a commit con filtro de triggers PR, no ofrece listado cronológico por rama. Se resolvió `HEAD~1` vía API y se consultó ese SHA; cuando no devolvió runs se consultaron los siguientes ancestros de la misma rama hasta la base. Por tanto no se garantiza la ausencia de ejecuciones `push`/manuales ni otros historiales fuera de la primera página.

| Línea | Run previo encontrado al exacto del HEAD, SHA | Resultado |
|---|---|---|
| LAB | [39632187869](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/39632187869), `a4fae8465141bb5d889205a68f6dd73ea841395b` | FAIL tarea `:app:testDebugUnitTest`, causa concreta no aislada en esta inspección. |
| Canal Personal | Ancestros `0bc6094e`, `7492b5dc`, `cec3d8d0`, `8c53fdb4`: sin runs PR en API | Sin segundo run de esta rama disponible; base #14 es otro HEAD/PR, no heredar. |
| Audio | [40020576799](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40020576799), `6cd7ff910ee4d87cf6862e3ca0c62a1aa616b44a` | build OK, emulador FAIL `/usr/bin/sh: Illegal option -o pipefail`. Five Products 40020576480 cancelado. |
| Stats | [40024581943](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40024581943), `588ab8ce0de51d18a255873d55b37ee61ae93897` | build OK, emulador FAIL mismo error de shell; Five Products 40024581720 cancelado. |
| Odds / alfa | [39682778247](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/39682778247), `0e11bf2ee6f71946156bdbb186a568106a289f49` | build FAIL en `:audio:lintDebug`, emulador omitido; no implica fallo de Odds. |
| Kart #19 | Ancestros `d6abe6ee`, `34b072cb`, `fc2cd236`: sin runs PR en API | Sin segundo run de rama disponible; no sustituir por #20. |
| Kart QA #20 | [40036035578](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40036035578), `7a3f07cadb8e99f8d5bf0e37867f3a86fe4203f8` | build OK, emulador FAIL entrada a carrera; HEAD actual también emulador FAIL pausa. |
| Calendario | [40139247332](https://github.com/cxn250394-a11y/II-Pruebas/actions/runs/40139247332), `08d9c4471032a57811ff4c8f3c789641d1f34a62` | build FAIL compilación de tests `cannot find symbol`, emulador omitido; corregido en HEAD posterior que sí reporta éxito. |

## Certificados y hash internos de artefactos disponibles

Se descargaron los siete ZIP de artefactos y su SHA-256 coincide con el digest de la API GitHub; se calculó localmente SHA-256 de cada APK y se leyó `SOURCE-COMMIT.txt` y el reporte `apksigner` de CI dentro del ZIP. No hay `apksigner` instalado localmente, por lo que la validación criptográfica es **la registrada en CI, no una verificación criptográfica repetida localmente**. En todos los reportes se indica certificado Android Debug, un firmante y verificación v2. Los SHA de certificado son huellas públicas, no claves.

| Línea / artefacto | SHA-256 APK interna | SHA-256 certificado de reporte CI |
|---|---|---|
| LAB #1 / 12360498101 | `7cd3c306f0107ecf612356f36d28ae1cb9e69de25897cd5ee1410c191741ae30` | `9d6e6a5f87e0be2ebf1f5f5b3f6278752d767547c74f8d8ca1e50d32de196424` |
| Canal #15 / 12528913815 | `3ee2ed1753757bfec15ea8c0ffed1d8938aa6a75285e582c81823bb6b32206e6` | `9e6ad914413d809a80524fab1db11d90146f469307274d7571945672284836dd` |
| Audio #16 / 12529734513 | `0c8ef7cd04fc9961c8691f3b9eaf803d8a65ab315876c17be9a4f5d4d5d351be` | `4a242ac87b6e1808eed03e2248587fe88fb584990e87c6e77c6540e939379d78` |
| Stats #18 / 12532422627 | `5b285ab75dc5f54f692008a10ff645e49abe9591f6be531477a56c259beffd3f` | `3702621449bd411b18117da1e50afe759fdc8d983eb86d89a263a3f8cd38f2b7` |
| Odds alfa #3 / 12386205662 | `36b008032504b04184a72c994b116b7eb31d985ab47ee782c671c3feab09f62d` | `3c2e0009abc9051095bc7e4b110b2445f022edb007f89858ec1e771fd166c3eb` |
| Kart #19 / 12537075490 | `42ce97952fd1d8df8924304d8c5d0c4c67a887398a9ea20519e74d6e0df26d01` | `63d2d2b24c108fd219e6ce3431f2a3e47c8553186b85a9cf3629c77a00173222` |
| Calendario #22 / 12577984960 | `c4731cdfc2e6210f16ef860f766fb1338ca9090a374f3af29cad1324bdbb9885` | `dd6c57cad6c2f310376a919100f941e4454ad8626cccc27f3f77580c4cc7aaea` |

Los `SOURCE-COMMIT.txt` de cada paquete corresponden a los HEAD listados en tabla inicial. Por ejemplo, alfa #3 y sus APK de Audio/Stats/Kart comparten certificado `3c2e...c3eb`, mientras los artefactos posteriores de esas líneas tienen certificados diferentes; ello impide asumir actualización directa incluso si applicationId coincide. La sola coincidencia de huella de certificado tampoco sustituye comprobar aplicación ID, versión y una actualización con datos en el dispositivo.
