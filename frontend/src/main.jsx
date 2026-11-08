import React from 'react'
import ReactDOM from 'react-dom/client'
import './i18n/index.js'
import App from './App.jsx'
import './index.css'
import './design-system/tokens.css'
import './design-system/themes.css'
import './design-system/materials.css'
import './design-system/motion.css'

ReactDOM.createRoot(document.getElementById('root')).render(
    <React.StrictMode>
        <App />
    </React.StrictMode>,
)