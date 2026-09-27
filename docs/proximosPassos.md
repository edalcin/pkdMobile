# Próximos Passos

Atualizado em 2026-09-27.

## Onde estamos

- **Planejamento concluído.** O wayfinder chegou ao destino.
  - Spec da v1: [`docs/spec-v1.md`](spec-v1.md).
  - ADR da stack: [`docs/adr/0001-stack-kotlin-compose.md`](adr/0001-stack-kotlin-compose.md).
  - Mapa e tickets: `docs/wayfinder/`.
- **Pré-requisitos no PKD: feitos e em produção** (EC2, `pkd.dalc.in`, imagem `pkd:stable` = `sha-de4a579`).
  - "Encerrar as outras sessões" (`POST /api/sessions/revoke-others` + botão em Administração → Sessões).
  - `/api/capture` cria **Nota** `#captura` e aceita `idempotency_key`.
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
  - O app edita a Nota como texto simples: a formatação rica feita na PWA se perde quando o app salva o texto.
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

## Próxima ação

Os 8 slices da v1 estão feitos. Falta da spec:
1. **Configurações** (spec §3): biometria (liga/desliga, BiometricPrompt), logout, lista Não enviados. Hoje logout e Não enviados ficam na barra de Notas.
2. **Detalhe da Nota ainda sem "Converter"/"Apagar"** — fora da v1 (spec §9), sem ação.
2. Container `notas` no EC2: o `atualizar.sh` ligou de novo o app Notas, que foi desligado em 2026-09-26. Tirar do `docker-compose.yml` se não for intencional.

## Slices (em ordem)

1. ~~Login + sessão~~ (feito na v0.2.0).
2. ~~Cache Room + lista de Notas~~ (feito na v0.2.0; criar Nota vai para o slice 3).
3. ~~Fila de envio~~ (feita na v0.3.0, sem WorkManager). Inclui criar Nota (FAB) e a edição offline.
4. ~~Memórias~~ (feito na v0.4.0).
5. ~~Documentos/Árvore~~ (feito na v0.5.0).
6. ~~Busca~~ (feito na v0.6.0).
7. ~~Share~~ (feito na v0.7.0). O bloqueio biométrico (spec §4) ainda não existe; o Share já fica fora dele.
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
- **Release:**
  - `git tag vX.Y.Z && git push origin vX.Y.Z` → o CI gera o APK assinado e publica no GitHub Releases → o Obtainium atualiza o celular.
  - O versionName vem da tag e o versionCode vem do run number do CI. Build local = `0.0.0-dev`.
- **Assinatura:** o keystore fica em `C:\Users\EDalcin\Documents\chaves\pkdmobile-release.jks`, fora do repositório. Os 4 GitHub Secrets estão configurados.
- **Dependabot:** o Gradle recebe só patches. Versões minor/major exigem compileSdk 36+. Para adotá-las: subir o compileSdk e o SDK local/CI, e então remover o `ignore` em `.github/dependabot.yml`.
- **Deploy do PKD (EC2 `98.93.8.1`, chave `C:\Users\EDalcin\.ssh\EC2Geral.pem`):**
  1. push no `main` do `../pkd` → o workflow **Build and Publish** gera `edge`;
  2. rodar o workflow **Promote to Production** → `stable`;
  3. no EC2: `./atualizar.sh --aplicar`. O script faz backup do compose e dos bancos, e o `atualizar.sh` agora aceita notas no comentário `tag de origem`.

## Pendências menores

- ~~Testar o botão **Encerrar as outras sessões**~~: o endpoint foi testado contra o PKD local (`{"revoked":2}`, e o app caiu na tela de login). Falta só o usuário ver o botão na PWA.
- Nome do app no launcher: hoje é "pkdMobile". O usuário pode preferir "PKD" (mudança de uma linha em `strings.xml`).
