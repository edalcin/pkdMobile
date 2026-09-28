---
title: Como o editor se comporta no celular?
type: wayfinder:prototype
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: [01-bundle-editor]
---

## Question

Com o bundle do editor numa WebView do app (build de debug, emulador), a experiência é boa? Protótipo rápido: campo de título, editor TipTap, barra acima do teclado (H1 · H2 · H3 · bullets · numerada · checklist · negrito · itálico · link · desfazer/refazer) e o balão de link "Abrir · Editar · Remover". Abrir uma Nota da PWA com tabela e imagem e salvar sem perda. O usuário testa e reage.

## Resolution

2026-09-28. O usuário testou o protótipo no emulador ([fonte e instruções](../prototypes/README-editor-prototype.md)). Screenshots ficaram fora do repositório (`OMPtemp`).

Fatos observados:
- TipTap com as extensões do PKD sem mudança roda em `file:///android_asset/`. Bundle: 466 KB / **148 KB gzip** (sem Mermaid; Mermaid foi stub).
- Round-trip no editor (`getHTML()`): tabela, imagem, highlight, checklist, DocLink, Mermaid e alinhamento sobrevivem. O alinhamento se perde depois, no `SanitizeEditorHTML` do PKD (research §4).
- Com `enableEdgeToEdge()`: é preciso `statusBarsPadding()` (a barra de status engolia toques) e `imePadding()` na raiz Compose, para a WebView encolher com o teclado.
- CSS: `[hidden]{display:none !important}`, porque regras `display:flex` vencem o `[hidden]` do navegador.

Decisões (com o usuário):
1. **Barra nativa (Compose)** acima do teclado (`imePadding`), com Boxicons e estado ativo vindo do JS pela ponte. A barra dentro da WebView foi rejeitada: visual diferente do app, posicionamento frágil via `visualViewport`.
2. **DocLink = link externo:** o toque só mostra o balão. "Abrir" abre o Documento ou a Nota no app (cache). A extensão `DocLink` do PKD precisa de um `onOpen` injetável no lugar de `window.location.hash`.
3. **Teclado não sobe ao tocar num link:** o balão aparece sem teclado (`inputmode="none"` enquanto o balão está aberto). O teclado sobe no próximo toque no texto ou em "Editar". Não testado; se falhar em algum teclado, aceitar o teclado.

Não testado: alças de seleção (toque longo) sobre o balão.
