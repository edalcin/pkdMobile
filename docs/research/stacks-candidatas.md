# Research: como as stacks candidatas atendem os requisitos da v1?

Ticket: `docs/wayfinder/tickets/02-stacks-candidatas.md`. Stacks: Kotlin/Compose, Flutter, Capacitor + Svelte, PWA/TWA melhorada.

Convenção de citação: fontes do PKD como `pkd/caminho/arquivo.go:linha` (repositório `S:/git/pkd`); fontes externas como link direto à doc oficial. Toda afirmação sem fonte primária confirmada está marcada **[incerto]**.

## Fatos do PKD usados nas 4 seções

- Sessão por cookie: `pkd_session`, `sessionCookieMaxAge = 86400 * 30` (30 dias) — `pkd/internal/server/handlers_auth.go:12`.
- Login: senha mestra, com throttle de 5 falhas/30 min; se 2FA habilitado e dispositivo não confiável, envia código por e-mail (`handleLogin2FA`) — `pkd/internal/server/handlers_auth.go:14-55,85-121`. Dispositivo confiável fica confiado **permanentemente** (sem expiração no registro) — `pkd/internal/store/devices.go:16-23`.
- CSRF: double-submit cookie `pkd_csrf` + header `X-CSRF-Token`; qualquer request com `Authorization: Bearer ` pula CSRF inteiramente — `pkd/internal/server/middleware_csrf.go:21-48`.
- CSP da SPA: `connect-src 'self'` (entre outras diretivas) — sem qualquer header `Access-Control-*`/CORS em todo `internal/server` (confirmado por busca no diretório) — `pkd/internal/server/middleware_security.go:31-41`.
- Bearer: `PKD_IMPORT_TOKEN` via `tokenOrSession` (aceita o token OU a sessão de UI; um Bearer que não bate é 401, nunca cai para cookie) — `pkd/internal/server/handlers_memories.go:30-40`; rotas `/api/notes` e `/api/memories` usam esse middleware — `pkd/internal/server/server.go:213-222`.
- Notas: `POST /api/notes` é idempotente via `idempotency_key` (mesma chave retorna 200 com a Nota existente em vez de duplicar) — `pkd/internal/server/handlers_notes.go:48-95`.
- Documentos: `PATCH`/update usa controle de versão otimista — `version` divergente retorna `409` com `VersionConflict{StoredVersion, Stored}` — `pkd/internal/server/handlers_documents.go:82-204` (conflito de versão em `166-186`).
- Share intent hoje: a PWA declara `share_target` (`action: /api/capture`) no manifest — `pkd/frontend/public/manifest.webmanifest:23-27` — e o handler cria um **Documento** com a tag `captura`, não uma Nota — `pkd/internal/server/handlers_capture.go:17-19,61-62`. (Ticket 05 aponta que isso pode mudar; nenhuma das 4 stacks resolve essa decisão de produto sozinha.)
- Service worker atual: cacheia GETs de documento, mas todo POST/PUT/PATCH/DELETE (inclusive `/api/capture`) vai direto à rede; offline retorna 503 estruturado — `pkd/frontend/public/sw.js:6-8,56-60`.
- Frontend: Svelte 5.55 + Vite 6 + TipTap 3.30 (`@tiptap/core`, `starter-kit`, extensões) — `pkd/frontend/package.json:12-26`.

---

## 1. Kotlin + Jetpack Compose (nativo Android)

