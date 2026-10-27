# Terra 6.1 — Inventario comprobado antes de escribir producto (F0)
Verificado 2026-10-27 contra API de GitHub de cxn250394-a11y/II-Pruebas. Repo PUBLIC; main 12cc1c7ea0d2717190ac9a2eed2a0bd9bc4ed4e1. Estos son estados por HEAD, no resultados generales de las marcas.

| App/carril | PR/HEAD fuente | CI relevante | Estado aceptable | Riesgo principal y siguiente acción |
| --- | --- | --- | --- | --- |
| Centro de Alertas LAB | #1 123c355c | CI del PR LAB; no dispositivo real | APTO_CON_LIMITES_OFFLINE solo para pruebas | P1: medición HyperOS/Telegram dual y entrega real pendientes |
| CanalDeck Personal | #15 72db3f98 | 40018123189 SUCCESS (JVM, emulator) | CI_APTO_EMULADOR | P1: firma debug y migración favoritos/sesión; P2: vistas previas y enlaces privados |
| AudioDeck | #16 368438fc | 40021310468 SUCCESS | CI_APTO_EMULADOR | P1: afirmación bit-perfect real sin DAC; TIDAL/Qobuz requieren autorización |
| StatsDeck | #18 6377082f | 40025268638 SUCCESS | CI_APTO_EMULADOR | P1: fuentes de córners/tarjetas y tiempo real sin evidencia |
| OddsDeck | #3 98cff714 / #21 misión 1df45ded | 39684481482 SUCCESS para ALFA; no v0.2 publicada | ALFA, no v0.2 | P1: ausencia de productor V2 + proveedores de precios; mantener evidencia de cuotas |
| Sandrift Karts productor | #19 f6118bf9 | 40034889480 SUCCESS | APK de ensayo, QA parcial | P1: dialogo «Viewing full screen» invalida interpretación de controles/pausa en capturas |
| Sandrift QA strict | #20 1c40a4ad | 40039666002 FAIL emulador | BLOQUEADO | P1: pause-did-not-stop-race; anterior 40036035578 FAIL menú. No borrar test |
| Calendario Familiar | #22 208b705c | 40139330895 SUCCESS (18 tests, emulador) | CI_APTO_EMULADOR | P1: datos/firma debug, P2: validación 70 años/escala/icono HyperOS |

## Diagnóstico transversal

- **P1-SIGN:** 7 apps no tienen firma estable privada configurada y verificada por esta auditoría; no asegurar instalación como actualización de una debug previa. Diseñar contrato y backup antes de entrega para datos permanentes.
- **P1-QA-KART:** el test original de QA de Sandrift no demuestra pausa. El test reforzado sí detecta un fallo, pero necesita distinguir test/interacción de defecto real del juego. No es verde.
- **P1-PRIV:** repo público; screenshots reales y datos personales no son aptos para subir. Pruebas solo ficticias, no citas de IMSS reales ni capturas de canal privado.
- **P1-DATA-SPORTS:** StatsDeck trabaja con observaciones; OddsDeck carece de integración API universal. No llamar alertas en vivo a polling manual ni «recomendación enviada» a un pronóstico.
- **P2-UX-FAMILIAR:** el calendario en vista mes necesita inspección con texto a 200 %, TalkBack y tarjetas armoniosas; la captura emulada de inicio vacío no prueba comprensión de una mujer de 70 años.
- **P2-ICON:** icono número de día usa activity aliases, dependiente de caché de launcher; verificar día 31→1 en POCO sin asumir medianoche exacta.

## Separación por productor
- PR #19 (Grok) diferente de PR #20 (QA). Aceptación no heredada.
- PR #21 describe misión de Argon, **sin código OddsDeck 0.2 declarado**.
- PR #5–#13 de Pick$ siguen aislados OFF; nunca tomar esos cambios para la rama Personal.
- Las nuevas tareas Terra parten del **HEAD exacto correspondiente**, no de main ni de una rama aleatoria.

## Acciones autorizadas en esta ronda
1. Publicar mega misión y este informe en PR Draft **solo de coordinación**.
2. Implementar un incremento aislado de Calendario Familiar con su propio PR, CI, Lint y APK de pruebas si las herramientas lo permiten.
3. Intentar fortalecer QA de Sandrift en rama separada sin cambiar el HEAD del productor ni declarar éxito ficticio.
4. No merge, no Release, no cambio de visibilidad ni subida de secretos.
