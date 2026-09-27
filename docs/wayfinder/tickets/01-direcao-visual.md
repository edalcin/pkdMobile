---
title: Qual direção visual o app segue?
type: wayfinder:prototype
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: []
---

## Question

Entre as 3 direções (A · Notas-first, B · Espelho da PWA, C · Busca-first), qual dá a melhor experiência para criar e ver Notas rapidamente, sem perder o acesso a Memórias e Documentos? Pode ser uma mistura.

Prototype: um HTML estático clicável com moldura de celular, com as telas da v1 em claro e escuro, para cada direção. Screenshots de validação em `C:\Users\EDalcin\Desktop\OMPtemp`.

## Assets

- Prototype: [`../prototypes/01-direcao-visual.prototype.html`](../prototypes/01-direcao-visual.prototype.html). Duplo clique para abrir. `?variant=A|B|C`, `?theme=light|dark`, barra flutuante com ← →, Tema e Share (simula o share intent).

## Resolution

2026-09-27 — O usuário escolheu **A · Notas-first**, sem mistura.

O que a direção A fixa para a spec da v1:
- Tela inicial: feed de Notas em cartões (2 colunas), com título (1ª linha), trecho e Tags.
- FAB: cria Nota na aba Notas e cria Memória na aba Memórias. A aba Documentos não tem FAB (Documentos são só leitura na v1).
- Barra inferior com 4 abas: Notas · Memórias · Documentos · Busca.
- Memórias: lista cronológica agrupada por mês (dia + dia da semana).
- Documentos: Árvore colapsável; o detalhe mostra caminho, corpo só leitura, Subdocumentos e Associações.
- Busca: uma aba própria, com resultados agrupados por tipo (Notas, Memórias, Documentos).
- Detalhe em tela cheia (sem barra inferior), com voltar. A Nota é editada no próprio detalhe, com salvamento automático.
- Share intent: abre uma bottom sheet "Nova Nota" com o conteúdo recebido.
- Tokens de cor e tipografia da PWA (`../pkd/frontend/src/styles/app.css`), tema claro/escuro.

O prototype fica no repositório (`docs/wayfinder/prototypes/`) como fonte primária, porque a regra do projeto proíbe branches novas.
