# Próximos Passos

Atualizado em 2026-09-28.

## Onde estamos

- **Planejamento concluído.** O wayfinder chegou ao destino.
  - Spec da v1: [`docs/spec-v1.md`](spec-v1.md).
  - ADR da stack: [`docs/adr/0001-stack-kotlin-compose.md`](adr/0001-stack-kotlin-compose.md).
  - Mapa e tickets: `docs/wayfinder/`.
- **Pré-requisitos no PKD: feitos e em produção** (EC2, `pkd.dalc.in`; hoje `pkd:stable` = `3f65b66`, com `GET /api/notes`/`GET /api/memories` completos, `/api/capture` com tags do pedido, chi `v5.3.0`, e os pré-requisitos do editor rico do ADR 0002).
  - "Encerrar as outras sessões" (`POST /api/sessions/revoke-others` + botão em Administração → Sessões).
  - `/api/capture` cria **Nota** e aceita `idempotency_key`. Tag: as `tags` do pedido (o app manda `#android`), ou `#captura` sem elas (PWA).
  - O share da PWA funciona: sem `X-CSRF-Token` quando `Sec-Fetch-Site` é `none`/`same-origin`, e responde `303` para a Nota. Testado no celular.
- **App `v0.2.0`: slices 1 + 2 feitos** (login + sessão, cache Room + Notas). Testado no emulador contra um PKD local:
  - Login com URL + senha; 2FA por e-mail (tela pronta, **não testada**: o PKD local não manda e-mail).
  - Cookies cifrados (Keystore AES-GCM) em `data/Session.kt`; `X-CSRF-Token` nas mutações (`data/Api.kt`).
  - Sessão expirada (`401`) → tela de login com a mesma URL; "Sair e trocar de servidor" faz logout.
  - Feed de Notas em 2 colunas (favoritas primeiro), pull-to-refresh, atualização ao abrir e ao voltar do background.
  - Detalhe: texto simples com salvamento automático (1ª linha = título), Tags (pôr/tirar), favoritar.
  - Logout (ícone na barra de Notas) apaga cache e cookies.
  - Kotlin + Compose + Material 3, minSdk 31, pacote `in.dalc.pkdmobile`; Memórias, Documentos e Busca ainda vazias.
- **PKD em produção com o `GET /api/notes` novo** (`body_html`, `tags`, `updated_at`): os cartões mostram trecho e Tags no celular.
- **Limites conhecidos:**
  - ~~O app edita a Nota como texto simples~~: resolvido na `v0.10.0` (editor rico).
  - O PATCH do PKD ignora `favorite`; o app usa `POST /api/documents/{id}/favorite` (alterna) só quando o estado no PKD é diferente do pedido.

- **`v0.2.1`: Cloudflare Access.** `pkd.dalc.in` fica atrás do Cloudflare Access. Sem autorização, o Access responde `302` para o login dele (HTML), e a `v0.2.0` mostrava "Value <html> … cannot be converted to JSONObject". Decisão do usuário: **Service Token**.
  - A tela de login tem os campos opcionais `CF-Access-Client-Id`/`-Secret`, que ficam cifrados no Keystore. O app manda os dois headers em todo pedido.
  - Um `3xx` mostra "O Cloudflare Access recusou o pedido. Confira o Service Token."
  - Se o primeiro login falha, o app esquece o servidor, e a tela não diz mais "A sessão expirou".
- **Login no celular com o Service Token: funciona** (usuário, 2026-09-27). Policy `pkdMobile service token` (Service Auth) na aplicação `pkd` do Access.
- **`v0.3.0`: slice 3 feito** (fila de envio + criar Nota). Testado no emulador contra um PKD local, com o PKD parado e depois de volta:
  - Tabela Room `outbox`; o banco foi para a versão 2 (`fallbackToDestructiveMigration`: a v1 era só cache).
  - Toda mudança vai primeiro ao cache e à fila, e depois ao PKD, em ordem. A Nota nova tem id negativo e mostra "Na fila" até o PKD dar o id real.
  - Edições na mesma Nota enquanto esperam se juntam num item só; editar uma Nota ainda não enviada muda o item `create`.
  - A fila vai a cada mudança e a cada atualização (abrir, voltar do background, pull-to-refresh). Rede/`5xx`/`401`/`429` param a fila e mantêm tudo; outro `4xx` manda o item para **Não enviados** (Copiar, Recriar com o texto editável, Descartar). Ícone com contador na barra de Notas.
  - Com itens na fila, a atualização não troca o cache das Notas (só as Tags).
  - Logout com fila avisa quantos itens serão perdidos.
  - **Desvio do ADR 0001:** sem WorkManager. A spec não tem sync em background, então a fila roda no processo do app (`Notes.flush`). Adotar WorkManager só se o envio em background passar a ser requisito.
  - O POST de uma Nota com título repetido não falha: o PKD dá outro título ("… (2)").

