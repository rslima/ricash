import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './i18n'
import App from './App.tsx'
import { initNative } from './lib/native-init'
import { installChunkReloadHandler } from './lib/chunk-reload'
import { purgeLegacyApiCache } from './lib/purge-api-cache'

initNative()
installChunkReloadHandler()
void purgeLegacyApiCache()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
