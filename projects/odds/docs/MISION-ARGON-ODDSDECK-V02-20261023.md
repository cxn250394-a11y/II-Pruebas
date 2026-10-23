# Misión ARGON-ODDSDECK-01 — OddsDeck v0.2 · inteligencia de cuotas personal
Fecha: 23 de octubre de 2026 · Dueño: Charly · Ejecutor invitado: Gemini Argon (con Jules/Antigravity como ejecutor de repositorio, si hace falta)

## Autoridad de inicio (exacta)
- Repositorio: `cxn250394-a11y/II-Pruebas`.
- Base **inmutable**: rama `feature/five-products-alpha`, commit `98cff714738cbff71f67a3b4eac24dc7fe597ac5`.
- Crear la rama de ejecución `argon/oddsdeck-v02-20261023` **desde esa base exacta**, no desde `main`.
- Al terminar, entregar **Draft PR hacia `feature/five-products-alpha`**, SIN merge, deploy, Releases públicas ni operaciones sobre apuestas.
- Esta rama `mission/argon-oddsdeck-v02-20261023` solo custodia la misión; no editarla ni confundirla con la de implementación.
- Si Argon desde Gemini no dispone de permisos de GitHub, usar **Jules o Antigravity** autorizado por el dueño para escribir, probar y crear el PR. Si tampoco hay conexión, entregar parche reproducible sin afirmar publicación ni APK.

## Encargo
Desarrollar **OddsDeck v0.2**, nuestra aplicación Android independiente que reúne **funciones propias** inspiradas en las experiencias de OddsJam, Action Network, BetGPT, Props.Cash y comparadores de mercados. Queremos interfaz convincente, navegación sencilla, análisis trazable, importación real y cálculos honestos. La app NO debe ser solo mockups o ejemplos ficticios.

**No** tratar como acceso “Premium” a servicios ajenos: el valor premium **propio** será el software de comparaciones, filtros, auditoría y UX. No reproducir marcas/diseños pixel por pixel, contenido protegido, APIs privadas, suscripciones ni datos sin licencia.

### Recupera lo que ya existe, no empieces desde cero
- `projects/odds/src/main/java/mx/charly/odds/OddsActivity.kt`: comparador manual y CSV, mejores precios frescos, filtros de identidad de mercado, calculadora EV y no-vig, registro de boletos y ROI / variación con cuota de cierre.
- `projects/logic/src/main/kotlin/mx/charly/logic/Analysis.kt`: validación de números y CSV, Quote.key estricta, caducidad 120 s, comparación, cálculos.
- `projects/odds/src/test/java/mx/charly/odds/OddsSmokeTest.kt`.
- `projects/README.md` y workflow `.github/workflows/five-products.yml`.

### Alcance obligatorio de v0.2 (terminar, probar y empaquetar)
1. **Radar de cuotas verificables**: lista de eventos/mercados/casas introducidos por el usuario con origen y hora observada (epoch ms), línea, periodo y reglas. Comparar solo **identidades coincidentes exactamente**; jamás equiparar Over 2.5 FT 90min con 1T, extra-time incluido, HA o mercados del mismo nombre con liquidación distinta. Colores de estado Vigente / Vencido / No disponible; duración de validez configurable por usuario **sin revivir cuotas pasadas**.
2. **Historial de movimientos** por selección/casa con múltiples observaciones sucesivas: representar diferencias, hora y procedencia; **no** sobrescribir observación anterior ni usar precio ya reemplazado como “mejor actual”. Mejor precio únicamente entre últimas observaciones frescas. Sustituir estado solo mediante importación confirmada, preservando historial y sin confundir datos ficticios.
3. **Calculadoras transparentes**: probabilidad implícita de cuota, eliminación **proporcional** de margen solo si se conocen todos los resultados excluyentes y exhaustivos, EV **condicionado a probabilidad explícitamente proporcionada por el usuario** (nunca generada sin evidencia), equilibrio y cálculo descriptivo de CLV cuando exista cuota inicial y de cierre verificables con misma identidad. Cuidado: cambio de precio bruto ≠ CLV ajustado por margen, tampoco garantiza EV positivo.
4. **Registro de boletos**: conservar estado ganada/perdida/nula/pendiente, ROI con denominadores correctos y evitar contabilizar pendientes como ganancias. Cualquier importación o borrado debe exigir confirmación; el historial local debe sobrevivir a reinicio y a archivos corruptos sin borrarse silenciosamente.
5. **UX Android**: aspecto oscuro pulido, pestañas o navegación clara (Radar, Historial, Calculadoras, Boletos, Ajustes), filtros por casa/deporte/mercado/origen cuando el campo esté disponible; mensajes amables de estado vacío, carga, fallo, dato vencido y demo **claramente etiquetado** como ficticio. Conservar soporte Android con APK instalable independiente.
6. **Importación práctica**: CSV estructurado existente y, si cabe con pruebas, **imagen elegida manualmente** desde Android con OCR **local** (e.g. ML Kit Latin bundled). La salida OCR debe ir a pantalla editable de revisión, nunca convertir una imagen ambigua en selección “confirmada” por sí sola. Sin captura automática de otras apps, cookies, accesibilidad, scraping, reenvíos o extracción de medios protegidos. Si la OCR no cabe en este incremento, entregarla como puerta posterior explícita, **sin simular que ya funciona**.

