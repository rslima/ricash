import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './i18n'
import App from './App.tsx'
import { initNative } from './lib/native-init'

initNative()

// One-time cleanup: earlier releases cached authenticated /api/v1 responses
// in the service worker; purge that cache from already-installed PWAs.
if ('caches' in window) {
  caches.delete('api-cache').catch(() => {})
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
