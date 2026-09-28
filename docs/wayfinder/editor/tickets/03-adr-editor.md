---
title: Qual é o ADR do editor rico?
type: wayfinder:grilling
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: [01-bundle-editor, 02-prototype-editor]
---

## Question

Com o research do bundle e a reação ao protótipo, escrever o ADR 0002: WebView + TipTap vindo do PKD, a ponte Kotlin ↔ JS, o salvamento na fila de envio e os limites. Ele substitui o limite "o app edita a Nota como texto simples".

## Resolution

2026-09-28 — Decidido com o usuário (grilling): [ADR 0002](../../../adr/0002-editor-rico-webview-tiptap.md).

1. **Ponte:** o JS manda o HTML ao Kotlin em toda transação; o fluxo de salvar da v1 fica igual (1 s + `onDispose` → fila de envio).
2. **Bundle vendorizado:** o script `scripts/update-editor-bundle` builda no `../pkd` e copia para `assets/editor/`, com `PKD_SHA`.
3. **PKD:** `SanitizeEditorHTML` passa a aceitar `text-align` e `colspan`/`rowspan` (pré-requisito, com teste).
4. **Onde:** detalhe, FAB e "Recriar" usam o editor rico; o share continua com texto simples.
5. **Proteção:** checagem ao abrir; se o bundle perde conteúdo, a Nota abre só para leitura ("Edite na PWA").
