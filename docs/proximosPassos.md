# Próximos Passos

Estado: planejamento (wayfinder). Ainda não há código.

- Mapa: `docs/wayfinder/map.md` (destino, decisões, fog, out of scope).
- Tickets: `docs/wayfinder/tickets/`.
- Fechado: **Qual direção visual o app segue?** → A · Notas-first. Prototype em `docs/wayfinder/prototypes/01-direcao-visual.prototype.html`.
- Research fechada: **Como as stacks candidatas atendem os requisitos da v1?** Resultado em `docs/research/stacks-candidatas.md`.
- Fechado: **Como o app autentica no PKD?** → sessão do PKD, só HTTPS, um servidor, biometria opcional. Pré-requisito no backend do PKD: botão "Encerrar as outras sessões".
- Fechado: **O que funciona offline e como os conflitos são resolvidos?** → cache + fila de envio; a última escrita vence.
- Fechado: **Qual stack implementa a v1?** → Kotlin + Compose, APK no GitHub Releases. ADR em `docs/adr/0001-stack-kotlin-compose.md`.
- Fechado: **A PWA também passa a criar Nota no share?** → sim, Nota `#captura` por `/api/capture`.
- Pré-requisitos no PKD para a v1: botão "Encerrar as outras sessões"; `/api/capture` cria Nota e aceita `idempotency_key`.
- Fronteira: 07 (spec da v1) — último ticket antes do destino.
- Próximo ticket recomendado: **Qual é a spec da v1?** (`07-spec-v1.md`).