- **`v0.4.0`: slice 4 feito** (Memórias). Testado no emulador contra um PKD local:
  - Aba Memórias: lista agrupada por mês (mais novas primeiro, como o PKD), dia + dia da semana, hora ou período (Almoço, Tarde…). Pull-to-refresh.
  - FAB → bottom sheet "Nova Memória" (data com DatePicker, título, detalhes), pela fila de envio (kind `memory`, `idempotency_key`). Offline mostra "Na fila".
  - Detalhe só leitura: data completa, título, detalhes, `MEM-…`.
  - Room versão 3 com **migração real** 2→3 (a fila da v0.3.0 sobrevive). Testado: item na fila na v0.3.0, atualização para a nova versão, item enviado.
  - Não enviados: Memória recusada tem só Copiar e Descartar (Recriar abre o sheet de Nota).
- **PKD em produção com o `GET /api/memories` novo** (`body_html`), deploy de 2026-09-27.
- **`v0.5.0`: slice 5 feito** (Documentos, só leitura). Testado no emulador contra um PKD local:
  - Aba Documentos: a Árvore (`GET /api/tree`) no cache (tabela `docs`, ordem pré-ordem), colapsável, pull-to-refresh.
  - Detalhe: caminho na Árvore, corpo em WebView sem JavaScript. Pedidos da WebView ao PKD (imagens `/api/attachments/…`) passam pelo app (`shouldInterceptRequest`) com cookie + headers do Cloudflare Access; links abrem fora.
  - Subdocumentos (do cache da Árvore), Notas relacionadas (abre o Documento ou a Nota do cache), Arquivos (**Ver** = baixa para o cache e abre em outro app via FileProvider; **Baixar** = DownloadManager para Downloads), Links externos.
  - Cache LRU dos 50 Documentos abertos (tabela `doc_bodies`); offline mostra a cópia com o aviso.
  - Documento protegido (`encrypted_locked`): mostra "Desbloqueie na PWA" (desbloqueio no app fora da v1).
  - Room versão 4, migração 3→4 testada (atualização de um aparelho com a v0.4.0).
- **`v0.6.0`: slice 6 feito** (Busca). Testado no emulador contra um PKD local:
  - Aba Busca: campo com espera de 400 ms, mínimo 2 letras; resultados agrupados em Notas, Memórias e Documentos; toque abre o detalhe.
  - Online: busca híbrida do PKD (`GET /api/tree?q=`, que marca `is_note`/`is_memory`).
  - Offline (erro de rede): busca local no cache (títulos; corpo das Notas e Memórias), sem acento e sem maiúsculas, com o aviso "resultados parciais".
- **`v0.7.0`: slice 7 feito** (Share). Testado no emulador contra um PKD local:
  - `ShareActivity` (tema translúcido) recebe `ACTION_SEND` `text/plain`: bottom sheet "Nova Nota #captura" sobre o app de origem; o assunto (`EXTRA_SUBJECT`) vira a 1ª linha.
  - Salvar põe um item `capture` na fila → `POST /api/capture` (`title`, `content`, `url`, `idempotency_key`); o PKD põe `#captura` e busca o Open Graph. 1ª linha = só o link → título vazio, o PKD usa o título da página (testado com github.com).
  - Offline: a Nota aparece com o link cru e "Na fila"; a fila envia depois (testado).
  - Sem servidor configurado: aviso "Entre no pkdMobile antes de compartilhar". Sessão expirada: a Nota fica na fila até o login.
