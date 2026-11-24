# ITablet — Punto de control 2026-11-24

## Referencias de recuperación
- Repositorio: Sistemasefa/ITablet.
- Rama funcional: feature/android-direct-sap.
- Commit funcional confirmado por el usuario: 22a691c5e146cef1b0593707ed5374b7c804fd48.
- Documentación funcional anterior: docs/PUNTO_DE_CONTROL_2026-10-29.md (commit 8514a65b66a2f4fcea8d3cb11e9f3e131a350000).
- Rama de preparación Codex: codex/itablet, creada desde feature/android-direct-sap.
- Instrucciones AGENTS.md: commit 0d6722072a1d88bd47da94ca8e69c70315dbc010.

## Estado al cierre
El usuario confirmó que la aplicación y el envío de correo funcionan. No se han solicitado cambios funcionales posteriores. La preparación de Codex añadió documentación e instrucciones en una rama separada; no se ha creado ni conectado el entorno de ejecución en la cuenta de Codex, ni ejecutado compilaciones allí.

## Pendientes y decisiones
- Identificación por lotes: explícitamente en pausa. BatchNumberDetails con WarehouseCode produjo HTTP 400. SQLQueries es una alternativa por investigar, no una solución validada.
- Estética: pendiente, sin cambios autorizados.
- Siguiente paso cuando el usuario lo indique: abrir Codex, conectar Sistemasefa/ITablet, seleccionar codex/itablet como base si está disponible, preparar SDK/JDK según Gradle y comprobar compilación sin cambiar lógica. Solicitar captura de configuración si es necesario.
- SAP privado requiere pruebas en red corporativa; no asumir acceso desde Codex remoto.

## Protocolo contra pérdida de contexto
Leer AGENTS.md, este documento y el checkpoint funcional anterior antes de editar. Inspeccionar el código real y verificar rama y commit. No mezclar con VMobile. Mantener la rama funcional intacta hasta aprobación del usuario; no reiniciar desarrollo ni cambiar credenciales o certificado SAP. Registrar cambios, pruebas realmente ejecutadas y pendientes en futuros checkpoints. No reanudar lotes automáticamente. La documentación complementa Git, no sustituye el código fuente ni garantiza memoria de conversación.
