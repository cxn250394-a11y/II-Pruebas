# Preflight local — LAB 0.2

Base de trabajo: main `12cc1c7ea0d2717190ac9a2eed2a0bd9bc4ed4e1`.

Ejecutado en el contenedor de desarrollo, sin acceso a dispositivos del usuario:

- `bash scripts/test-lab-core.sh`: **55/55 PASS**, reglas puras Kotlin.
- `python3 scripts/check-lab.py`: **PASS**, manifiesto, recursos XML, applicationId independiente, sin Internet, sin permisos runtime y sin integraciones externas.
- `bash apps/centro-de-alertas/scripts/test-core.sh`: **20/20 PASS**, regresión de reglas v0.1 (no prueba de la UI antigua).

Estas pruebas NO equivalen a compilación Android, instalación, pruebas de entrega real ni rendimiento HyperOS. La compilación Android y las pruebas Robolectric se ejecutan por separado en el workflow del PR; consultar su estado y sus artefactos.

No se tocó `apps/centro-de-alertas/`, el workflow original, otros repositorios ni RS1000. Solo se añaden el LAB, un núcleo local, tests, documentación y CI con permiso read-only. No merge ni deploy.