- **`v0.8.0`: slice 8 feito** (Boxicons). Testado no emulador:
  - Fonte Boxicons 2.1.4 (a mesma da PWA, MIT) em `res/font/boxicons.ttf`; `assets/boxicons.txt` mapeia classe → código (gerado do `boxicons.css` do `../pkd/frontend/node_modules/boxicons`). Licença em `assets/boxicons-LICENSE.txt`.
  - `ui/Boxicons.kt`: `Boxicons.Icon(nome, descrição)`; o glifo fica fora do leitor de tela (só a descrição).
  - Todos os Material Icons trocados; dependência `material-icons-extended` removida (APK de release: 3,4 MB).
  - Árvore: o ícone `bx-…` de cada Documento aparece; classe que não existe na 2.1.4 (ex.: `bx-sticky-note`) cai em `bx-file`.

- **`v0.9.0`: 3 ajustes pedidos em 2026-09-28 feitos** (apagar Nota, tag picker, share `#android`). Testado no emulador contra um PKD local:
  - **Apagar Nota com confirmação:** ícone `bx-trash` na barra do Detalhe da Nota; `AlertDialog` "Mover «título» para a lixeira?" (Mover/Cancelar). Vai pela fila de envio: kind `delete` → `DELETE /api/documents/{id}` (`404` conta como sucesso); Nota nunca enviada (id negativo) só sai da fila, sem pedido; edições pendentes da mesma Nota (patch/favorite/create/capture) somem. Testado: Nota já sincronizada (sai do `GET /api/notes`), offline→online (fila `1 na fila` até reconectar), Nota nunca enviada (sem pedido ao PKD, confirmado no log do servidor).
  - **"Nova tag" com lista das Tags existentes:** o campo de texto livre virou um campo com sugestões (cache local de `GET /api/tags`, que agora traz `count`); filtro sem acento/maiúsculas, Tags já postas ficam escondidas, ordem = mais usada primeiro; última linha `Criar «texto»` quando não há Tag igual. Testado: sugestão `#pessoal (1)` e `Criar «pe»` ao digitar "pe".
  - **Share `#android`:** `Notes.capture` manda `tags: ["android"]` para `/api/capture` (que agora usa as tags do pedido em vez de `#captura` fixo — mudança no PKD, `handlers_capture.go`); o bottom sheet mostra "Nova Nota #android". Testado: Nota criada com a Tag `android` só (confirmado no `GET /api/notes` do PKD).
  - Room versão 5, migração real 4→5 (`ALTER TABLE tags ADD COLUMN count`).
  - PKD em produção com a mudança de `/api/capture` (`9d56195`, deploy de 2026-09-28). Tag `v0.9.0` publicada no mesmo dia.

- **PKD `3f65b66` em produção (2026-09-28): pré-requisitos do ADR 0002 feitos.** Testado pelo subagente com o PKD local e um navegador real:
  - `frontend/src/lib/editor/extensions.js` (`buildExtensions`): uma lista de extensões TipTap para a PWA e para o bundle mobile.
  - `DocLink` com `onOpen` injetável; busca `[[` injetável; Mermaid com `import()` dinâmico.
  - Bundle mobile: `cd ../pkd/frontend && npm run build:editor` → `frontend/dist-editor/` (`vite.editor.config.js`, `base: './'`). ~151 KB gzip sem Mermaid.
  - API da página: `window.pkdEditor.setContent(html)` / `getHTML()` / `command(h1|h2|h3|bullet|ordered|task|bold|italic|link|undo|redo)` / `setTheme('light'|'dark')` / `setEditable(bool)`. Callbacks na interface JS `AndroidEditor`: `onChange(html)` (toda transação), `onState(json)`, `onOpenLink(url)`, `onOpenDoc(id)`, `onReady()`, `onLossCheck({ok, details})`. O balão "Abrir · Editar · Remover" e o `inputmode="none"` ficam dentro da página.
  - Checagem de perda: após `setContent`, compara texto e histograma de tags; se perde algo, `setEditable(false)` e `onLossCheck({ok:false})`.
  - `SanitizeEditorHTML` aceita `text-align` em `p`/`h1–h6` e `colspan`/`rowspan` inteiros (teste em `internal/security/sanitize_test.go`). O alinhamento agora sobrevive ao save na PWA.