**Share intent → criar Nota**
App nativo declara `<intent-filter>` com `ACTION_SEND` na Activity e lê `Intent.EXTRA_TEXT`/`EXTRA_STREAM`. Documentação oficial: [developer.android.com — sharing (Compose)](https://developer.android.com/develop/ui/compose/sharing/send) e [intent-filter element](https://developer.android.com/guide/topics/manifest/intent-filter-element). É o único caminho 100% first‑party das 4 stacks (sem plugin de terceiros).

**Leitura offline + fila de escrita de Notas**
Padrão recomendado pelo próprio Android: **Room** como fonte única de verdade + **WorkManager** para a fila de sincronização em background (retry com backoff, sobrevive a reboot). Fontes oficiais: [Room + Offline-first architecture](https://developer.android.com/topic/architecture/data-layer/offline-first) e [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager). A chave `idempotency_key` do PKD (`pkd/internal/server/handlers_notes.go:48-95`) encaixa direto no padrão "outbox com UUID client-side" descrito na doc do Android.

**HTML de Documentos + reuso do Svelte/TipTap**
Compose não executa HTML/JS nativamente. Para mostrar o corpo HTML de um Documento é preciso um `android.webkit.WebView` ([referência oficial](https://developer.android.com/reference/android/webkit/WebView)) — ou seja, o "reuso do frontend Svelte/TipTap" na prática vira "abrir a página real do PKD dentro de um WebView", não reuso de componente Svelte em código nativo. O WebView do Android suporta Service Worker desde a API 24 via `ServiceWorkerController` ([referência oficial](https://developer.android.com/reference/android/webkit/ServiceWorkerController)), então o cache do `sw.js` existente funcionaria dentro desse WebView.

**Auth com o PKD**
CORS é um mecanismo **enforced apenas por navegadores/WebViews**; um app nativo (Retrofit/OkHttp puro, sem WebView) não é afetado pela ausência de CORS no servidor — [MDN CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS) confirma que CORS existe para relaxar a same-origin policy do navegador, que HTTP clients nativos não têm. Logo `connect-src 'self'` do PKD (`middleware_security.go:31-41`) não bloqueia chamadas nativas — só bloquearia se a tela de Documento usar WebView carregando um domínio diferente do PKD (não é o caso, o WebView aponta para o próprio servidor). Cookie de sessão: `OkHttp` com `CookieJar` customizado persiste `pkd_session` e o CSRF token; anexar `X-CSRF-Token` via `Interceptor`. Referência: [CookieJar/OkHttp — padrão de mercado, não é doc oficial do Android](https://square.github.io/okhttp/) mas é a lib recomendada nos guias de networking do Android **[não há página oficial developer.android.com endossando OkHttp por nome — inferência a partir de práticas de mercado, marcado incerto]**. Bearer (`PKD_IMPORT_TOKEN`) é trivial via header `Authorization`, e como não usa cookie nem é acionável por página cross-origin, o servidor também pula CSRF (`middleware_csrf.go:26-29`).

**Tamanho do APK, CI, custo de manutenção**
Um app Compose mínimo com R8/minificação fica na faixa de **1,5–3 MB** (debug sem otimização pode passar de 10 MB, não é o número real de distribuição) — sem uma página única "oficial" que cravar o número exato, a faixa vem de discussões e da doc oficial de métricas de migração da Compose ([developer.android.com — compare metrics](https://developer.android.com/develop/ui/compose/migrate/compare-metrics)); **[estimativa, não uma cifra fixa documentada]**. CI: GitHub Actions com `actions/setup-java` (oficial GH) + `gradle/actions/setup-gradle` (ação oficial do time Gradle, sucessora do `gradle-build-action`) — [Gradle docs — GitHub Actions](https://docs.gradle.org/current/userguide/github-actions.html), [actions/setup-java](https://github.com/actions/setup-java). Custo de manutenção para 1 dev: é a stack com **maior custo de entrada** (Kotlin, Compose, Room, WorkManager, WebView, OkHttp — tudo novo em relação ao stack atual do PKD, que é Go+Svelte) e **zero reuso** do código Svelte/TipTP já existente, exceto via WebView; em troca, é a única 100% suportada pela Google sem dependência de terceiros para as APIs centrais (Room, WorkManager, WebView, Compose).

**Limites nas direções A/B/C do mockup**
Direção B (espelho da PWA) exigiria recriar toda a navegação da SPA em Compose ou embrulhar a PWA inteira num WebView (perdendo a vantagem de "nativo"); A (Notas-first) e C (Busca-first) são telas simples e pequenas (lista de Notas, busca), boas candidatas a Compose puro com chamadas REST — o trabalho pesado de reuso é só para a visualização de Documento (WebView).

---

## 2. Flutter (Dart)

**Share intent → criar Nota**
Não existe solução first-party do time Flutter para *receber* `ACTION_SEND` (o Flutter foca em *enviar* via [`share_plus`](https://pub.dev/packages/share_plus)). Para receber é preciso o pacote comunitário [`receive_sharing_intent`](https://pub.dev/packages/receive_sharing_intent), que ainda exige editar manualmente o `AndroidManifest.xml` com o mesmo `<intent-filter>` nativo. Ou seja: mesma configuração nativa do item 1, mais uma dependência de terceiros para a ponte Dart↔Android.

**Leitura offline + fila de escrita**
Sem solução SQLite first-party: [`sqflite`](https://pub.dev/packages/sqflite) (padrão de mercado, mas comunidade, não mantido pelo time Flutter) ou [`drift`](https://drift.simonbinder.eu/) (também comunidade, type-safe, streams reativos). A fila de escrita usaria o plugin comunitário `workmanager` (wrapper Dart do WorkManager nativo) ou `Connectivity`+retry manual — não há um guia oficial equivalente ao do Android para esse padrão em Flutter **[inferência]**.

**HTML de Documentos + reuso do Svelte/TipTap**
[`webview_flutter`](https://pub.dev/packages/webview_flutter) **é** first-party (mantido pelo time Flutter/`flutter/packages`), então a mesma estratégia do item 1 (abrir a página do Documento dentro de um WebView) é totalmente suportada oficialmente. Reuso do TipTap continua sendo "rodar a página real", não reuso de componente.

**Auth com o PKD**
Mesma lógica do item 1 sobre CORS não afetar clientes nativos (Dart `http`/`dio` fora de WebView). Para cookie de sessão, usar `dio` + `cookie_jar` (pacotes comunitários) e anexar `X-CSRF-Token` manualmente; Bearer é um header simples.

**Tamanho do APK, CI, custo de manutenção**
Flutter embute o motor Dart/Skia no binário, então mesmo um app mínimo carrega esse "engine tax"; a doc oficial só dá ferramentas para reduzir (`--split-per-abi`, App Bundle, `--analyze-size`, R8), sem publicar um número fixo de baseline — [docs.flutter.dev/perf/app-size](https://docs.flutter.dev/perf/app-size), [docs.flutter.dev/deployment/android](https://docs.flutter.dev/deployment/android). Prática comum aponta para uma APK mínima de alguns MB por ABI **[estimativa, sem cravar valor]**, tipicamente maior que a de um Compose mínimo. CI: doc oficial (`docs.flutter.dev/deployment/cd`) reconhece GitHub Actions como opção, mas a ação usada na prática (`subosito/flutter-action`) é comunitária, não publicada pela Flutter/Google — [github.com/subosito/flutter-action](https://github.com/subosito/flutter-action). Custo de manutenção: introduz uma **terceira linguagem** (Dart, além de Go e TypeScript/Svelte já usados no PKD) para 1 dev só; SQLite e share-intent dependem de pacotes de comunidade (risco de abandono — o pacote de SQLite mais usado, `sqflite`, e o de share intent (`receive_sharing_intent`) não são first-party). Sobre o futuro do Flutter: em abril/2024 o Google cortou cargos nos times de Flutter/Dart/Python (~200 posições reportadas, número não confirmado oficialmente); a liderança do produto (Kevin Moore) afirmou que o roadmap seguia intacto, e o framework continua recebendo releases ativos em 2025/2026 — mas é um sinal de risco organizacional a considerar para manutenção de longo prazo **[fonte: reportagens de imprensa, não comunicado oficial do Google — marcado incerto quanto ao impacto futuro]**.

**Limites nas direções A/B/C**
Mesmos limites do Compose: B exige WebView full-screen ou reconstrução de UI; A/C são boas candidatas a widgets Flutter nativos com chamadas REST.

---

## 3. Capacitor + Svelte (reaproveitando o frontend do PKD)

**Share intent → criar Nota**
Capacitor não tem plugin first-party (Ionic) para *receber* intents de compartilhamento — só para abrir/registrar deep links via `@capacitor/app` (`appUrlOpen`) ([capacitorjs.com/docs/apis/app](https://capacitorjs.com/docs/apis/app)). Para `ACTION_SEND` é preciso: (a) um plugin comunitário como `@capgo/capacitor-share-target` ou `capacitor-plugin-send-intent`, ou (b) escrever um plugin Capacitor customizado em Kotlin que lê `getActivity().getIntent()` — documentado oficialmente em [capacitorjs.com/docs/android/custom-code](https://capacitorjs.com/docs/android/custom-code). Ou seja: mesmo trabalho nativo do item 1, encapsulado como plugin Capacitor.

**Leitura offline + fila de escrita**
Como o app roda a SPA real dentro do WebView do sistema, o `sw.js` existente já funciona sem mudança nenhuma (o WebView Android suporta Service Worker desde API 24, confirmado no item 1). Para SQLite "de verdade" (não IndexedDB), existe [`@capacitor-community/sqlite`](https://github.com/capacitor-community/sqlite) — mas é comunidade, e um relato de 2024 indica que o mantenedor histórico saiu do projeto ativo, com uma alternativa comercial (Capawesome) surgindo **[fonte: discussão da comunidade, não anúncio oficial — incerto quanto ao estado atual de manutenção]**. Como o service worker do PKD já usa cache HTTP (não SQLite) para leitura, a fila de escrita pode ser feita com o mesmo `sw.js`/IndexedDB do navegador, sem precisar desse plugin nativo.

**HTML de Documentos + reuso do Svelte/TipTap**
Esta é a única stack com reuso **total e literal**: o mesmo bundle Svelte 5.55 + TipTap 3.30 (`pkd/frontend/package.json:14,18-26`) roda dentro do WebView, sem reescrever nada. Não há "abrir uma página parecida" — é o mesmo código.

**Auth com o PKD**
Ponto técnico mais importante desta stack: por padrão, o Capacitor serve os assets locais (empacotados no APK) a partir de uma origem fixa (`https://localhost` ou o scheme configurado), e qualquer chamada de rede ao PKD real seria **cross-origin** do ponto de vista do WebView — nesse caso, a ausência de CORS no servidor (`middleware_security.go:31-41`, `connect-src 'self'`) **bloquearia** a maioria dos requests (WebView aplica a política de mesma origem como qualquer navegador). A solução documentada oficialmente é usar `server.url` na config do Capacitor para o WebView carregar o app **diretamente do domínio do PKD** (mesma origem) em vez de rodar os assets locais — [capacitorjs.com/docs/config](https://capacitorjs.com/docs/config), [capacitorjs.com/docs/guides/live-reload](https://capacitorjs.com/docs/guides/live-reload). A doc oficial descreve isso como recurso de *live reload/dev*, "não usar em produção" — usá-lo permanentemente em produção (carregar sempre a URL real do PKD, sem empacotar assets locais) é uma forma **não documentada oficialmente para esse caso de uso**, embora tecnicamente seja a mesma configuração; **[incerto: viabilidade em produção não confirmada pela doc oficial, precisa validação prática]**. Alternativa mais segura, também não documentada como "solução CORS": adicionar CORS no servidor Go só para essa origem específica do app — mudança de escopo do PKD, fora do controle da stack. Cookie de sessão, CSRF (header) e Bearer funcionam nativamente, pois é a mesma SPA que já faz isso hoje.

**Tamanho do APK, CI, custo de manutenção**
Como reaproveita o WebView do sistema (não embute engine própria como Flutter), o wrapper Capacitor tende a ser pequeno — mas não há um número fixo publicado oficialmente pela Ionic/Capacitor **[estimativa de poucos MB, incerto]**. CI: guia oficial da própria Capacitor cobre GitHub Actions — `npm run build` → `npx cap sync android` → `./gradlew bundleRelease`, com Gradle padrão — [capacitorjs.com/docs/guides/ci-cd](https://capacitorjs.com/docs/guides/ci-cd). Custo de manutenção: **o menor entre as 4 stacks candidatas a "app de verdade"** — reaproveita 100% do conhecimento de Svelte/TipTap já existente no time; o único código novo é o plugin de share-intent (algumas dezenas de linhas de Kotlin) e a config de rede. Único risco: resolver corretamente a questão de origem/CORS acima.

**Limites nas direções A/B/C**
Nenhuma — como é a mesma SPA, qualquer direção visual (A, B ou C) é só uma questão de rotas/CSS na própria PWA, sem limite imposto pelo wrapper.

---

## 4. PWA / TWA melhorada (Trusted Web Activity via Bubblewrap)

**Share intent → criar Nota**
O PKD já implementa isso: `share_target` no manifest aponta para `POST /api/capture` (`pkd/frontend/public/manifest.webmanifest:23-27`), API padrão do W3C — [web.dev — share target](https://developer.chrome.com/docs/android/trusted-web-activity) confirma que, uma vez a PWA instalada, o Android a lista no menu "Compartilhar" nativamente, sem código extra por plataforma. O único trabalho aqui é de **produto**, não de stack: decidir se `/api/capture` passa a criar Nota em vez de Documento com tag `captura` (`pkd/internal/server/handlers_capture.go:17-19,61-62`) — é exatamente a pergunta do ticket 05, independente da stack escolhida.

**Leitura offline + fila de escrita**
Sem WebView nativo por trás, um TWA é só o navegador (Chrome) renderizando a PWA — não há acesso a SQLite nativo do SO. Para "SQLite de verdade" dentro do navegador, o próprio projeto SQLite mantém oficialmente um build WASM com suporte a OPFS (armazenamento persistente, isolado por origem) — [sqlite.org/wasm](https://sqlite.org/wasm/doc/trunk/about.md), pacote oficial `@sqlite.org/sqlite-wasm`; requer rodar dentro de um Web Worker e, para o VFS padrão `opfs`, headers `Cross-Origin-Opener-Policy`/`Cross-Origin-Embedder-Policy` no servidor (existe uma variante `opfs-sahpool` que dispensa esses headers, ao custo de não suportar múltiplas abas). A fila de escrita usa o mesmo `sw.js` + `Background Synchronization API` — suportada em Chrome for Android desde a versão 49, confirmada pela [MDN](https://developer.mozilla.org/en-US/docs/Web/API/Background_Synchronization_API) (execução do sync fica a critério do navegador, não é imediata).

**HTML de Documentos + reuso do Svelte/TipTap**
Reuso total, igual ao Capacitor — é literalmente a mesma PWA, sem wrapper nenhum além do launcher TWA.

**Auth com o PKD**
Sem problema de origem: o TWA abre a URL real do PKD (mesmo domínio, verificado via Digital Asset Links — arquivo `assetlinks.json` em `/.well-known/`, protocolo oficial do Google — [developers.google.com/digital-asset-links](https://developers.google.com/digital-asset-links/tools/generator)), então cookie de sessão, CSRF double-submit e `connect-src 'self'` funcionam exatamente como na PWA hoje — nenhuma mudança de servidor necessária.

**Tamanho do APK, CI, custo de manutenção**
TWA é a menor APK das 4 por larga margem: a doc/comunidade da própria ferramenta oficial (Bubblewrap, mantida pelo Chrome Labs do Google) reporta wrappers em torno de **~200 KB**, porque a UI/lógica inteira mora no servidor web, não no APK — [developer.chrome.com/docs/android/trusted-web-activity](https://developer.chrome.com/docs/android/trusted-web-activity), CLI oficial [GoogleChromeLabs/bubblewrap](https://github.com/GoogleChromeLabs/bubblewrap). CI: `bubblewrap build` roda como qualquer CLI Node em GitHub Actions (sem ação de terceiros necessária, só Node + Gradle padrão gerado pela própria ferramenta) — fluxo descrito no [quick-start oficial](https://developer.chrome.com/docs/android/trusted-web-activity/quick-start). Custo de manutenção: **o menor de todos** — nenhuma stack nova, nenhuma linguagem nova; o trabalho é 100% dentro do repositório `pkd` (frontend Svelte) mais a configuração one-time do wrapper Bubblewrap + assetlinks.json.

**Limites nas direções A/B/C**
Direção B (espelho da PWA) é trivial por definição — é a PWA. A e C exigem o mesmo trabalho de frontend que exigiriam em qualquer stack (novas rotas/telas na SPA), mas esse trabalho não é afetado pelo wrapper TWA. Limitações reais: (1) depende de o usuário ter Chrome/navegador compatível com TWA instalado (praticamente garantido em Android, mas é uma dependência externa ao app); (2) sem SQLite nativo do SO — só WASM+OPFS, mais pesado para inicializar que Room/sqflite nativos; (3) sem os plugins nativos "de bolso" que Kotlin/Flutter/Capacitor têm para câmera, notificações etc., caso a v1 precise deles depois (fora do escopo desta v1 conforme `docs/wayfinder/map.md:34-35`).

---

## Tabela comparativa

| Critério | Kotlin/Compose | Flutter | Capacitor + Svelte | PWA/TWA (Bubblewrap) |
|---|---|---|---|---|
| Share intent → Nota | Nativo, 100% first-party ([Android docs](https://developer.android.com/develop/ui/compose/sharing/send)) | Precisa plugin de comunidade (`receive_sharing_intent`) + manifest nativo | Precisa plugin de comunidade ou plugin Capacitor customizado (Kotlin) | Já funciona hoje via `share_target` (W3C) — só falta decisão de produto (criar Nota vs. Documento) |
| Cache SQLite + fila de escrita | Room + WorkManager, ambos first-party e com [guia oficial](https://developer.android.com/topic/architecture/data-layer/offline-first) | `sqflite`/`drift` (comunidade) + `workmanager` (comunidade) | `sw.js`/IndexedDB já existente (grátis); SQLite nativo real só via plugin comunitário com manutenção incerta | Sem SQLite do SO; SQLite oficial via WASM+OPFS (`sqlite.org/wasm`), mais pesado de configurar; fila via Background Sync API (Chrome only, execução não imediata) |
| HTML de Documento + reuso Svelte/TipTap | WebView (não é reuso de componente, é rodar a página) | WebView (`webview_flutter`, first-party) | Reuso total e literal — mesma SPA | Reuso total e literal — mesma SPA |
| Cookie/CSRF/Bearer + ausência de CORS | Sem impacto — CORS não se aplica a HTTP client nativo fora de WebView | Sem impacto — mesmo motivo | **Impacto real**: WebView local é origem diferente do PKD → precisa `server.url` apontando pro domínio real (uso em produção não documentado oficialmente) | Sem impacto — TWA abre o domínio real, mesma origem, igual à PWA hoje |
| Tamanho de APK | ~1,5–3 MB app mínimo [estimativa] | Maior que Compose (motor Dart/Skia embutido) [estimativa, sem número oficial] | Pequeno (usa WebView do sistema) [estimativa, sem número oficial] | ~200 KB (todo o app mora no servidor) — citado pela doc oficial do Chrome |
| CI (GitHub Actions) | `actions/setup-java` + `gradle/actions/setup-gradle`, ambos oficiais | `subosito/flutter-action` é comunidade, não oficial | Guia oficial da Capacitor (`capacitorjs.com/docs/guides/ci-cd`), Gradle padrão | CLI oficial Bubblewrap + Gradle padrão, sem ação de terceiros |
| Custo de manutenção (1 dev) | Alto — 3ª stack de tecnologia nova, zero reuso de código | Mais alto ainda — 4ª linguagem (Dart) só para isto, dependências-chave são comunidade, incerteza organizacional pós-cortes 2024 no time Flutter [incerto quanto a impacto futuro] | Baixo — reaproveita 100% o conhecimento e o código Svelte/TipTap existente | O mais baixo — nenhuma tecnologia nova, trabalho fica dentro do próprio repositório `pkd` |
| Limite em A/B/C | B exige reconstrução de UI ou WebView full-screen | Igual ao Compose | Nenhum — qualquer direção é trabalho normal de frontend | Nenhum — B é trivial por ser a própria PWA |

## Notas finais

- Toda comparação de **tamanho de APK** para Kotlin/Compose, Flutter e Capacitor é estimativa de mercado, não um número publicado por doc oficial equivalente ao caso do TWA (que tem citação direta em `developer.chrome.com`). Está marcado **[estimativa]** em cada seção.
- A questão de CORS/origem do Capacitor (`server.url` em produção) precisa de uma prova de conceito antes de virar decisão de ADR — a doc oficial cobre esse mecanismo só para desenvolvimento/live-reload.
- O risco organizacional do Flutter (cortes de 2024 no time Google) é baseado em reportagens de imprensa da época, não em comunicado oficial do Google confirmando números ou impacto — mantido como **[incerto]**.
