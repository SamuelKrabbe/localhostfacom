import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './styles/global.css'
import App from './App.tsx'
import { wordmarkText } from './lib/wordmark.ts'

// The year in the wordmark is current, so the tab title cannot be static in index.html.
document.title = wordmarkText()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
