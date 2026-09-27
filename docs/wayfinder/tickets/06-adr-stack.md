---
title: Qual stack implementa a v1?
type: wayfinder:grilling
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: [01-direcao-visual, 02-stacks-candidatas, 03-autenticacao, 04-offline]
---

## Question

Com a direção visual escolhida e os fatos sobre as stacks, qual stack implementa a v1? Resultado: um ADR em `docs/adr/`.

## Resolution

2026-09-27 — Decidido com o usuário (grilling). ADR: [`docs/adr/0001-stack-kotlin-compose.md`](../../adr/0001-stack-kotlin-compose.md).

1. **Stack:** Kotlin + Jetpack Compose (Material 3); Room, WorkManager, Keystore, BiometricPrompt; WebView só leitura para o corpo do Documento.
2. **Distribuição:** APK assinado no GitHub Releases pelo CI, com atualizações pelo Obtainium. Sem Google Play.
3. **minSdk:** 31 (Android 12).
4. **Tema:** tokens do PKD em Material 3 fixo; claro/escuro segue o sistema.
5. **Exceções às regras globais:** Compose no lugar de SvelteKit; Room no armazenamento do app (sem `DB_PATH`); APK no lugar de Docker/UNRAID.
