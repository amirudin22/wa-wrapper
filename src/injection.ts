const INJECTION_VERSION = 'v1.0.0'

const CSS = `
div[data-testid="conversation-panel"] {
  max-width: 100% !important;
  flex: 1 !important;
}
div[data-testid="sidebar"] {
  width: 100% !important;
  max-width: 100% !important;
}
header[data-testid="sidebar-search"],
div[data-testid="chat-list"] {
  width: 100% !important;
}
html {
  font-size: 14px;
}
@media (max-width: 480px) {
  html { font-size: 12px; }
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

export function setupMutationObserver(): void {
  const observer = new MutationObserver(() => {
    const panel = document.querySelector('[data-testid="conversation-panel"]')
    if (panel && panel.getBoundingClientRect().width > window.innerWidth) {
      panel.setAttribute('style', 'max-width: 100vw !important')
    }
  })

  observer.observe(document.body, {
    childList: true,
    subtree: true,
    attributes: true,
  })
}

const knownSelectors = [
  '[data-testid="conversation-panel"]',
  '[data-testid="sidebar"]',
]

export function startDOMWatchdog(): void {
  setInterval(() => {
    for (const sel of knownSelectors) {
      if (!document.querySelector(sel)) {
        console.warn(`[Injection] Selector not found: ${sel}`)
      }
    }
  }, 30000)
}

export function getInjectionVersion(): string {
  return INJECTION_VERSION
}
