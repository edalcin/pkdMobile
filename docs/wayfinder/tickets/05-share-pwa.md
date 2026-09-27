---
title: A PWA também passa a criar Nota no share?
type: wayfinder:grilling
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: []
---

## Question

O share intent do Android cria uma Nota. A PWA (`POST /api/capture`) cria um Documento com a tag `#captura`. Os dois comportamentos ficam diferentes, ou a PWA muda para criar Nota também? O que acontece com a tag `#captura` em uma Nota?

## Resolution

2026-09-27 — Decidido com o usuário (grilling). Fato: hoje `POST /api/capture` busca o Open Graph, cria um Documento na raiz e aplica `#captura` (`../pkd/internal/server/handlers_capture.go:20-75`).

1. **Share:** PWA e app criam **Nota**.
2. **Tag:** a Nota de captura recebe `#captura`.
3. **Caminho único:** `/api/capture` passa a aceitar `idempotency_key`. O share do app entra na fila de envio e vai para `/api/capture`, que busca o Open Graph. Offline, a Nota mostra o link cru e ganha o título quando a fila sai.
4. **Legado:** os Documentos `#captura` existentes ficam como estão.

Pré-requisito no PKD para a v1: `/api/capture` cria Nota e aceita `idempotency_key`.