- **`v0.10.0`: editor rico de Notas (ADR 0002).** WebView com o bundle TipTap vendorizado do PKD (`app/src/main/assets/editor/`, servido por `WebViewAssetLoader` em `https://appassets.androidplatform.net/assets/editor/`), barra nativa Compose (Boxicons) acima do teclado.
  - Onde: detalhe da Nota, FAB "Nova Nota" (tela cheia, título opcional → ~60 primeiras letras do corpo; os dois vazios → não cria), "Recriar" em Não enviados. O share continua texto simples.
  - Salvar: `onChange` → 1 s de debounce + ao sair → `Notes.edit` → fila de envio. Abrir sem editar não manda PATCH (o app ignora o `onChange` de eco do `setContent`).
  - Conteúdo que o bundle não conhece (`onLossCheck` não ok): banner "Edite na PWA", barra escondida, nunca salva.
  - Link → balão Abrir (navegador); DocLink → Nota do cache ou detalhe do Documento.

- **`v0.11.0`: Link no corpo (2026-09-30).** Decisões do grilling (Q1–Q7) em `../pkd/docs/proximosPassos.md` §"Link no corpo"; termo no glossário do PKD.
  - O PKD cria o link para toda URL `http(s)://` na gravação. Por isso a Memória nova ("Detalhes") e o share continuam como texto simples: o app recebe o HTML com link quando a fila envia o item e no refresh. Offline, "Na fila", o texto aparece sem link.
  - Detalhe da Memória: `AnnotatedString.fromHtml` em vez de `htmlToText`. O link fica azul e sublinhado, e um toque abre o navegador. O card da Nota continua em texto puro.
  - Bundle do editor atualizado: só `http(s)://` vira link quando você digita, como no PKD. No editor, o toque no link continua abrindo o balão "Abrir" (Q6).
  - Testado no emulador contra um PKD local: o detalhe da Memória mostra o link azul e sublinhado, e um toque abre o Chrome. **Não testado no celular.**

## Próxima ação (retomar daqui)

Sessão encerrada pelo usuário em 2026-09-28. Estado:

- `v0.9.0` publicada e **testada no celular: ok**. Nome "pkdMobile" fica. Sem biometria, sem tela de Configurações.
- Mapa do editor rico: **destino alcançado** → [ADR 0002](adr/0002-editor-rico-webview-tiptap.md). Mapa: [`docs/wayfinder/editor/map.md`](wayfinder/editor/map.md).
- PKD: pré-requisitos do ADR 0002 **em produção** (`3f65b66`).
- **App `v0.10.0` (editor rico): commit `451de0b` + tag `v0.10.0` enviados** (o CI da release estava rodando no fim da sessão). Smoke no emulador ok (2026-09-28, PKD local): tabela/checklist/DocLink/alinhamento preservados após editar; abrir sem editar não manda PATCH; H2/bullet salvos; DocLink → Abrir no app; FAB sem título → título do corpo; offline → fila → enviado; conteúdo desconhecido → banner, sem PATCH; tema escuro; `assembleRelease` (R8) ok.
  - Bug achado e corrigido: o `setContent` do bundle manda um `onChange` de eco → o `RichNoteEditor` ignora o 1º `onChange` depois de carregar HTML não vazio. **Não testado no emulador:** Nova Nota vazia, digitar só 1 letra → o botão Salvar deve ligar.

**Ao retomar:**
1. Conferir o GitHub Release `v0.10.0` (APK assinado) → atualizar pelo Obtainium → testar o editor no celular (teclado, barra acima do teclado, link → Abrir, Nota rica da PWA, Nova Nota com 1 letra).
2. Se ok: candidatos da v1.1.

Depois: candidatos da v1.1 (spec §9: upload/câmera, "Neste dia"); opcional: tirar `notas/db` do backup em `~/atualizar.sh` (linha 36) no EC2.

Feito em 2026-09-27: container `notas` removido do EC2. Backup em `~/docker-compose_comNotas.yml`; o serviço saiu do `docker-compose.yml` e o container parado foi removido (`docker rm notas`). Os dados (`~/notas/db`, `~/notas/files`) ficaram no disco. Reverter: `cp ~/docker-compose_comNotas.yml ~/docker-compose.yml && docker-compose up -d`.

