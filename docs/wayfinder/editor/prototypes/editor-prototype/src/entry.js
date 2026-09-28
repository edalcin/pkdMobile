// PROTOTYPE — throwaway. Bootstraps the real PKD TipTap extensions in a plain WebView page.
// Imports straight from S:/git/pkd (never edited); the two PWA couplings (apiGet, mermaid) are
// redirected to stubs in build.mjs. See docs/wayfinder/editor/prototypes/README-editor-prototype.md.
import { Editor } from '@tiptap/core'
import { StarterKit } from '@tiptap/starter-kit'
import { Table, TableRow, TableCell, TableHeader } from '@tiptap/extension-table'
import { TaskList, TaskItem } from '@tiptap/extension-list'
import { Highlight } from '@tiptap/extension-highlight'
import { TextAlign } from '@tiptap/extension-text-align'
import { NodeSelection } from '@tiptap/pm/state'
import { ResizableImage } from 'S:/git/pkd/frontend/src/lib/editor/resizable-image-extension.js'
import { DocLink } from 'S:/git/pkd/frontend/src/lib/editor/doclink-extension.js'
import { MermaidCodeBlock } from 'S:/git/pkd/frontend/src/lib/editor/mermaid-code-block.js'

const bridge = () => window.PrototypeBridge
const qs = new URLSearchParams(location.search)

// Theme arrives as a query param set by the Activity (a <script> at the top of editor.html
// already applies it before first paint). The A/B toolbar variant is switched at runtime via
// window.setToolbarMode so flipping the chip doesn't reload the WebView and lose content.
function applyToolbarMode(showWeb) {
  document.getElementById('web-toolbar').hidden = !showWeb
  document.body.classList.toggle('has-web-toolbar', showWeb)
  if (showWeb) pinToolbar()
}
window.setToolbarMode = (mode) => applyToolbarMode(mode === 'web')
applyToolbarMode(qs.get('bar') === 'web')

const editor = new Editor({
  element: document.getElementById('editor-root'),
  extensions: [
    StarterKit.configure({
      codeBlock: false,
      link: {
        // ponytail: openOnClick:false deviates from Editor.svelte:1128 on purpose — map.md's
        // decision is "tocar num link mostra o balão", not navigate away from the app.
        openOnClick: false,
        HTMLAttributes: { target: '_blank', rel: 'noopener noreferrer' },
      },
    }),
    MermaidCodeBlock,
    ResizableImage.configure({ inline: true, allowBase64: true }),
    TaskList,
    TaskItem.configure({ nested: true }),
    Table.configure({ resizable: false }),
    TableRow,
    TableCell,
    TableHeader,
    Highlight.configure({ multicolor: true }),
    TextAlign.configure({ types: ['heading', 'paragraph'] }),
    DocLink,
  ],
  content: '',
  editorProps: {
    attributes: { class: 'ProseMirror', 'data-placeholder': 'Comece a escrever…' },
  },
  onTransaction: () => {
    reportState()
    updateBubble()
  },
  onUpdate: scheduleChangeReport,
})

// ---- window API for the Android bridge ----------------------------------------------------

window.setContent = (html) => {
  editor.commands.setContent(html || '<p></p>', { emitUpdate: false })
}
window.getHTML = () => editor.getHTML()

const COMMANDS = {
  h1: () => editor.chain().focus().toggleHeading({ level: 1 }).run(),
  h2: () => editor.chain().focus().toggleHeading({ level: 2 }).run(),
  h3: () => editor.chain().focus().toggleHeading({ level: 3 }).run(),
  bullet: () => editor.chain().focus().toggleBulletList().run(),
  ordered: () => editor.chain().focus().toggleOrderedList().run(),
  task: () => editor.chain().focus().toggleTaskList().run(),
  bold: () => editor.chain().focus().toggleBold().run(),
  italic: () => editor.chain().focus().toggleItalic().run(),
  link: () => openLinkPrompt(editor.getAttributes('link').href || ''),
  undo: () => editor.chain().focus().undo().run(),
  redo: () => editor.chain().focus().redo().run(),
}
window.editorCommand = (name) => COMMANDS[name]?.()

let changeTimer = null
function scheduleChangeReport() {
  clearTimeout(changeTimer)
  changeTimer = setTimeout(() => bridge()?.onChange?.(editor.getHTML()), 500)
}

function reportState() {
  bridge()?.onState?.(
    JSON.stringify({
      h1: editor.isActive('heading', { level: 1 }),
      h2: editor.isActive('heading', { level: 2 }),
      h3: editor.isActive('heading', { level: 3 }),
      bullet: editor.isActive('bulletList'),
      ordered: editor.isActive('orderedList'),
      task: editor.isActive('taskList'),
      bold: editor.isActive('bold'),
      italic: editor.isActive('italic'),
      link: editor.isActive('link'),
      undo: editor.can().undo(),
      redo: editor.can().redo(),
    }),
  )
}

// ---- link bubble (Abrir · Editar · Remover) ------------------------------------------------

const bubbleEl = document.getElementById('link-bubble')
const bubbleOpenBtn = document.getElementById('bubble-open')
const bubbleEditBtn = document.getElementById('bubble-edit')
const bubbleRemoveBtn = document.getElementById('bubble-remove')
let bubbleTarget = null // { kind: 'link'|'docLink', href?, docId? }

