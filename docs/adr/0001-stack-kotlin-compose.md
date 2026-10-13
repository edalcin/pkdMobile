---
status: accepted
---

# App Android em Kotlin + Jetpack Compose, distribuído como APK

A v1 do pkdMobile é um app nativo em **Kotlin + Jetpack Compose (Material 3)**, com minSdk 31 (Android 12). A direção visual A (Notas-first) é uma UI nova em qualquer stack, então reaproveitar o Svelte da PWA traria pouco. As decisões de autenticação e offline (cookies cifrados no Keystore, biometria opcional, share intent fora do bloqueio, fila de envio, cache completo de Notas e Memórias, busca offline) têm todas uma solução first-party no Android: Room, WorkManager, `ACTION_SEND`, Keystore e BiometricPrompt. O corpo de um Documento, só leitura na v1, aparece em uma WebView com o `body_html` já sanitizado pelo PKD.

O tema é Material 3 fixo com os tokens de cor da PWA (`../pkd/frontend/src/styles/app.css`). O modo claro/escuro segue o sistema. Os ícones são Boxicons em vetor.

A distribuição é por **APK assinado no GitHub Releases**, gerado pelo GitHub Actions. As atualizações chegam pelo Obtainium. Não entra no Google Play: o app tem um único usuário. A chave de assinatura fica em GitHub Secrets.

## Considered Options

- **Capacitor + Svelte**: reaproveita a linguagem e os componentes da PWA. Foi descartado porque a origem local do WebView cai em cross-origin com o PKD, que não tem CORS. A única saída documentada (`server.url`) é de desenvolvimento. Share, armazenamento seguro, biometria e SQLite também dependeriam de plugins de comunidade.
- **PWA/TWA**: o menor APK (~200 KB) e nenhuma tecnologia nova. Foi descartado porque os cookies ficam no Chrome (não no Keystore), não há biometria nativa, e a fila de envio dependeria da Background Sync API, que não roda na hora.
- **Flutter**: seria uma 4ª linguagem (Dart), e o recebimento de share e o SQLite dependem de pacotes de comunidade.

## Consequences

Este projeto não segue algumas diretrizes globais, que pressupõem um serviço web em container:

- **Frontend**: Compose + Material 3 no lugar de SvelteKit + shadcn-svelte.
- **Dados**: o SQLite local (Room) fica no armazenamento privado do app. `DB_PATH` não se aplica.
- **Empacotamento**: o CI publica um APK no GitHub Releases no lugar de uma imagem Docker. Não há Dockerfile nem template para o UNRAID.

Continuam valendo: commits no `main`, nenhum segredo no repositório, Dependabot no CI, documentação em Markdown/Mermaid/C4, e o código Android neste repositório.