## Slices (em ordem)

1. ~~Login + sessão~~ (feito na v0.2.0).
2. ~~Cache Room + lista de Notas~~ (feito na v0.2.0; criar Nota vai para o slice 3).
3. ~~Fila de envio~~ (feita na v0.3.0, sem WorkManager). Inclui criar Nota (FAB) e a edição offline.
4. ~~Memórias~~ (feito na v0.4.0).
5. ~~Documentos/Árvore~~ (feito na v0.5.0).
6. ~~Busca~~ (feito na v0.6.0).
7. ~~Share~~ (feito na v0.7.0).
8. ~~Boxicons~~ (feito na v0.8.0).

## Como trabalhar (referência rápida)

- **Toolchain local** (Windows):
  - `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
  - `ANDROID_HOME=C:\Users\EDalcin\AppData\Local\Android\Sdk` (platform 35, build-tools 35, emulador).
  - Build: `gradlew.bat lintDebug assembleDebug`.
  - **`S:` é unidade de rede e o Gradle falha nela** ("Cannot create directory …"). Copiar para o disco local e compilar lá:
    `robocopy S:/git/pkdMobile C:/Users/EDalcin/AppData/Local/pkdMobile-src /MIR /XD build .gradle .git .idea`, depois o build em `C:/Users/EDalcin/AppData/Local/pkdMobile-src`. (Um init script que muda o `buildDir` não serve: o KSP exige a mesma raiz.)
- **PKD local para teste:** `go build ./cmd/pkd` e rodar com `PKD_PASSWORD`, `PKD_DB_PATH`, `PKD_ATTACHMENTS_PATH`, `PKD_LISTEN_ADDR=:8099` e `PKD_IMPORT_TOKEN` (para semear Notas com Bearer). Sem SES não há 2FA. No emulador a URL é `http://10.0.2.2:8099`: **só o build de debug aceita `http://`** (`src/debug/AndroidManifest.xml` + `BuildConfig.DEBUG`).
- **Emulador:** AVD `pkd35`. Headless: `emulator -avd pkd35 -no-window -no-audio -gpu swiftshader_indirect`. Depois `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Screenshots vão para `C:\Users\EDalcin\Desktop\OMPtemp`.
- **Bundle do editor (ADR 0002):** depois de mudar o editor no PKD, rodar `scripts/update-editor-bundle` (Git Bash; `PKD_DIR` padrão `../pkd`). Ele roda `npm run build:editor`, copia `frontend/dist-editor/` para `app/src/main/assets/editor/` e grava o SHA do PKD em `PKD_SHA`. Commitar os três juntos.
- **Release:**
  - `git tag vX.Y.Z && git push origin vX.Y.Z` → o CI gera o APK assinado e publica no GitHub Releases → o Obtainium atualiza o celular.
  - O versionName vem da tag e o versionCode vem do run number do CI. Build local = `0.0.0-dev`.
- **Assinatura:** o keystore fica em `C:\Users\EDalcin\Documents\chaves\pkdmobile-release.jks`, fora do repositório. Os 4 GitHub Secrets estão configurados.
- **Dependabot:** o Gradle recebe só patches. Versões minor/major exigem compileSdk 36+. Para adotá-las: subir o compileSdk e o SDK local/CI, e então remover o `ignore` em `.github/dependabot.yml`.
- **Deploy do PKD (EC2 `98.93.8.1`, chave `C:\Users\EDalcin\.ssh\EC2Geral.pem`):**
  1. push no `main` do `../pkd` → o workflow **Build and Publish** gera `edge`;
  2. rodar o workflow **Promote to Production** → `stable`;
  3. no EC2: `./atualizar.sh --aplicar`. O script faz backup do compose e dos bancos. Conferir no fim que o container `pkd` foi recriado ("Up X seconds"); se o `pkd` não mudou, o promote ainda não tinha rodado.

## Pendências menores

- ~~Testar o botão **Encerrar as outras sessões**~~: o endpoint foi testado contra o PKD local (`{"revoked":2}`, e o app caiu na tela de login). Falta só o usuário ver o botão na PWA.
