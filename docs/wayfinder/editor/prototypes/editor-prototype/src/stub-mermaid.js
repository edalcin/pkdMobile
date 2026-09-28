// PROTOTYPE stub for the `mermaid` package (84 MB uncompressed, see docs/research/bundle-editor.md
// §2). Renders the source as a <pre> instead of an SVG diagram — the MermaidCodeBlock node schema
// and toggle UI stay intact, so a Mermaid block still round-trips through HTML unchanged.
function escapeHTML(str) {
  return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export default {
  initialize() {},
  async render(_id, source) {
    return { svg: `<pre class="mermaid-stub">${escapeHTML(source)}</pre>` }
  },
}
