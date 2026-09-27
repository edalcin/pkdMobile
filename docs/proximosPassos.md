# Próximos Passos

Estado: **esqueleto do app criado**. Projeto Gradle (Kotlin DSL, catálogo de versões, wrapper commitado), tema Material 3 com os tokens do PKD (claro/escuro segue o sistema, sem dynamic color), casca de navegação (barra inferior com as 4 abas, TopAppBar + estado vazio por aba, FAB sem ação em Notas/Memórias), assinatura de release por variáveis de ambiente com fallback para debug, e CI (`android.yml`) que builda/testa/linta em todo push a `main` e publica um Release com APK assinado em toda tag `v*`. Ainda não há login, chamadas à API, Room, WorkManager nem share intent — isso é código real, ainda por vir.

- Mapa: `docs/wayfinder/map.md` (destino, decisões, fog, out of scope).
- Tickets: `docs/wayfinder/tickets/`.
- Spec da v1: [`docs/spec-v1.md`](spec-v1.md). ADR da stack: [`docs/adr/0001-stack-kotlin-compose.md`](adr/0001-stack-kotlin-compose.md).
- Ícones da casca de navegação: Material Icons (padrão do Compose) por enquanto. Boxicons em vetor (definido na ADR 0001) entra depois, como um slice próprio de troca de ícones.
- FAB de Notas e Memórias existe na tela mas não faz nada ainda (sem bottom sheet, sem gravação) — só decora o layout até o slice de cache + fila de envio.

## Pré-requisitos no PKD (ainda pendentes, seção 8 da spec)

1. Endpoint + botão "Encerrar as outras sessões" na Administração da PWA.
2. `POST /api/capture` criar **Nota** (não Documento) com `#captura`, aceitando `idempotency_key`.

## Próximos slices (em ordem)

1. **Login + sessão** (spec §4): tela de URL do PKD + senha, 2FA por e-mail, cookies `pkd_session`/`pkd_device` cifrados no Android Keystore, `X-CSRF-Token` nas mutações, expiração de 30 dias.
2. **Cache Room + lista de Notas**: schema Room para Notas, tela de Notas real (feed 2 colunas, favoritas primeiro), detalhe com salvamento automático.
3. **Fila de envio** (WorkManager): criar/editar Notas offline, `idempotency_key`, retry em `5xx`/rede, "Não enviados" em `4xx` definitivo.
4. **Memórias**: cache Room, lista cronológica por mês, bottom sheet "Nova Memória", fila de envio para criação.
5. **Documentos/Árvore**: cache da estrutura da Árvore, tela colapsável, corpo em WebView (só leitura), Subdocumentos, Associações.
6. **Busca**: campo + resultados agrupados por tipo; offline com aviso de "resultados parciais".
7. **Share** (`ACTION_SEND`): bottom sheet "Nova Nota" com Tag `#captura`, fora do bloqueio biométrico.
8. **Boxicons**: trocar os Material Icons da casca de navegação (e do resto do app) pelos vetores Boxicons definidos na ADR 0001.
