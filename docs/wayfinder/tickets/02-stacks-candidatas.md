---
title: Como as stacks candidatas atendem os requisitos da v1?
type: wayfinder:research
status: closed
assignee:
blocked_by: []
---

## Question

Para Kotlin/Compose, Flutter, Capacitor + Svelte e PWA/TWA melhorada: como cada uma atende estes pontos?

- Share intent (receber texto/link de outros apps) e criação de Nota.
- Leitura offline com cache local (SQLite) e fila de escrita de Notas.
- Mostrar HTML de Documentos (só leitura) e reuso do frontend Svelte/TipTap do PKD.
- Auth com o PKD: sessão por cookie (30 dias, CSRF double-submit, 2FA por e-mail) ou Bearer token. O servidor não tem CORS (`connect-src 'self'`).
- Tamanho do APK, CI de build (GitHub Actions), custo de manutenção para um só desenvolvedor.
- Limites de cada uma para as direções A, B e C do mockup.

## Resolution

Resultado completo: [`docs/research/stacks-candidatas.md`](../../research/stacks-candidatas.md).

- Kotlin/Compose: share intent, cache (Room) e fila (WorkManager) são first-party. Não há problema com CORS. O custo de manutenção é alto: a stack é nova e não reaproveita código.
- Flutter: o share intent e o SQLite dependem de pacotes da comunidade. É uma quarta linguagem (Dart). O custo de manutenção é o mais alto.
- Capacitor + Svelte: reaproveita toda a SPA Svelte/TipTap. O share intent precisa de um plugin. **Risco:** a WebView local tem outra origem, e o servidor não tem CORS. `server.url` em produção precisa de uma prova de conceito.
- PWA/TWA: o share_target já funciona. Não há problema de origem, e o APK tem cerca de 200 KB. O offline depende do service worker ou de IndexedDB/SQLite-WASM. A Background Sync só funciona no Chrome.
- Tamanhos de APK (exceto TWA) são estimativas.
