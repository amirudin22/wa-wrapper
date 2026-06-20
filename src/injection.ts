const INJECTION_VERSION = 'v1.1.0'

const CSS = `
/* === SINGLE-COLUMN MOBILE LAYOUT === */
/* Force sidebar dan chat panel full layar */
div[data-testid="sidebar"] {
  width: 100vw !important;
  max-width: 100vw !important;
  min-width: 100vw !important;
  flex: none !important;
}

div[data-testid="conversation-panel"] {
  width: 100vw !important;
  max-width: 100vw !important;
  min-width: 100vw !important;
  flex: none !important;
  position: fixed !important;
  top: 0 !important;
  left: 0 !important;
  bottom: 0 !important;
  z-index: 100 !important;
  transform: translateX(100%) !important;
  transition: transform 0.25s ease !important;
}

/* Saat chat aktif, geser masuk dari kanan */
div[data-testid="conversation-panel"]:not([style*="display: none"]) {
  transform: translateX(0) !important;
}

/* Container utama-flex jadi block */
div[data-testid="sidebar"] ~ div[data-testid="conversation-panel"],
div[data-testid="conversation-panel"] ~ div[data-testid="sidebar"] {
  /* sibling handling */
}

/* Force parent container jadi vertikal */
div[data-testid="sidebar"]:not([style*="display: none"]) {
  display: flex !important;
}

/* === HEADER / TOP BAR === */
header[data-testid="sidebar-search"],
header:has(div[data-testid="conversation-info"]),
header:has(div[data-testid="chat-header"]) {
  background: #075E54 !important;
  color: white !important;
}

header[data-testid="sidebar-search"] span,
header[data-testid="sidebar-search"] div[data-testid="chat-list-search"],
header[data-testid="sidebar-search"] * {
  color: white !important;
}

/* Search input styling */
div[data-testid="chat-list-search"] input,
div[data-testid="search input"] input {
  border-radius: 24px !important;
  background: rgba(255,255,255,0.15) !important;
  color: white !important;
}

div[data-testid="chat-list-search"] input::placeholder {
  color: rgba(255,255,255,0.6) !important;
}

/* === CHAT LIST === */
div[data-testid="cell-frame-container"],
div[data-testid="conversation-info"] {
  padding: 8px 16px !important;
}

/* Avatar lebih natural */
img[data-testid="conversation-icon"],
[data-testid="conversation-info-photo"] img {
  border-radius: 50% !important;
  object-fit: cover !important;
}

/* Unread badge styling */
span[data-testid="icon-unread-count"],
span[data-testid="pills-unread-count"] {
  background: #00a884 !important;
  font-size: 11px !important;
  min-width: 20px !important;
  height: 20px !important;
  border-radius: 10px !important;
  display: inline-flex !important;
  align-items: center !important;
  justify-content: center !important;
}

/* === FLOATING ACTION BUTTON (New Chat) === */
button[data-testid="conversation-new"],
button[data-testid="new-chat-button"] {
  position: fixed !important;
  bottom: 24px !important;
  right: 24px !important;
  width: 56px !important;
  height: 56px !important;
  border-radius: 50% !important;
  background: #00a884 !important;
  box-shadow: 0 4px 12px rgba(0,0,0,0.3) !important;
  z-index: 50 !important;
}

/* === CHAT PANEL === */
div[data-testid="conversation-header"],
header[data-testid="conversation-header"] {
  background: #075E54 !important;
  color: white !important;
}

/* Message input bar */
footer[data-testid="conversation-footer"],
div[data-testid="conversation-footer"] {
  background: #1f2c33 !important;
  border-top: 1px solid #2a3942 !important;
}

footer[data-testid="conversation-footer"] div[contenteditable="true"],
div[data-testid="conversation-footer"] div[contenteditable="true"] {
  border-radius: 24px !important;
  background: #2a3942 !important;
  color: #e9edef !important;
  padding: 10px 16px !important;
}

/* === OVERALL TWEAKS === */
/* Font lebih kecil untuk mobile */
html, body, div, span, p, a {
  font-size: 14px !important;
}

/* Responsive font */
@media (max-width: 400px) {
  html { font-size: 12px !important; }
}

/* Hapus gap/scroll horizontal */
.app-wrapper-web .app,
.app-wrapper-web,
#app {
  overflow-x: hidden !important;
}

/* Background seluruh app */
.app-wrapper-web,
.app-wrapper-web .app {
  background: #111b21 !important;
}

/* Emoji panel mobile-friendly */
div[role="dialog"][data-testid="emoji-panel"] {
  max-height: 40vh !important;
  bottom: 0 !important;
  top: auto !important;
  border-radius: 16px 16px 0 0 !important;
}

/* Attachment menu */
div[data-testid="attachment-panel"] {
  border-radius: 16px 16px 0 0 !important;
}
`

export function injectCSS(): void {
  const existing = document.getElementById('wa-injection-css')
  if (existing) return

  const style = document.createElement('style')
  style.id = 'wa-injection-css'
  style.textContent = CSS
  document.head.appendChild(style)
}

export function applyLayoutFix(): void {
  const enforceLayout = () => {
    const sidebar = document.querySelector('[data-testid="sidebar"]') as HTMLElement | null
    const panel = document.querySelector('[data-testid="conversation-panel"]') as HTMLElement | null

    if (!sidebar || !panel) return

    sidebar.style.width = '100vw'
    sidebar.style.maxWidth = '100vw'
    sidebar.style.minWidth = '100vw'
    sidebar.style.flex = 'none'

    panel.style.width = '100vw'
    panel.style.maxWidth = '100vw'
    panel.style.minWidth = '100vw'
    panel.style.flex = 'none'
    panel.style.position = 'fixed'
    panel.style.top = '0'
    panel.style.left = '0'
    panel.style.bottom = '0'
    panel.style.zIndex = '100'

    // Chat panel visible? geser ke posisi
    const isPanelVisible = panel.style.display !== 'none' &&
      window.getComputedStyle(panel).display !== 'none'

    if (isPanelVisible) {
      panel.style.transform = 'translateX(0)'
    }

    // Jika ada chat yg diklik, pastikan panel fullscreen
    const parentFlex = panel.parentElement
    if (parentFlex) {
      parentFlex.style.display = 'block'
    }
  }

  enforceLayout()
  setInterval(enforceLayout, 2000)
}

export function setupMutationObserver(): void {
  const observer = new MutationObserver(() => {
    applyLayoutFix()
  })

  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['style', 'class', 'data-testid'],
  })
}

const knownSelectors = [
  '[data-testid="conversation-panel"]',
  '[data-testid="sidebar"]',
  '[data-testid="sidebar-search"]',
]

export function startDOMWatchdog(): void {
  setInterval(() => {
    for (const sel of knownSelectors) {
      if (!document.querySelector(sel)) {
        console.warn(`[Injection] Selector not found: ${sel}`)
      }
    }
    applyLayoutFix()
  }, 10000)
}

export function getInjectionVersion(): string {
  return INJECTION_VERSION
}
