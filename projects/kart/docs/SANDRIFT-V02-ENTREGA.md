# Sandrift Karts v0.2 beta · entrega de Grok Bot (GROK-SANDRIFT-01)

**Base:** `feature/five-products-alpha` @ `98cff714738cbff71f67a3b4eac24dc7fe597ac5`. **Rama:** `grok/sandrift-karts-v02-20261023`. Draft PR → `feature/five-products-alpha`. Sin merge, deploy ni Release.
**Autoría:** Grok Bot. **No se autorratifica:** la revisión es de ChatGPT/Torre.

## Qué cambió (archivos)

| Archivo | Rol |
|---|---|
| `projects/logic/.../Race.kt` | Motor simulable, sin libGDX: física, colisiones, objetos, IA, vueltas, copa (`Cup`), semilla (`Rng`) |
| `projects/logic/.../RaceTest.kt` | 22 pruebas de carrera nuevas (las 6 de `LogicTest` y la de `PropertyTest` siguen pasando sin cambios) |
| `projects/kart/src/main/java/.../SandriftGame.kt` | Render, entrada, menús, pausa, resultados, HUD, minimapa, FPS medidos |
| `World.kt` | Escenario 3D procedural: bordillos, cuadros de meta, cajas, barriles, minas, llama de turbo, chispas |
| `HudLayout.kt`, `ControlSettings.kt`, `PerfMeter.kt` | Lógica pura (testeable en JVM): disposición, sensibilidad/zona muerta, estadísticas de frames |
| `KartActivity.kt`, `AndroidManifest.xml` | `sensorLandscape`, más `configChanges`; extra `autostart` solo para la prueba sintética |
| `projects/kart/build.gradle` | versionCode 2, versionName `0.2.0-beta` (solo este módulo) |
| `projects/kart/src/test/.../KartPureTest.kt` | 7 pruebas JVM: 5 proporciones de pantalla, controles, medidor |
| `projects/kart/ci/sandrift-smoke.sh`, `.github/workflows/sandrift-karts-v02.yml` | CI dedicado: tests, lint, APK, SHA-256, SOURCE-COMMIT, emulador API 35 |

No se tocan StatsDeck, AudioDeck, CanalDeck, OddsDeck, `ui`, `build.gradle` raíz, `five-products.yml`, Super Bet ni RS1000.

## Funciones: hechas / no hechas

| Objetivo | Estado | Prueba |
|---|---|---|
| Acelerador/freno reales (GAS, FRENO, inercia al soltar) | Hecho | `throttleAcceleratesAndCoastingSlows` |
| Tope de velocidad por vehículo incluso con turbos apilados | Hecho | `speedNeverExceedsVehicleCapEvenWithStackedBoosts` |
| Derrape graduado: 3 niveles (azul 0.6 s, naranja 1.3 s, violeta 2.1 s) → miniturbo 0.7/1.2/1.8 s | Hecho | `driftChargeGivesTieredMiniTurbo`, `driftDoesNotChargeWhenSlowOrStraight` |
| Feedback visual del derrape (barra y chispas del color del nivel, botón teñido) y del turbo (llama, FOV) | Hecho | Visual; sin prueba automática |
| Muro lateral a ±7.5 m: rebote amortiguado y pérdida de velocidad, sin saltos | Hecho | `wallStopsKartAndCostsSpeedWithoutExplosion` |
| Obstáculos (barriles) con respuesta acotada; el escudo evita la frenada | Hecho | `obstacleSlowsButDoesNotTeleport`, `shieldIgnoresObstacleSlowdown` |
| Choques kart-kart con empuje lateral acotado | Hecho | Cubierto por la invariante `speedAndPositionStayFiniteUnderHostileInput` |
| Cajas de objeto (9 por circuito), reaparición a 4 s, un solo objeto a la vez | Hecho | `itemBoxGrantsOneItemAndRespawns`, `usingItemConsumesItExactlyOnce` |
| Objetos originales: TURBO, ESCUDO (defensivo, expira), PULSO (ofensivo, al más cercano por delante), MINA (ofensivo, expira a 12 s) | Hecho | `shieldExpires`, `pulseStunsNearestRivalAheadAndShieldBlocks`, `mineHitsFollowerAndExpires` |
| IA: línea de carrera, esquiva barriles, usa objetos, rubber-band acotado (±1.5 m/s) | Hecho | `aiFinishesAtAPlausiblePace` |
| Vueltas 1→3 y meta; clasificación coherente | Hecho | `lapsCountUpToThreeAndFinish`, `standingsAndRankAgree` |
| Copa 3 circuitos 10-7-5-3-1, desempate por la última carrera | Hecho | `cupScoring`, `cupRejectsBadOrder` |
| Contrarreloj sin rivales + récord guardado | Hecho | `timeTrialHasOnlyThePlayer` |
| Carrera determinista con semilla | Hecho | `seededRacesAreDeterministicAndSeedMatters` |
| Pausa real (el motor no avanza ni usa objetos) | Hecho | `pausedRaceDoesNotAdvance`; segundo plano → pausa automática |
| Circuito mejorado: DUNAS DEL SOL con desnivel real (> 8 m) | Hecho | `dunesCircuitHasRealElevation` |
| Volante táctil izq/der con rampa, inclinación alternativa, sensibilidad y zona muerta guardadas | Hecho | `deadZoneAndSensitivity`, `settingsRoundTripAndClamp`, `buttonSteeringRampsAndReturns` |
| HUD que no tapa la pista, botones ≥ 90 px virtuales, en 20:9, 16:9, 16:10 y 4:3 | Hecho (cálculo) | `raceControlsAreLargeInsideScreenAndDoNotOverlap`, `menuAndPauseButtonsFitEveryScreen` |
| FPS visible + log `SandriftPerf` (fps medio, p95, peor frame, tirones) | Hecho | `perfMeterReportsMeasuredNumbers` |
| 60 FPS sostenidos en POCO F7 | **NO_VERIFICADO** | Falta medir en el teléfono |
| Sonido/música | **No hecho** | Sin audio en v0.2 |
| Modelos/texturas de calidad comercial | **No hecho** | Primitivas procedurales |

