# Protótipo do editor — fonte primária (throwaway)

Ticket: [Como o editor se comporta no celular?](../tickets/02-prototype-editor.md). O protótipo saiu do app depois do veredito. A fonte fica aqui só como registro.

- `editor-prototype/build.mjs` + `src/` + `editor.html`: bundle TipTap com as extensões do `../pkd/frontend` sem mudança (esbuild do `pkd/frontend/node_modules`, stubs para `apiGet` e `mermaid`). `node build.mjs` gera `dist/`.
- `editor-prototype/EditorPrototypeActivity.kt`: a tela de teste (variantes A/B, título, "Carregar amostra", "Ver HTML").

Para rodar de novo:
1. Copie a Activity para `app/src/debug/java/in/dalc/pkdmobile/` e declare-a em `app/src/debug/AndroidManifest.xml` (`exported="true"`, sem launcher).
2. Rode `node build.mjs` e copie `dist/*` para `app/src/debug/assets/editor-prototype/`.
3. Build de debug (ver `docs/proximosPassos.md` → "Como trabalhar") e `adb shell am start -n in.dalc.pkdmobile/.EditorPrototypeActivity`.
