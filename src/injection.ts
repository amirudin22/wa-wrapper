const INJECTION_VERSION = 'v1.2.0'

const CSS = `
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
div[data-testid="conversation-panel"]:not([style*="display: none"]) {
  transform: translateX(0) !important;
}
header, header[data-testid="sidebar-search"],
div[data-testid="conversation-header"] {
  background: #075E54 !important;
  color: white !important;
}
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
footer[data-testid="conversation-footer"] {
  background: #1f2c33 !important;
  border-top: 1px solid #2a3942 !important;
}
`

export function injectCSS(): void {
  const existing = document.getElementById('wa-wrapper-css')
  if (existing) return

  const style = document.createElement('style')
  style.id = 'wa-wrapper-css'
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

    const parentFlex = panel.parentElement
    if (parentFlex) {
      parentFlex.style.display = 'block'
    }
  }

  enforceLayout()
  setInterval(enforceLayout, 2000)
}

export function getInjectionVersion(): string {
  return INJECTION_VERSION
}
