---
title: O que funciona offline e como os conflitos são resolvidos?
type: wayfinder:grilling
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: []
---

## Question

Quais conteúdos ficam no cache local (Notas, Memórias, Documentos recentes)? É possível criar e editar Notas offline? Quando o aparelho volta a ter conexão, como o app resolve um conflito com uma edição feita na PWA? (A API de Notas é idempotente por `idempotency_key`; o PUT de Documentos tem controle de versão.)

Pergunta surgida no ticket de autenticação: no logout, o app apaga o cache local?

## Resolution

2026-09-27 — Decidido com o usuário (grilling). Fatos do `../pkd`: listas completas sem paginação nem feed de mudanças; `idempotency_key` sem prazo em `POST /api/notes` e `/api/memories` (replay → `200` com a mesma entidade); `PATCH` de Notas/Memórias sem versão; `PUT` de Documentos com `version` e `409`; a PWA hoje só lê offline (sem outbox).

1. **Cache de leitura:** todas as Notas e Memórias, a estrutura da Árvore e os corpos dos Documentos abertos (LRU).
2. **Escrita offline:** criar e editar Notas e Memórias. As mutações entram na fila de envio e saem em ordem; criações levam `idempotency_key`.
3. **Conflito:** a última escrita vence. Sem mudança no backend: o PKD tem um único usuário.
4. **Logout:** apaga cache, fila e cookies. Com fila não vazia, avisa antes e permite cancelar.
5. **Atualização:** em primeiro plano — ao abrir/voltar do background, pull-to-refresh e depois de esvaziar a fila. Sem sync em background na v1.
6. **Busca offline:** busca por texto no cache, com aviso "resultados parciais".
7. **Falhas da fila:** `401` → mantém a fila e pede login; `5xx`/rede → tenta de novo na próxima atualização; `4xx` definitivo → item vai para "Não enviados" (copiar, recriar ou descartar).

Sem ADR.
