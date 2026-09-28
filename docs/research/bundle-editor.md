# Research: como o PKD gera um bundle do editor para o app?

Ticket: `docs/wayfinder/editor/tickets/01-bundle-editor.md`. Fontes: `pkd/frontend` (Vite 6 + Svelte 5.55 + TipTap 3.30), `pkd/internal/security/sanitize.go`, `pkd/.github/workflows/*`, `pkdMobile/.github/workflows/android.yml`, `pkdMobile/app/build.gradle.kts`.

Convenção de citação: `pkd/caminho:linha` (repositório `S:/git/pkd`) e `pkdMobile/caminho:linha` (este repositório). Afirmações não confirmadas por fonte primária estão marcadas **[incerto]** ou **[estimativa]**.

---

## 1. As extensões dependem de Svelte ou do resto da PWA? O que sai de `Editor.svelte`?

**Boa notícia: TipTap 3 é vanilla JS.** Nenhuma extensão importa Svelte — `Editor.svelte` só faz `new Editor({...})` (API do `@tiptap/core`, não um componente Svelte) — `pkd/frontend/src/lib/components/Editor.svelte:1123-1155`. Um bundle standalone (sem Svelte) é viável.

As 9 extensões da PWA e seu acoplamento:

| Extensão | Arquivo | Acoplamento com a PWA |
|---|---|---|
| StarterKit, Table\*, TaskList/TaskItem, Highlight, TextAlign | pacotes `@tiptap/*` | Nenhum — pacotes npm puros |
| `ResizableImage` | `pkd/frontend/src/lib/editor/resizable-image-extension.js:1-88` | Nenhum — só DOM (`createElement`, listeners) |
| `MermaidCodeBlock` | `pkd/frontend/src/lib/editor/mermaid-code-block.js:1-163` | Lê `document.documentElement.dataset.theme` (linha 9) para tema claro/escuro — convenção da página, não da PWA; o app só precisa espelhar esse atributo no `<html>` da WebView |
| `DocLink` | `pkd/frontend/src/lib/editor/doclink-extension.js:1-76` | **Dois acoplamentos reais**: (a) `addNodeView` faz `window.location.hash = '/doc/' + id'` ao clicar (linha 60) — é o roteador hash da SPA, sem sentido dentro da WebView do app; (b) usa `Suggestion` (`[[`) que chama `buildLinkSuggestion()` → `link-suggestion.js` |
| `link-suggestion.js` | `pkd/frontend/src/lib/editor/link-suggestion.js:8` | `import { apiGet } from '../api.js'` — chama `GET /api/search` direto, com a base URL/cookies da PWA implícitos |

CSS: o `<style>` de `Editor.svelte` tem ~1250 linhas (linhas 1958-3201), quase tudo chrome da PWA (sidebar, diálogos, árvore de documentos). Regra específica de editor encontrada: só `:global(.ProseMirror a)` (linha 2148). Classes das extensões (`.doc-link`, `.resizable-image-wrapper`, `.mermaid-empty`) não têm regra dedicada — herdam estilo do restante da folha. Extrair o CSS do editor exige curadoria manual linha a linha, não é uma extração automática.

**Recomendação**: criar um módulo novo (ex.: `pkd/frontend/src/lib/editor-bundle/`) que exporte (a) a lista de extensões TipTap configurada, (b) uma função `mountEditor(container, options)` em JS puro (API `@tiptap/core`, sem Svelte), (c) o CSS do editor extraído a dedo. Os dois pontos que **precisam** virar callback injetável em vez de import fixo: `apiGet` (`link-suggestion.js:8`) e `window.location.hash` (`doclink-extension.js:60`) — o app injeta sua própria busca de documentos e sua própria navegação (ponte para o Kotlin) no lugar delas.

---

## 2. Como gerar um bundle separado que funcione em `file:///android_asset/` sem rede? Tamanho?

