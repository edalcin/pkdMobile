---
title: Como o app autentica no PKD?
type: wayfinder:grilling
status: open
assignee:
blocked_by: []
---

## Question

O app usa o login do PKD (sessão por cookie de 30 dias, CSRF, 2FA por e-mail) ou um Bearer token dedicado? Se for token: como ele é emitido, revogado e guardado no aparelho? Hoje `POST /api/import` só aceita Bearer, e `POST /api/chat` só aceita sessão.