function updateBubble() {
  const { state } = editor
  const { selection } = state
  let target = null

  if (selection instanceof NodeSelection && selection.node.type.name === 'docLink') {
    target = { kind: 'docLink', docId: selection.node.attrs.docId }
  } else if (editor.isActive('link')) {
    target = { kind: 'link', href: editor.getAttributes('link').href || '' }
  }

  bubbleTarget = target
  if (!target) {
    bubbleEl.hidden = true
    return
  }

  bubbleEditBtn.hidden = target.kind !== 'link'
  const coords = editor.view.coordsAtPos(selection.from)
  bubbleEl.hidden = false
  const half = bubbleEl.offsetWidth / 2
  bubbleEl.style.left = `${Math.max(8, coords.left - half)}px`
  bubbleEl.style.top = `${Math.max(8, coords.top - bubbleEl.offsetHeight - 8)}px`
}

bubbleOpenBtn.onclick = () => {
  if (!bubbleTarget) return
  if (bubbleTarget.kind === 'link') bridge()?.openExternal?.(bubbleTarget.href)
  else bridge()?.openDocLink?.(String(bubbleTarget.docId))
  bubbleEl.hidden = true
}
bubbleEditBtn.onclick = async () => {
  if (!bubbleTarget || bubbleTarget.kind !== 'link') return
  const url = await promptURL(bubbleTarget.href)
  if (url === null) return
  const chain = editor.chain().focus().extendMarkRange('link')
  if (url) chain.setLink({ href: url }).run()
  else chain.unsetLink().run()
  bubbleEl.hidden = true
}
bubbleRemoveBtn.onclick = () => {
  if (!bubbleTarget) return
  if (bubbleTarget.kind === 'link') editor.chain().focus().extendMarkRange('link').unsetLink().run()
  else editor.chain().focus().deleteSelection().run()
  bubbleEl.hidden = true
}

// ---- URL prompt (custom overlay — Android WebView has no window.prompt by default) --------

const promptOverlay = document.getElementById('prompt-overlay')
const promptInput = document.getElementById('prompt-input')
let promptResolve = null

function openLinkPrompt(initial) {
  promptURL(initial).then((url) => {
    if (url === null) return
    const chain = editor.chain().focus().extendMarkRange('link')
    if (url) chain.setLink({ href: url }).run()
    else chain.unsetLink().run()
  })
}

function promptURL(initial) {
  promptInput.value = initial || ''
  promptOverlay.hidden = false
  promptInput.focus()
  promptInput.select()
  return new Promise((resolve) => {
    promptResolve = resolve
  })
}
document.getElementById('prompt-ok').onclick = () => {
  promptOverlay.hidden = true
  promptResolve?.(promptInput.value.trim())
}
document.getElementById('prompt-cancel').onclick = () => {
  promptOverlay.hidden = true
  promptResolve?.(null)
}
promptInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') document.getElementById('prompt-ok').click()
  if (e.key === 'Escape') document.getElementById('prompt-cancel').click()
})

// ---- DocLink navigation (doclink-extension.js sets window.location.hash on click) ---------

window.addEventListener('hashchange', () => {
  const m = /^#\/doc\/(\d+)$/.exec(location.hash)
  if (m) bridge()?.openDocLink?.(m[1])
  history.replaceState(null, '', location.pathname + location.search)
})

// ---- web toolbar (variant B) ---------------------------------------------------------------
// Listeners are wired unconditionally (cheap, idempotent); applyToolbarMode() above and
// window.setToolbarMode() at runtime control visibility only.

document.querySelectorAll('#web-toolbar [data-cmd]').forEach((btn) => {
  btn.addEventListener('mousedown', (e) => e.preventDefault()) // keep editor focus/selection
  btn.addEventListener('click', () => COMMANDS[btn.dataset.cmd]?.())
})
const updateToolbarActiveState = () => {
  document.querySelectorAll('#web-toolbar [data-cmd]').forEach((btn) => {
    const cmd = btn.dataset.cmd
    const active =
      cmd === 'h1' ? editor.isActive('heading', { level: 1 })
      : cmd === 'h2' ? editor.isActive('heading', { level: 2 })
      : cmd === 'h3' ? editor.isActive('heading', { level: 3 })
      : cmd === 'bullet' ? editor.isActive('bulletList')
      : cmd === 'ordered' ? editor.isActive('orderedList')
      : cmd === 'task' ? editor.isActive('taskList')
      : cmd === 'bold' ? editor.isActive('bold')
      : cmd === 'italic' ? editor.isActive('italic')
      : cmd === 'link' ? editor.isActive('link')
      : false
    btn.classList.toggle('active', !!active)
  })
}
editor.on('transaction', updateToolbarActiveState)

// Pin the web toolbar above the on-screen keyboard using visualViewport (the WebView's
// layout viewport doesn't shrink when the IME opens, only visualViewport does).
const webToolbarEl = document.getElementById('web-toolbar')
function pinToolbar() {
  const vv = window.visualViewport
  if (!vv) return
  const keyboardGap = window.innerHeight - vv.height - vv.offsetTop
  webToolbarEl.style.bottom = `${Math.max(0, keyboardGap)}px`
}
window.visualViewport?.addEventListener('resize', pinToolbar)
window.visualViewport?.addEventListener('scroll', pinToolbar)
pinToolbar()

window.PROTOTYPE_READY = true
