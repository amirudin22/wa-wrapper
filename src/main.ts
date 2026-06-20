import './style.css'
import { setCache, getCache, clearExpired } from './dexie'
import { injectCSS, setupMutationObserver, startDOMWatchdog, getInjectionVersion } from './injection'

const WA_URL = 'https://web.whatsapp.com'

async function init(): Promise<void> {
  clearExpired()

  const splash = document.getElementById('splash')
  const app = document.getElementById('app')

  if (!app) return

  // Restore theme preference from cache
  const theme = await getCache<string>('ui:theme')
  if (theme) {
    document.documentElement.setAttribute('data-theme', theme)
  }

  // Inject CSS immediately to prevent flicker
  injectCSS()

  // Create iframe to load WhatsApp Web
  const iframe = document.createElement('iframe')
  iframe.src = WA_URL
  iframe.style.cssText = 'width:100%;height:100%;border:none;'
  iframe.allow = 'camera; microphone; autoplay'

  iframe.onload = () => {
    // Hide splash
    if (splash) splash.classList.add('hidden')

    // Setup injection on load
    injectCSS()
    setupMutationObserver()
    startDOMWatchdog()

    // Save injection version
    setCache('config:injectionVersion', getInjectionVersion())
  }

  app.appendChild(iframe)
}

init()