**Mecanismo**: `pkd/frontend/vite.config.js` não define `build.rollupOptions.input` hoje — Vite usa `index.html` como única entrada por padrão. A forma natural de adicionar uma segunda saída é o **multi-page mode nativo do Vite** (`build.rollupOptions.input: { main: 'index.html', editor: 'editor.html' }`, [Vite docs — Multi-Page App](https://vite.dev/guide/build.html#multi-page-app)) com uma página HTML nova cujo `<script type="module">` importe só o módulo do editor — não "modo lib" (lib mode gera um pacote importável, não uma página executável, e a WebView precisa de uma página). Duas mudanças obrigatórias para `file://`: `base: './'` no `vite.config.js` (paths absolutos `/assets/...` quebram sob `file://`) e manter `assetsInlineLimit: 4096` (já existe, `vite.config.js:26` — inlina assets pequenos em base64, sem round-trip de rede).

**Tamanho (build real, medido nesta pesquisa)**:
- Chunk `tiptap` (`@tiptap/core` + `starter-kit` + `extension-image` + `suggestion`, `vite.config.js:14-20`): **415 KB / 132.78 KB gzip**. As demais extensões (`extension-table`, `extension-list`, `extension-highlight`, `extension-text-align`, `extension-code-block`) hoje caem dentro do chunk `index` de 1,1 MB porque não estão no `manualChunks` — nesse chunk também vai código irrelevante pro app (Sidebar, Admin, GraphView). Numa entrada nova, só o grafo de imports alcançável pelo editor entra no bundle — ou seja, o "index" gigante não se aplica; o piso real fica perto dos 415 KB do TipTap + essas extensões pequenas (pacotes `@tiptap/*` somam 8,7 MB **descompactados** no total, um indício de que são pequenas individualmente).
- Mermaid é, de longe, a maior dependência: 84 MB descompactados em `node_modules/mermaid` (~10× o total do TipTap). **Mas o próprio mermaid já faz code-splitting por tipo de diagrama** via `import()` dinâmico interno — o build gerou 30+ chunks separados (`flowDiagram-*.js` 62 KB, `sequenceDiagram-*.js` 117 KB, `c4Diagram-*.js` 65 KB, etc.), cada um baixado só quando aquele tipo de diagrama realmente renderiza.
- O problema: `mermaid-code-block.js:2` faz `import mermaid from 'mermaid'` **estático**, e esse arquivo é importado estaticamente por `Editor.svelte:11` — logo hoje o núcleo do mermaid carrega sempre que o editor monta, com ou sem bloco Mermaid na Nota.

**Recomendação**: sim, dá para carregar só quando há Mermaid — trocar a linha 2 de `mermaid-code-block.js` por um `import()` dinâmico dentro de `renderMermaid()`/`addNodeView()`, guardado por `isMermaid(node)` (já existe, linha 23-28). Página sem bloco Mermaid fica no piso de ~450-500 KB (gzip ~140 KB) só de TipTap; a primeira Nota com um bloco Mermaid paga o custo extra (núcleo + chunk do tipo de diagrama, algumas dezenas a ~150 KB gzip conforme o tipo) uma vez, sob demanda.

---

## 3. Como o bundle chega ao APK? Como a versão fica ligada à do PKD em produção?

**Estado hoje: nada disso existe ainda.**

- `pkd/.github/workflows/build-and-publish.yml:24-33` builda o frontend (`npm run build`) só como passo interno da imagem Docker do servidor Go (`vite.config.js:8`: saída vai para `internal/server/web/dist`, que o Go embute no binário — confirmado `.gitignore`d, não versionado). As 3 workflows do pkd (`build-and-publish.yml`, `promote-to-prod.yml`, `ghcr-cleanup.yml`) **não publicam o bundle do frontend como artefato isolado** em nenhum lugar — só a imagem Docker completa do servidor vai para o GHCR.
- `pkdMobile/.github/workflows/android.yml:15,40` só faz `actions/checkout@v7` do próprio `pkdMobile` — nenhum checkout do `pkd`, nenhuma referência a bundle externo. `pkdMobile/app/build.gradle.kts` não tem task de cópia de assets nem qualquer menção ao `pkd`.

Ou seja: as 3 opções do ticket (artefato do CI do PKD, cópia no repo do pkdMobile, build no CI do pkdMobile com checkout do PKD) **nenhuma está implementada** — é plumbing nova, para o ADR (ticket 03) decidir.

**Sobre versão em produção**: diferente do que se poderia supor, o PKD **tem** tags semver (`git tag`: `v1.0`, `v1.1`, `v1.1.0`, `v1.2`, `v1.3.0`) e `pkd/.github/workflows/promote-to-prod.yml` re-tageia a imagem `:edge` como `:stable` + a tag semver ao dar push numa tag `v*.*.*` (linhas 3-6, 50-61) — `docker pull ghcr.io/.../pkd:stable` na EC2 é literalmente essa tag. Isso dá um jeito real de "ligar a versão do bundle à versão do PKD em produção": a última tag semver promovida = o commit exato que está rodando.

**Recomendação**: opção (c) do ticket — checkout do `pkd` no CI do `pkdMobile` (`android.yml`), pinado a essa tag semver (não a `main`, que pode estar à frente do que está em produção), gravada num arquivo versionado do `pkdMobile` (ex.: `pkd-bundle-version.txt` lido pelo `android.yml`), buildar o bundle novo ali e copiar para `app/src/main/assets/editor/` antes de `assembleRelease`. Atualizar essa tag é um bump deliberado, revisável em PR, sem exigir nenhuma mudança no CI do `pkd` (mantém o repositório do PKD somente-leitura, como pedido). Vendorizar o build pronto no repo do `pkdMobile` (opção b) é mais simples de CI mas arrisca ficar fora de sincronia silenciosamente — só recomendável se o checkout cross-repo se provar custoso demais no protótipo (ticket 02).

---

## 4. O HTML do editor passa por `SanitizeEditorHTML` sem perda?

**Não — há perda confirmada.** Testado empiricamente (`security.SanitizeEditorHTML`, `pkd/internal/security/sanitize.go:74-76`, via teste Go temporário rodado e removido nesta pesquisa):

```
IN : <p style="text-align: center">centered</p><table><tr><td colspan="2" rowspan="1">merged</td></tr></table><ul data-type="taskList"><li data-type="taskItem" data-checked="true"><label><input type="checkbox" checked><span></span></label><div><p>task</p></div></li></ul>
OUT: <p>centered</p><table><tr><td>merged</td></tr></table><ul data-type="taskList"><li data-type="taskItem" data-checked="true"><span></span><div><p>task</p></div></li></ul>
```

1. **TextAlign — perda real e alcançável hoje.** `TextAlign.configure({types:['heading','paragraph']})` (`Editor.svelte:1141`) renderiza `style="text-align: X"` (`@tiptap/extension-text-align/dist/index.js:30-33`). `editorPolicy` só chama `AllowStyles` para `width/height` em `img`/`figure` e `background-color/color` em `mark` (`sanitize.go:36-42`) — nunca para `style` em `p`/`h1..h6`. O `style` inteiro é descartado, confirmado no teste acima. A barra de ferramentas expõe 4 botões de alinhamento (`Editor.svelte:1381-1388`, `setTextAlign(...)`) — é um recurso real e usado; hoje **todo save descarta o alinhamento silenciosamente**. Isso já é um bug pré-existente na PWA, não algo que o app introduz — mas o app herda essa limitação e o ADR deveria registrar isso.
2. **Table `colspan`/`rowspan`/`colwidth` — perda real, hoje inalcançável pela UI.** O schema de célula carrega esses 3 atributos (`@tiptap/extension-table/dist/index.js:111-117,151-157`), mas `editorPolicy` só permite `class` em `td`/`th` (`sanitize.go:43-45`) — nada de `colspan`/`rowspan`/`colwidth` em lugar nenhum da política. Confirmado no teste: `colspan="2" rowspan="1"` some. Hoje a barra de tabela só tem inserir/nova linha/nova coluna (`Editor.svelte:1423-1426`), sem mesclar células — então nenhuma Nota tem `colspan`/`rowspan` != 1 na prática atual (baixo impacto agora, mas uma tabela colada via markdown com `rowspan` real perderia o merge silenciosamente).
3. **TaskList/TaskItem — sem perda, apesar da aparência.** TipTap renderiza `<li data-checked="true"><label><input type="checkbox" checked><span/></label><div>…</div></li>` (`@tiptap/extension-list/dist/index.js:1215-1228`), mas o estado "marcado" vive no atributo `data-checked` do `<li>` (linha 1202), não na propriedade `checked` do `<input>`. `editorPolicy` permite `data-*` em qualquer elemento (`AllowDataAttributes()`, `sanitize.go:47`) e permite `li`/`span`/`div`, só não `label`/`input` (fora da lista de `AllowElements`, `sanitize.go:19-30`) — o sanitizador remove `<label>`/`<input>` mas preserva `data-type`/`data-checked` no `<li>` e o texto da tarefa. No próximo load, o `parseHTML` do TaskItem casa pelo `<li>`/atributos e recria seu próprio `<label><input>` (linhas 1201-1203) — round-trip sem perda visual ou funcional, confirmado no teste acima.
4. **DocLink, Highlight, Image/ResizableImage — sem perda**, todos explicitamente permitidos (`span`+`class`+`data-*` em `sanitize.go:28,43-44,47`; `background-color/color` em `mark` e `width/height` em `img` em `sanitize.go:34-42`).

**Recomendação**: antes (ou junto) do trabalho do bundle mobile, resolver a perda de `TextAlign` — ou corrigir `editorPolicy` em `sanitize.go` para permitir `style="text-align: ..."` (e idealmente `colspan`/`rowspan`/`colwidth`) nos elementos certos, ou tirar os botões de alinhamento da barra do app (não faz sentido o app oferecer um recurso que o PKD já descarta no primeiro save). Essa é uma correção no `pkd`, fora do escopo de edição deste research (read-only) — mas deve virar item do ADR (ticket 03).

---

## Resumo para o ADR

- Extensões TipTap são vanilla JS, reaproveitáveis sem Svelte; só 2 acoplamentos reais precisam virar callback (`apiGet` da busca de `[[`, navegação por `window.location.hash` do DocLink).
- Bundle separado = segunda entrada HTML no Vite (`base: './'`), não modo lib; piso ~450-500 KB gzip ~140 KB (TipTap completo) sem Mermaid; Mermaid deve virar `import()` dinâmico só quando a Nota tem bloco Mermaid.
- Nenhuma das 3 formas de entrega do ticket existe hoje — a mais viável, sem tocar CI do `pkd`, é checkout do `pkd` pinado na última tag semver promovida (`:stable`) dentro do CI do `pkdMobile`.
- `SanitizeEditorHTML` **perde** alinhamento de texto (bug real, reprodutível, hoje) e mesclagem de célula de tabela (inalcançável pela UI atual); checklist sobrevive ao round-trip apesar de parecer arriscado.
