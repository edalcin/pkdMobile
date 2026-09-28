---
label: wayfinder:map
title: pkdMobile — editor rico de Notas
---

# pkdMobile — editor rico de Notas

## Destination

Um ADR (`docs/adr/0002-…`) que diz como o app edita Notas com formatação (H1–H3, listas, links clicáveis) sem perder o que a PWA escreveu, pronto para implementar num slice.

## Notes

- Domínio: app Android para o PKD (`../pkd`). Glossário em `CONTEXT.md`: usar os termos dele (Nota, Tags, Fila de envio).
- Skills: `grilling` + `domain-modeling` nos tickets de grilling; `prototype` no ticket de prototype; `research` nos tickets de research.
- Uma pergunta por vez, ao conversar com o usuário. Respostas em português (ASD-STE100).
- Tracker: markdown local. Tickets em `docs/wayfinder/editor/tickets/`. Front matter: `type`, `status` (open/closed), `assignee` (claim), `blocked_by`.
- Screenshots e imagens de teste vão para `C:\Users\EDalcin\Desktop\OMPtemp`, nunca para o repositório.
- Não criar branches (regra global: tudo no `main`). Resultados de research ficam em `docs/research/`.

Decisões de charting (2026-09-28, com o usuário):
- Este mapa cobre só o editor. Apagar Nota, seletor de Tags e `#android` no share são feitos fora do mapa.
- **Preservar tudo:** WebView + TipTap com as mesmas extensões da PWA. Tabela, highlight, imagem e Mermaid feitos na PWA ficam intactos quando o app salva.
- **Título:** campo separado acima do editor, como na PWA. O PKD exige título (`400` se vazio; `409` se o `PATCH` repete um título). Na criação sem título, o app deriva o título das primeiras palavras do corpo (~60 caracteres). Corpo e título vazios: a Nota não é criada.
- **Link:** sempre em edição; tocar num link mostra o balão "Abrir · Editar · Remover".
- **Barra (inserir):** H1 · H2 · H3 · bullets · lista numerada · checklist · negrito · itálico · link · desfazer/refazer, acima do teclado, com rolagem horizontal.
- **Origem do bundle:** o PKD gera o bundle do editor (uma fonte de verdade para as extensões). O formato exato fica para o research.

## Decisions so far

<!-- uma linha por ticket fechado -->
- [Como o PKD gera um bundle do editor para o app?](tickets/01-bundle-editor.md): extensões TipTap são vanilla JS reaproveitáveis; bundle vira 2ª entrada HTML no Vite com Mermaid lazy; entrega/versão via checkout do `pkd` no CI do `pkdMobile` pinado na tag `:stable`; `SanitizeEditorHTML` hoje perde alinhamento de texto e merge de célula de tabela.
- [Como o editor se comporta no celular?](tickets/02-prototype-editor.md): barra nativa Compose acima do teclado; DocLink só mostra o balão ("Abrir" abre no app, `onOpen` injetável); teclado não sobe ao tocar num link; edge-to-edge exige `statusBarsPadding` + `imePadding` na raiz; bundle 148 KB gzip.

## Not yet specified

(vazio: a ponte Kotlin ↔ JS e o salvamento na fila de envio ficam no ticket **Qual é o ADR do editor rico?**)

## Out of scope

- Editor rico para Memórias e Documentos: o pedido é só para Notas.
- Inserir tabela, imagem, highlight ou Mermaid no app: ficam só preservados (imagem precisa de upload, fora da v1).
