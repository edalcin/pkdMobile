---
status: accepted
---

# Editor rico de Notas: WebView com o TipTap do PKD, bundle vendorizado

O app edita o corpo de uma Nota numa **WebView com o TipTap e as mesmas extensões da PWA**. O PKD guarda o corpo como HTML do TipTap, e só o TipTap faz esse round-trip sem perda: tabela, highlight, imagem, checklist, DocLink e Mermaid feitos na PWA ficam intactos quando o app salva. O app só **insere** H1–H3, bullets, lista numerada, checklist, negrito, itálico e link. O resto fica só preservado. Isto substitui o limite da v1 "o app edita a Nota como texto simples". Base: [research do bundle](../research/bundle-editor.md) e [prototype](../wayfinder/editor/tickets/02-prototype-editor.md).

## Forma

- **Tela:** o título é um campo Compose separado, acima da WebView. O PKD exige título: na criação sem título, o app usa as primeiras palavras do corpo (~60 caracteres). Corpo e título vazios: a Nota não é criada. Depois da criação, o app nunca manda título vazio.
- **Barra de formatação nativa (Compose)**, com Boxicons, acima do teclado (`imePadding`). O estado ativo de cada botão vem do JS pela ponte. Com `enableEdgeToEdge()`, a raiz Compose precisa de `statusBarsPadding()` e `imePadding()`, para a WebView encolher com o teclado.
- **Links:** o editor fica sempre em modo de edição. Tocar num link (externo ou DocLink) mostra o balão "Abrir · Editar · Remover", sem subir o teclado (`inputmode="none"` enquanto o balão está aberto). "Abrir" num link externo abre o navegador. "Abrir" num DocLink abre o Documento ou a Nota no app.
- **Ponte Kotlin ↔ JS:** o JS manda o `getHTML()` ao Kotlin em **toda** transação do editor. O Kotlin guarda o último HTML e mantém o fluxo de salvar da v1: 1 s de debounce + salvar ao sair da tela → `Notes.edit` → fila de envio. Assim, sair da tela não perde nada, sem esperar o `evaluateJavascript`.
- **Onde o editor entra:** detalhe da Nota, FAB "Nova Nota" e "Recriar" em Não enviados. O **share continua com texto simples**: é uma captura rápida pelo `/api/capture`.
- **Proteção contra perda silenciosa:** depois do `setContent`, o JS compara o texto puro e a contagem de elementos-chave (`img`, `table`, `a`, `li`, `span[data-*]`, `pre`) entre o HTML original e o `getHTML()`. Se algo sumiu (uma extensão que o bundle não conhece), a Nota abre só para leitura, com "Esta Nota tem conteúdo que o app ainda não edita. Edite na PWA." Nada é salvo.

## Origem do bundle

O **PKD gera o bundle**, para as extensões serem uma única fonte de verdade: uma segunda entrada HTML no Vite (`editor.html` + `mountEditor`, `base: './'`), sem Svelte. O bundle é **vendorizado** no pkdMobile: o script `scripts/update-editor-bundle` roda o build no `../pkd`, copia o resultado para `app/src/main/assets/editor/` e grava o SHA do PKD em `PKD_SHA`. O CI do Android não muda. O tamanho medido é ~148 KB gzip sem Mermaid.

**Pré-requisitos no PKD:**
1. A entrada `editor.html` no Vite e um módulo `mountEditor(container, options)` que monta as extensões da PWA.
2. `DocLink` com `onOpen` injetável, no lugar de `window.location.hash`. A busca `[[` também é injetável (callback no lugar de `apiGet`).
3. `mermaid-code-block.js` com `import()` dinâmico, para o núcleo do Mermaid carregar só quando a Nota tem um bloco Mermaid.
4. `SanitizeEditorHTML` passa a permitir `text-align: left|center|right|justify` em `p`/`h1–h6` e `colspan`/`rowspan` inteiros em `td`/`th`. Hoje o sanitizer descarta o alinhamento em todo save da PWA.

## Considered Options

- **Editor Compose nativo** (HTML de entrada e saída): o visual é mais nativo, mas ele perderia o que não conhece (tabela, Mermaid, DocLink), ou exigiria abrir essas Notas só para leitura. Foi descartado porque a regra é preservar tudo.
- **Barra de formatação dentro da WebView:** o código Kotlin seria menor, mas o visual é diferente do app, e o posicionamento pelo `visualViewport` se mostrou frágil no prototype.
- **Checkout do `pkd` no CI do Android** (recomendação do research): o bundle sempre segue o SHA pinado, mas o CI fica ~1–2 min mais lento, depende do repo do PKD, e o build local precisaria de `npm ci` no `S:`. A proteção ao abrir cobre o risco de divergência. Trocar para esta opção se a PWA passar a mudar extensões com frequência.
- **Pull do HTML ao sair da tela** (debounce no JS): menos tráfego na ponte, mas a última frase se perderia se a tela fechasse antes da resposta do `evaluateJavascript`.

## Consequences

- Atualizar o editor é um passo manual: quando a PWA mudar extensões, rodar `scripts/update-editor-bundle` e publicar uma versão do app. Até lá, as Notas com conteúdo novo abrem só para leitura.
- O APK cresce ~0,5 MB (bundle sem Mermaid) e mais os chunks do Mermaid.
- Se um push por tecla deixar a digitação lenta em Notas grandes, a correção é um debounce curto no JS, com um flush antes de sair da tela.
- O balão sem teclado (`inputmode="none"`) não foi testado. Se falhar em algum teclado, o app aceita o teclado ao tocar num link.
- Nota cifrada: o `PATCH` responde `409`, e o item vai para Não enviados, como na v1.
