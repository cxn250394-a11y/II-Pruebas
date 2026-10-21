# II Pruebas: proyectos y límites

## Entrega activa: Centro de Alertas LAB 0.2

Dos frentes comparten una APK independiente de la v0.1:

1. **Organización Telegram/Nicegram:** bandeja local con nuevos/favoritos, agrupación por título observado + paquete + perfil Android, carpetas y extracción de mensajes incluidos en `MessagingStyle`. No es un cliente de Telegram, no conoce tu cuenta y no marca mensajes como leídos en el servicio. Chats con títulos iguales pueden quedar agrupados.
2. **Radar de avisos deportivos:** etiquetas conservadoras de gol, córner, tarjetas, penal y VAR/corrección, exclusivamente en fuentes clasificadas como deportivas. Cada fila conserva el texto y la procedencia. No acredita un evento, no crea estadísticas, no suma córners, no genera apuestas y no compra APIs. Un proveedor puede no emitir el aviso o hacerlo tarde; no se inventa lo que falta.

La pantalla de inicio explica el consentimiento y el orden de activación; el demo usa ejemplos ficticios en memoria, sin permisos ni escritura de historial. La v0.1 instalada sigue intacta porque el LAB tiene otro applicationId y otra base privada.

## Proyectos posteriores (no se presentan como terminados)

| Proyecto | Próxima pieza concreta | Condición para declararla válida |
| --- | --- | --- |
| Cliente Telegram tipo Nicegram | Comparar un cliente TDLib con un fork mantenido, y diseñar organización local | api_id propio, sesión consentida, protección de credenciales, sin saltarse restricciones de canales; nunca publicar sesiones |
| Análisis deportivo tipo OddsJam/Action/BetGPT | Modelo de evidencia: mercado, cuota, fuente, hora de observación, vencimiento y resultado | Datos autorizados reales; sin APIs contratadas; sin inferir una cuota a partir de una notificación; no conectado a SB |
| Reproductor alternativo a UAPP | Biblioteca local, reproducción continua y pruebas con archivos propios | Sin catálogos premium desbloqueados; bit-perfect/USB solo tras medir en el DAC y teléfono reales |
| Karts original | Un circuito y vehículo propios, manejo, derrape y un rival en motor 3D | APK jugable, recursos originales, tiempos de frame medidos; no promesa AAA/60 FPS sin evidencia |

## Límites operativos

- Código y CI solo en `cxn250394-a11y/II-Pruebas`.
- Sin conexiones, imports, credenciales ni cambios en Super Bet, Super Investment, Centro de Mando, Info o RS1000.
- Sin terminal remota, despliegues, apuestas, compras, reenvíos ni publicaciones de Telegram.
- El repositorio fue observado como público. Solo código y pruebas sintéticas: nunca avisos reales, tokens, cuentas, keystores, datos personales ni registros del teléfono.
- No se cambió la visibilidad. No hay Releases automáticas para este LAB.
- Desarrollo en rama/PR Draft; `main` y la v0.1 se conservan.
- Firma debug temporal: no publicar la clave. Para actualizar LABs futuros sin reinstalación se requiere una firma persistente privada; no se promete compatibilidad de firma aún.

## Referencias de arquitectura

- https://developer.android.com/reference/android/service/notification/NotificationListenerService
- https://developer.android.com/reference/android/app/Notification.MessagingStyle
- https://developer.android.com/develop/ui/views/layout/edge-to-edge
- https://developer.android.com/studio/publish/app-signing
- https://developer.android.com/training/testing/local-tests/robolectric

Robolectric simula Android en JVM. Ni sus pruebas ni Lint sustituyen las pruebas de entrega con pantalla apagada en POCO/HyperOS.
