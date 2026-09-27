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
- **App: esqueleto publicado e instalado no celular (`v0.1.2`, via Obtainium).**
  - Kotlin + Compose + Material 3, minSdk 31, pacote `in.dalc.pkdmobile`.
  - Tema com os tokens do PKD, que segue o claro/escuro do sistema.
  - Ícone do PKD (adaptive icon).
  - 4 abas vazias: Notas · Memórias · Documentos · Busca.
  - **Ainda sem login, sem API, sem Room.** O FAB não faz nada.

## Próxima ação (pergunta em aberto para o usuário)

Fazer os **slices 1 + 2 juntos** e publicar como `v0.2.0`, para o usuário fazer login e ver as Notas reais no celular. Recomendado. O usuário ainda não confirmou.

## Slices (em ordem)

1. **Login + sessão** (spec §4):
   - tela com URL (só `https://`) + senha, depois o código 2FA por e-mail;
   - cookies `pkd_session`/`pkd_device` cifrados no Keystore;
   - `X-CSRF-Token` (cookie `pkd_csrf`) nas mutações;
   - sessão expirada → pede a senha de novo.
2. **Cache Room + lista de Notas**: `GET /api/notes`, feed em 2 colunas com as favoritas primeiro, detalhe com salvamento automático, Tags e favoritar.
3. **Fila de envio** (WorkManager): `idempotency_key`, retry em `5xx`/rede, "Não enviados" em `4xx`, `401` → pede login.
4. **Memórias**: lista por mês, bottom sheet "Nova Memória".
5. **Documentos/Árvore**: `GET /api/tree`, corpo em WebView só leitura, Associações, Arquivos para ver/baixar.
6. **Busca**: servidor (`/api/tree?q=`); offline, busca local com "resultados parciais".
7. **Share** (`ACTION_SEND`): pela fila de envio para `/api/capture`, fora do bloqueio biométrico.
8. **Boxicons**: trocar os Material Icons.

## Como trabalhar (referência rápida)

- **Toolchain local** (Windows):
  - `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
  - `ANDROID_HOME=C:\Users\EDalcin\AppData\Local\Android\Sdk` (platform 35, build-tools 35, emulador).
  - Build: `gradlew.bat lintDebug assembleDebug`.
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

- Testar na PWA o botão **Administração → Sessões → Encerrar as outras sessões**. Ainda não foi testado pelo usuário.
- Nome do app no launcher: hoje é "pkdMobile". O usuário pode preferir "PKD" (mudança de uma linha em `strings.xml`).
