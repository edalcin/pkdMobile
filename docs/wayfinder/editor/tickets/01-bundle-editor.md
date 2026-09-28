---
title: Como o PKD gera um bundle do editor para o app?
type: wayfinder:research
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: []
---

## Question

O app vai editar Notas numa WebView com TipTap, com as mesmas extensões da PWA (StarterKit + Link, TaskList, Table, Highlight, TextAlign e as próprias `DocLink`, `ResizableImage`, `MermaidCodeBlock`, em `../pkd/frontend/src/lib/components/Editor.svelte`). O bundle vem do PKD (uma fonte de verdade).

Pesquisar no `../pkd/frontend` (Vite + Svelte 5, TipTap 3):
1. As extensões dependem de Svelte ou do resto da PWA (stores, API, CSS)? O que precisa sair de `Editor.svelte` para um módulo reaproveitável.
2. Como gerar um bundle separado (entry extra no Vite, modo lib, ou página HTML) que funcione em `file:///android_asset/` sem rede. Tamanho estimado (Mermaid é grande: dá para carregar só quando há um bloco Mermaid?).
3. Como o bundle chega ao APK: artefato do CI do PKD (release/asset), cópia no repo do pkdMobile, ou build no CI do pkdMobile com checkout do PKD. Como a versão do bundle fica ligada à versão do PKD em produção.
4. O HTML que o editor gera passa pelo `SanitizeEditorHTML` (`../pkd/internal/security/sanitize.go`) sem perda?

## Resolution

Data: 2026-09-28. Research completo em [`../../../research/bundle-editor.md`](../../../research/bundle-editor.md).

- Extensões TipTap são vanilla JS (sem Svelte); só `apiGet` (busca `[[`) e `window.location.hash` (clique em DocLink) precisam virar callback injetável para sair de `Editor.svelte`.
- Bundle separado = segunda entrada HTML no Vite (`base: './'`), não modo lib; piso medido ~415 KB/132 KB gzip (TipTap) sem Mermaid.
- Mermaid deve virar `import()` dinâmico (hoje é import estático) para só carregar quando a Nota tem bloco Mermaid — o pacote já faz code-splitting por tipo de diagrama internamente.
- Nenhuma das 3 formas de entrega do bundle existe hoje (CI do pkd, cópia no repo, checkout no CI do pkdMobile). Recomendado: checkout do `pkd` no CI do `pkdMobile`, pinado na última tag semver promovida a `:stable`.
- `SanitizeEditorHTML` **perde** alinhamento de texto (`text-align`, bug real e alcançável hoje) e mesclagem de célula de tabela (`colspan`/`rowspan`, hoje inalcançável pela UI) — checklist (TaskList) sobrevive ao round-trip apesar da aparência.