## Antes / después (comprobado por pruebas o por código)

| Aspecto | Alfa (98cff71) | v0.2 |
|---|---|---|
| Acelerador | Automático, sin botón | GAS/FRENO; al soltar, inercia |
| Borde | Se recorta el carril a ±8 sin coste | Muro a ±7.5: rebote y −20 % de velocidad (prueba) |
| Derrape | Binario: el turbo = la carga (> 0.6 s) | 3 niveles con duración fija y feedback por color |
| Objetos | Uno cada 180 m al pasar por el centro, sin cajas | Cajas físicas con reaparición, sorteo por posición, IA también usa objetos |
| IA | Seno fijo, ignora todo | Línea, esquiva, objetos, rubber-band acotado |
| Copa | Solo puntos del jugador | `Cup` con 5 pilotos y desempate |
| Pausa | Solo bandera en UI | `Race.paused` probado en el motor |
| Pruebas de carrera | 7 | 7 + 22 de motor + 7 JVM del módulo kart |
| Mutaciones | — | 6 mutantes manuales en Race.kt (muro, tope de velocidad, pausa, reaparición de caja, doble objeto, nivel de derrape): 6 detectados. El del tope sobrevivía al principio y se añadió una aserción |

## Instalación en POCO F7 (HyperOS 3)

1. En el Draft PR abre la ejecución verde de **"Sandrift Karts v0.2 | APK, tests, emulator"** → Artifacts → `Sandrift-Karts-v0.2-beta-apk` (zip con la APK, `SHA256SUMS.txt`, `SOURCE-COMMIT.txt`).
2. Comprueba el SHA-256 con el del artefacto. Copia la APK al teléfono y ábrela desde Archivos. HyperOS pedirá "permitir instalar apps de esta fuente".
3. **Firma debug temporal:** si tienes la v0.1 instalada con otra firma debug, puede que haya que desinstalarla primero. No se promete actualizar sobre una firma distinta.
4. La app no pide permisos de red ni otros: sin compras, anuncios, cuentas ni servidores.

**Controles:** `<` y `>` (izquierda) giran; **GAS** (abajo a la derecha) acelera; **FRENO** encima; **DERRAPE** a su izquierda: mantén en curva y suelta para el miniturbo. La caja de objeto está encima del volante. **PAUSA** arriba a la derecha (o el botón Atrás). En el menú: BOTONES/INCLINACION, Sensibilidad ±, Zona muerta ±. Esos ajustes se guardan.

**Medir FPS en el teléfono:** `adb logcat -s SandriftPerf` durante una carrera imprime cada 5 s `avgFps`, `p95ms`, `worstMs` y `hitches`. Sin ese log no hay afirmación de 60 FPS.

## Limitaciones para acercarse a calidad comercial (sin imitar recursos ajenos)

- Gráficos con primitivas planas sin texturas, sombras reales ni postprocesado. Haría falta un pipeline propio de modelos glTF con licencia propia o CC0.
- Sin audio. Se necesitan efectos y música originales o CC0, con atribución.
- La pista es una curva cerrada paramétrica y el carril es un desplazamiento lateral, no una física 3D libre: no hay saltos, atajos ni rampas.
- Las colisiones son en 2D (distancia y carril), no por malla.
- La IA no planifica adelantamientos y solo hay 3 circuitos y 3 vehículos.
- Sin fantasma en contrarreloj, sin multijugador y sin mando físico probado.
- Lint: 0 errores y 2 avisos heredados (orientación fija en Android 16, `allowBackup` sin `dataExtractionRules`).
- El rendimiento del emulador de CI (swiftshader, software) **no** representa al POCO F7.

## Recursos y originalidad

Todo se genera por código con primitivas de libGDX (Apache 2.0, ya en el proyecto). Los nombres de circuitos, pilotos y objetos y los colores son propios. No incluye modelos, texturas, música, personajes, marcas ni trazados de Beach Buggy Racing 2, Mario Kart 64 ni otro juego comercial.
