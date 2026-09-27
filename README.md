# pkdMobile

Interface de celular Android para o PKD.

App nativo em Kotlin + Jetpack Compose (Material 3), único usuário, distribuído como APK assinado no GitHub Releases (sem Google Play). Ver [`docs/adr/0001-stack-kotlin-compose.md`](docs/adr/0001-stack-kotlin-compose.md) e [`docs/spec-v1.md`](docs/spec-v1.md).

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
