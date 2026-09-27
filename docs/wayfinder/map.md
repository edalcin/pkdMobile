---
label: wayfinder:map
title: pkdMobile — da ideia à spec da v1 Android
---

# pkdMobile — da ideia à spec da v1 Android

## Destination

Direção visual escolhida, um ADR com a stack e a spec da v1 (telas, escopo, auth, offline, share), pronta para passar à implementação.

## Notes

- Domínio: app Android para o PKD (`../pkd`, backend Go + PWA Svelte 5/TipTap 3). Glossário em `CONTEXT.md`: usar os termos dele (Documento, Nota, Memória, Árvore, Associações, Tags).
- Skills: `grilling` + `domain-modeling` em todo ticket de grilling; `prototype` no ticket de mockup; `research` nos tickets de research.
- Uma pergunta por vez, ao conversar com o usuário. Respostas em português (ASD-STE100).
- Atualizar `CONTEXT.md` quando um termo do glossário mudar. Decisões ficam só nos tickets; ADRs em `docs/adr/`.
- Tracker: markdown local. Tickets em `docs/wayfinder/tickets/`. Front matter: `type`, `status` (open/closed), `assignee` (claim), `blocked_by`.
- Screenshots e imagens de teste vão para `C:\Users\EDalcin\Desktop\OMPtemp`, nunca para o repositório.
- Não criar branches (regra global: tudo no `main`). Resultados de research ficam em `docs/research/`.

Decisões de charting (antes do mapa):
- Escopo da v1: Notas (criar, ver, editar, buscar); Memórias (criar, ver); Documentos (só leitura, busca e Árvore).
- Share intent cria uma Nota por padrão; ela pode ser convertida depois.
- Stack não está decidida. Candidatas: Kotlin/Compose, Flutter, Capacitor + Svelte, PWA/TWA melhorada.
- Mockup: 3 direções (A · Notas-first, B · Espelho da PWA, C · Busca-first), HTML estático clicável com moldura de celular, claro/escuro.

## Decisions so far

<!-- uma linha por ticket fechado -->

- [Como as stacks candidatas atendem os requisitos da v1?](tickets/02-stacks-candidatas.md): Compose tem tudo first-party, mas custo alto; Capacitor e TWA reaproveitam o Svelte; Capacitor tem risco de origem/CORS; TWA tem o menor custo, mas offline fraco.
- [Qual direção visual o app segue?](tickets/01-direcao-visual.md): A · Notas-first — feed de Notas com FAB e barra inferior (Notas · Memórias · Documentos · Busca); [prototype](prototypes/01-direcao-visual.prototype.html).
- [Como o app autentica no PKD?](tickets/03-autenticacao.md): login por sessão (senha + 2FA, cookies cifrados, sem guardar senha), só HTTPS, um servidor, biometria opcional, botão "Encerrar as outras sessões" no PKD.
- [O que funciona offline e como os conflitos são resolvidos?](tickets/04-offline.md): cache de Notas, Memórias, Árvore e Documentos abertos; fila de envio para Notas e Memórias; a última escrita vence; atualização em primeiro plano; busca offline no cache.
- [Qual stack implementa a v1?](tickets/06-adr-stack.md): Kotlin + Compose, APK no GitHub Releases (Obtainium), minSdk 31, tokens do PKD; [ADR 0001](../adr/0001-stack-kotlin-compose.md).
- [A PWA também passa a criar Nota no share?](tickets/05-share-pwa.md): sim — PWA e app criam Nota `#captura` por `/api/capture` (com `idempotency_key` e Open Graph); os Documentos `#captura` antigos ficam.
- [Qual é a spec da v1?](tickets/07-spec-v1.md): [spec](../spec-v1.md) montada; Arquivos só ver/baixar, sem notificações, Nota com Tags e favorita. **Destino alcançado.**

## Not yet specified

(vazio — o mapa chegou ao destino)

## Out of scope

- Edição rica de Documentos (TipTap): fica para depois da v1 (decisão de escopo no charting).
- Graph View, Chat com documentos e Administração (backup, restauração): fora do escopo da v1.
- Múltiplas instâncias PKD (mais de um servidor ou conta): a v1 usa um servidor e uma sessão ([Como o app autentica no PKD?](tickets/03-autenticacao.md)).
- Upload de arquivos e câmera: v1 só vê e baixa Arquivos ([Qual é a spec da v1?](tickets/07-spec-v1.md)).
- Notificações: sem valor claro para a v1 ([Qual é a spec da v1?](tickets/07-spec-v1.md)).
- Converter e apagar Nota: continuam na PWA ([Qual é a spec da v1?](tickets/07-spec-v1.md)).
