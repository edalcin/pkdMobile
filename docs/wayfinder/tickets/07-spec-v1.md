---
title: Qual é a spec da v1?
type: wayfinder:grilling
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: [01-direcao-visual, 03-autenticacao, 04-offline, 05-share-pwa, 06-adr-stack]
---

## Question

Juntar as decisões fechadas em uma spec da v1 (`docs/spec-v1.md`): telas, escopo, auth, offline, share, stack e os pré-requisitos no PKD. Antes, decidir o que ainda está na fog:

- **Anexos e câmera:** upload/download de arquivos e foto direto para uma Nota. A direção A não mostra anexos; o ponto de entrada natural é a bottom sheet "Nova Nota". Entra na v1 ou fica fora?
- **Notificações:** lembretes de Memórias ou de atividades. Entra na v1 ou fica fora?

## Resolution

2026-09-27 — Decidido com o usuário (grilling). Spec: [`docs/spec-v1.md`](../../spec-v1.md).

1. **Anexos:** ver e baixar Arquivos existentes; sem upload nem câmera na v1.
2. **Notificações:** fora da v1 ("Neste dia" é candidato para depois).
3. **Nota:** além de criar, ver, editar e buscar, a v1 põe e tira Tags e favorita. Converter e apagar ficam na PWA.
