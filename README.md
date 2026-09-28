# pkdMobile

Interface de celular Android para o PKD.

App nativo em Kotlin + Jetpack Compose (Material 3), único usuário, distribuído como APK assinado no GitHub Releases (sem Google Play). Ver [`docs/adr/0001-stack-kotlin-compose.md`](docs/adr/0001-stack-kotlin-compose.md) e [`docs/spec-v1.md`](docs/spec-v1.md). Estado atual e próximos passos: [`docs/proximosPassos.md`](docs/proximosPassos.md).

## O que o app faz (v1)

- **Notas:** feed em 2 colunas (favoritas primeiro), criar (FAB), **editor rico** (o mesmo TipTap da PWA: títulos, listas, checklist, negrito, itálico, link; tabelas, DocLinks e alinhamento ficam iguais) com salvamento automático, Tags (com sugestões), favoritar, apagar (lixeira). Se a Nota tem conteúdo que o app ainda não edita, ela abre só para leitura ("Edite na PWA").
- **Memórias:** lista por mês, "Nova Memória" (data, título, detalhes), detalhe com `MEM-…`.
- **Documentos:** Árvore colapsável e detalhe só leitura (corpo, Subdocumentos, Notas relacionadas, Arquivos para ver/baixar, Links externos).
- **Busca:** a busca híbrida do PKD; sem conexão, busca local com "resultados parciais".
- **Share:** "Compartilhar → Nota no PKD" em outros apps cria uma Nota `#android` (texto simples).
- **Offline:** cache local (Room) de Notas, Memórias, Árvore e dos 50 últimos Documentos abertos; toda mudança passa por uma **fila de envio**, e o que o PKD recusa vai para **Não enviados** (ícone na barra de Notas).
- Ícones Boxicons (os mesmos da PWA); tema claro/escuro segue o sistema. Sem biometria.

> UNRAID e Docker não se aplicam a este projeto: é um APK Android, não um serviço web em container (ver seção "Consequences" da ADR 0001).

## Build local

Pré-requisitos:

- JDK 17 (ex.: Eclipse Temurin).
- Android SDK com `platform-tools`, `platforms;android-35` e `build-tools;35.0.0` instalados (`sdkmanager` no `cmdline-tools/latest/bin`).
- Variáveis de ambiente `JAVA_HOME` e `ANDROID_HOME` apontando para eles (ou `local.properties` com `sdk.dir=`).

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug   # build de debug + testes + lint
./gradlew assembleRelease                             # build de release (assinatura de debug se não houver keystore)
```

O APK de debug sai em `app/build/outputs/apk/debug/`; o de release em `app/build/outputs/apk/release/`.

## Bundle do editor

O editor de Notas é o bundle TipTap do PKD, vendorizado em `app/src/main/assets/editor/` (ver [ADR 0002](docs/adr/0002-editor-rico-webview-tiptap.md)). O arquivo `PKD_SHA` guarda o commit do PKD de onde ele saiu. Para atualizar depois de mudar o editor no PKD (Git Bash, com o repositório `pkd` ao lado):

```bash
scripts/update-editor-bundle   # PKD_DIR=/caminho/do/pkd se não for ../pkd
```

Commitar juntos o `assets/editor/` e o `PKD_SHA`.

## Assinatura de release

O build de release lê a chave de 4 variáveis de ambiente; sem elas, cai para a assinatura de debug (build local continua funcionando, só não instala por cima de uma versão assinada de verdade). Para gerar uma chave:

```bash
keytool -genkeypair -v -keystore pkdmobile-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias pkdmobile
```

Depois, defina (ver [`.env.example`](.env.example)):

```
PKD_KEYSTORE_PATH=<caminho para pkdmobile-release.jks>
PKD_KEYSTORE_PASSWORD=<senha do keystore>
PKD_KEY_ALIAS=pkdmobile
PKD_KEY_PASSWORD=<senha da chave>
```

**Nunca** commitar o `.jks`/`.keystore` nem essas senhas — ficam só no ambiente local e nos GitHub Secrets.

## CI/CD (GitHub Actions)

- Push em `main`: `testDebugUnitTest`, `lintDebug`, `assembleDebug`; sobe o APK de debug como artifact do workflow.
- Tag `v*`: decodifica o keystore e monta `assembleRelease`, depois cria um GitHub Release com o APK assinado.

GitHub Secrets necessários para a release (Settings → Secrets and variables → Actions):

| Secret | Conteúdo |
|---|---|
| `PKD_KEYSTORE_BASE64` | `pkdmobile-release.jks` em base64 (`base64 -w0 pkdmobile-release.jks`) |
| `PKD_KEYSTORE_PASSWORD` | senha do keystore |
| `PKD_KEY_ALIAS` | alias da chave |
| `PKD_KEY_PASSWORD` | senha da chave |

Dependabot ativo para `gradle` e `github-actions` ([`.github/dependabot.yml`](.github/dependabot.yml)).

## Instalar

1. Baixe o APK mais recente em [Releases](../../releases).
2. Instale via [Obtainium](https://github.com/ImranR98/Obtainium), apontando para este repositório — ele acompanha os Releases do GitHub e avisa quando sai uma versão nova.

## Primeiro login

- **URL do PKD:** só `https://` (o build de debug aceita também `http://`, para testar com um PKD local).
- **Cloudflare Access (opcional):** se o PKD fica atrás do Access, crie um Service Token (Zero Trust → Access → Service credentials) e ponha na aplicação do PKD uma policy com a action **Service Auth** que inclua esse token. No app, preencha `CF-Access-Client-Id` (termina em `.access`) e `CF-Access-Client-Secret`. Ficam cifrados no aparelho.
- **Senha** do PKD; num aparelho novo, o código de 2FA que chega por e-mail.
- A sessão dura 30 dias sem uso; depois o app pede só a senha. **Sair** (ícone na barra de Notas) apaga o cache, a fila e os cookies deste aparelho.
