import './style.css'
import { injectCSS, applyLayoutFix, getInjectionVersion } from './injection'
import { setCache } from './dexie'

const WA_URL = 'https://web.whatsapp.com'

async function init(): Promise<void> {
  const splash = document.getElementById('splash')
  const app = document.getElementById('app')

  if (!app) return

  const iframe = document.createElement('iframe')
  iframe.src = WA_URL
  iframe.style.cssText = 'width:100%;height:100%;border:none;'
  iframe.allow = 'camera; microphone; autoplay'

  iframe.onload = () => {
    if (splash) splash.classList.add('hidden')
    injectCSS()
    applyLayoutFix()
    setCache('config:injectionVersion', getInjectionVersion())
  }

  app.appendChild(iframe)
}

init()