### Datos externos / integración con otros proyectos
- **CERO APIs de pago obligatorias**. No declarar cobertura de cuotas en vivo, notificaciones, “surebets” o props si las fuentes reales no están disponibles.
- Se permiten datos manuales y CSV del usuario, así como fuentes abiertas/autorizadas cuyo uso y limitaciones se documenten. Si un proveedor gratuito no entrega líneas en vivo, marcar `NO_DISPONIBLE`, nunca usar datos falsos como si fueran actuales.
- **No modificar ni conectarse a Super Bet 3.0, Ferrari, Pick$, RS1000 ni sus repositorios.** Sin sesiones de casas, scripts de navegador con login, apuestas, pagos, envíos o compras. Cualquier integración futura será propuesta y revisión separada.
- No leer datos personales o historiales reales del dueño; las pruebas usan fixtures sintéticos. Repositorio público: jamás subir API keys, secretos, cookies, cuentas, boletos, imágenes privadas o keystores.

### Aislamiento estricto
Modifica únicamente:
- `projects/odds/**`;
- el **ajuste de versión de `:odds` exclusivamente** en `projects/build.gradle`, solo si es imprescindible;
- un workflow propio `.github/workflows/oddsdeck-v02.yml` si se necesita.
Para lógica nueva, crea archivos **dentro de `projects/odds`**. Evita editar `projects/logic/Analysis.kt` compartido por StatsDeck y otros módulos; si identificas una corrección crítica al legado, repórtala y prepara propuesta separada sin tocarla. No editar `projects/ui/**`, StatsDeck, AudioDeck, CanalDeck, Kart ni ningún código de `main`.

### Pruebas que hacen diferencia
JUnit/Robolectric y pruebas de propiedades con fixtures deben cubrir:
- distintos períodos, líneas, selecciones, reglas, mercado y evento **nunca se fusionan**;
- nuevo precio desplaza la antigua observación de la misma casa aun cuando la cuota antigua era mejor;
- datos expirados, futuros, duplicados, inválidos o sin procedencia **no pasan** a oportunidades vigentes;
- importación CSV truncado/malformado no sustituye datos correctos;
- no-vig rechaza conjuntos incompletos o no excluyentes; una cuota sola nunca genera ventaja matemática;
- EV requiere probabilidad entre 0 y 1 explícita; sin dato devuelve `N/D`, no probabilidad inventada;
- ROI maneja PENDING/VOID sin contar como ganancia; CLV necesita línea/reglas/periodo idénticos;
- reinicio preserva historial/favoritos (mismo paquete y firma), corrupción visible sin destrucción silenciosa;
- demo jamás se mezcla con datos reales;
- OCR solo si implementada: decisiones textuales ambiguas requieren confirmación manual, no compra ni reenvío.
No basta con compilar: pruebas con falsos positivos y correcciones deben fallar donde corresponde.

### Entrega operativa de cada Gate
**G0**: inspección de base, propuesta de arquitectura y riesgos (corta, documentada).
**G1**: lógica + pruebas rojas/verdes y regresión del comportamiento legado.
**G2**: interfaz útil, persistencia/importación, Lint sin errores bloqueantes.
**G3**: CI exacta con JDK/Android SDK, JUnit/Robolectric, APK debug firmada y SHA-256 **del APK interno**, `SOURCE-COMMIT.txt`; emulador API35 con instalación, arranque y navegación de Radar/Calculadoras/Boletos.
**G4**: Draft PR con screenshots del emulador y reporte honesto de lo implementado, lo no implementado y pruebas de teléfono pendientes. No se acepta APK solo citada sin estar en artefacto; no se declara “Premium de OddsJam” desbloqueado.

Nombre sugerido: `OddsDeck-v0.2-beta.apk`.
La versión de la app debe ser distinta de 0.1 alfa. Firma debug CI temporal; **no prometer actualización sin reinstalar** hasta firma persistente privada y mismo applicationId. No publicar clave de firma.

### Preguntas que NO deben bloquear el MVP
Si una API, servicio premium o OCR no es viable, termina el resto con datos manuales y conserva el bloqueo bien etiquetado. Si faltan datos reales, usa CSV de prueba **indicado DEMO FICTICIO**. No pedir al dueño credenciales o suscripciones para completar G1-G3.

## Tu respuesta final a Charly / ChatGPT
Máximo 8 líneas: URL del Draft PR, HEAD exacto, URL del workflow y estado, número de pruebas, estado real de la APK con enlace artifact, funcionalidades efectivas, límites y bloqueos. **Argon no ratifica su propio paquete**; ChatGPT revisará después, sin merges ni despliegues.
