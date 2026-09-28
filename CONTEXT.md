# Contexto do Projeto: Interface Android para o PKD

**Descrição:** Desenvolvimento de um aplicativo para Android que oferece acesso, edição e organização da base de conhecimento pessoal PKD, priorizando desempenho offline, integração com o sistema e uma experiência mobile-first. Stack: Kotlin + Jetpack Compose (ver `docs/adr/0001-stack-kotlin-compose.md`).



## 1. Visão Geral do PKD 

O PKD (Personal Knowledge Database) - @pkd/ - é uma aplicação auto-hospedada de base de conhecimento pessoal, focada em hierarquia ilimitada, edição rica e recuperação rápida de informações. Atualmente é uma PWA (Progressive Web App) com suporte a instalação e compartilhamento de links via share target. O projeto Android visa levar essa experiência para um aplicativo nativo, aproveitando recursos do sistema como notificações, armazenamento local, integração com a câmera e uma visualização limpa, com separação clara dos tipos de conteúdo "documentos", "notas" e "memórias". Quero poder criar e visualizar rapidamente, especialmente, as "notas".

**Principais funcionalidades do PKD (relevantes para o Android):**
- Hierarquia ilimitada de documentos (arrastar e soltar para reorganizar).
- Editor rico com TipTap v3 (negrito, listas, tabelas, imagens inline, diagramas Mermaid).
- Busca híbrida (léxica + semântica) com fallback automático.
- Chat com os documentos (requer chave Gemini).
- Memória Cronológica: eventos organizados por Ano → Mês → Dia.
- Notas rápidas (texto curto, fora da árvore principal).
- Associações: notas relacionadas, arquivos, links externos.
- Tags com cores, arquivamento, links públicos e Graph View.
- Administração: backup, restauração, gestão de anexos e links.

## 2. Domínio: Termos Específicos do PKD

| Termo                | Definição                                                    |
| -------------------- | ------------------------------------------------------------ |
| **Documento**        | Unidade principal de conteúdo. Possui título único, corpo rico, ícone, tags, hierarquia e associações. |
| **Nota**             | Texto curto e rápido (endereço, contato, lista) que vive no bloco “Notas” da sidebar, fora da árvore. Pode ser convertida em Documento ou Memória. |
| **Memória (MC)**     | Evento registrado no tempo (almoço, entrega, consulta). Vive apenas na árvore da Memória Cronológica (Ano → Mês → Dia), sem pai nem filhos. ID público `MEM-…`. |
| **Árvore**           | Estrutura hierárquica de documentos exibida na sidebar. Suporta filtro, ícones e estado expandido/colapsado persistido. |
| **Hierarquia**       | Relação pai‑filho entre documentos. Arrastar e soltar para reorganizar. |
| **Associações**      | Área no rodapé do documento com três colunas: Notas relacionadas, Arquivos e Links externos. |
| **Tags**             | Marcadores com cores configuráveis. Chips coloridos na sidebar e no editor. |
| **Graph View**       | Visualização em grafo (D3.js) de documentos, tags e hierarquia. Modo semântico exibe similaridade por embeddings. |
| **PWA Share Target** | Envio de conteúdo de outros apps para o PKD. Na PWA, cria uma Nota com a tag `#captura`; no app Android, cria uma Nota com a tag `#android`. |
| **Fila de envio**    | No app Android: Notas e Memórias criadas ou editadas sem conexão, esperando para ir ao PKD. |
| **Não enviados**     | No app Android: itens que o PKD rejeitou ao receber a fila de envio. O usuário copia, recria ou descarta cada um. |

## 3. Links Úteis

- Repositório PKD: `@pkd/`
- Documentação da API (parcial): ver README do PKD, seção “Importação de documentos”.
- Mapa de planejamento (decisões, tickets, escopo): `docs/wayfinder/map.md`.
- Glossário do PKD: [`@pkd/docs/adr/glossary.md`](https://github.com/edalcin/pkd/blob/main/docs/adr/glossary.md).

---
