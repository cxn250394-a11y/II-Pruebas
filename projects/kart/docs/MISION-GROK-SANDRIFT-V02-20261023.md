# Misión GROK-SANDRIFT-01 · Kart v0.2 original · 23-oct-2026

**Dueño:** Charly. **Ejecutor invitado:** Grok Bot. **Repositorio:** `cxn250394-a11y/II-Pruebas`.
**Base inmutable:** rama `feature/five-products-alpha`, commit exacto `98cff714738cbff71f67a3b4eac24dc7fe597ac5`.
**Trabajo:** crear **tu propia rama** `grok/sandrift-karts-v02-20261023` desde esa base. Entregar **Draft PR** dirigido a `feature/five-products-alpha` (no `main`). No hacer merge.

## Objetivo

Convertir Sandrift Karts alfa en un kart racer Android **original, más jugable y pulido**, inspirado en las mecánicas generales de Beach Buggy Racing 2 y Mario Kart 64; **no copiar** código, modelos, circuitos, personajes, marcas, música ni recursos protegidos de esos juegos.

**No tratar la alfa como final**: hay un prototipo 3D en libGDX, con carreras, copa, contrarreloj, pilotos, vehículos, controles e ítems. Inspecciona `projects/kart/src/main/java/mx/charly/kart/SandriftGame.kt`, `projects/logic/src/main/kotlin/mx/charly/logic/Race.kt` y pruebas existentes antes de cambiar nada.

## Objetivos priorizados de v0.2

1. **Conducción divertida y comprobable**: aceleración, frenado, límites laterales y derrape graduado; miniturbo calculado por tiempo de derrape, con feedback visual y sin velocidades infinitas. Separar lógica simulable de renderizado.
2. **Colisiones y pistas**: evitar atravesar los bordes de carretera, manejar obstáculos con respuesta controlada, nada de teletransportes o explosiones numéricas. Al menos un circuito 3D mejorado con diferencias visibles.
3. **Rivales y objetos**: IA con trayectoria no imposible, selección de objeto a partir de caja, uso consistente sin duplicar premios; mínimo un tipo ofensivo y uno defensivo originales.
4. **Controles Android**: volante táctil izquierda/derecha + acelerador/freno, alternativa inclinación, sensibilidad y zona muerta guardadas. Interfaz horizontal que no tape la pista.
5. **Menú y carreras**: circuito/vehículo, copa, contrarreloj, reinicio, resultados; manejo seguro de pausa/reanudar y vuelta al menú.
6. **Rendimiento**: optimizaciones medibles y FPS visible; 60 FPS **objetivo**, nunca declarar 60 FPS sostenidos sin logs tomados del POCO F7 o equipo del usuario.

## Requisitos de ingeniería y aceptación

- **Aislamiento:** modificar solo `projects/kart/**`, `projects/logic/src/main/kotlin/mx/charly/logic/Race.kt` y sus pruebas de carrera, más un workflow dedicado `.github/workflows/sandrift-karts-v02.yml` si hace falta. No editar código de AudioDeck, StatsDeck, OddsDeck, CanalDeck ni otros repos.
- **APK instalable real** `Sandrift-Karts-v0.2-beta.apk` firmada en debug para ensayo, no únicamente archivo .zip ni pseudocódigo. GitHub Actions debe adjuntar la APK individual dentro de artefacto y SHA-256 más SOURCE-COMMIT.
- **CI:** compilar Android, ejecutar JUnit del motor, Lint y prueba de instalación/arranque/entrada a carrera en emulador API 35. Mostrar capturas originales de menú, pista y carrera; no usar mocks para afirmar rendimiento.
- **Tests que necesito ver:** invariantes de velocidad y posición finitas, pausa, bordes/colisión, aplicación de boost, expiración/reinicio de objetos, puntuación de copa, conteo de vueltas, carrera determinista con seed cuando sea posible. Evitar tests inertes.
- **UX:** botones grandes, legibles y probados en al menos dos proporciones de pantalla. Comprobar que el juego no se rompe por orientación/cambio a segundo plano.
- **Calidad visual:** recursos propios u open-source compatibles y correctamente atribuidos. Evitar caracteres/vehículos/mapas demasiado parecidos a una IP comercial.
- **Seguridad:** sin compras, anuncios, pagos, apuestas, trackers, permisos innecesarios ni comunicación con servidores. Pruebas sintéticas solamente.
- **Fuente de verdad:** commit y workflow exactos, diferenciar PASS y pendientes. Firma debug temporal: no prometer actualización sobre firma diferente.

## Entregables en Draft PR

- APK generada por CI, enlace a artifact y checksum **interno**.
- Tabla de funciones hechas/no hechas, evidencias de pruebas y errores/advertencias.
- Breve comparación *antes/después* basada en pruebas, no en frases de marketing.
- Guía sencilla de instalación y controles para POCO F7 (HyperOS 3).
- Lista explícita de limitaciones para aproximarse a calidad de juego comercial sin imitar sus recursos.

## Límites operativos

**No merge, no deploy, no Release pública, no RS1000, no Super Bet, no apuestas, no compras, no accesos a celulares/cuentas, sin claves ni tokens en repo.** `main` y los Draft PR anteriores intactos. El repositorio es público; no subir datos personales.

Entrega un resumen de máximo 8 líneas y el enlace al Draft PR para que ChatGPT revise sin que Grok ratifique su propio trabajo.
