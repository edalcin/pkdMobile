---
title: Como o app autentica no PKD?
type: wayfinder:grilling
status: closed
assignee: omp (Eduardo Dalcin)
blocked_by: []
---

## Question

O app usa o login do PKD (sessão por cookie de 30 dias, CSRF, 2FA por e-mail) ou um Bearer token dedicado? Se for token: como ele é emitido, revogado e guardado no aparelho? Hoje `POST /api/import` só aceita Bearer, e `POST /api/chat` só aceita sessão.

## Resolution

2026-09-27 — Decidido com o usuário (grilling). Fatos do `../pkd`: single-user (`PKD_PASSWORD`); sessão `pkd_session` 30 dias idle, `Secure=false`; CSRF double-submit; 2FA por e-mail ligada a aparelho (`pkd_device`, 10 anos); Bearer `PKD_IMPORT_TOKEN` estático, só `/api/import`, `/api/notes`, `/api/memories`; sem revogação remota de sessões.

1. **Autenticação:** login do PKD por sessão (senha → 2FA se aparelho novo → cookies `pkd_session` + `pkd_device`; `X-CSRF-Token` nas mutações). Sem Bearer na v1: o token não cobre Documentos nem busca. Token por aparelho só se surgir necessidade (widget, sync em background).
2. **Transporte:** só HTTPS. O app recusa URL `http://`.
3. **Servidor:** um servidor, uma sessão. Login pede URL + senha; trocar de URL exige logout.
4. **Armazenamento:** só os cookies, cifrados (Android Keystore). A senha nunca é guardada; sessão expirada pede a senha de novo.
5. **Bloqueio local:** biometria/PIN opcional, desligada por padrão. O share intent não passa pelo bloqueio.
6. **Revogação:** mudança aditiva no PKD — botão "Encerrar as outras sessões" na Administração da PWA (apaga todas as sessões menos a atual). Pré-requisito da v1 no backend.

Sem ADR: nenhuma decisão atende aos três critérios.
