import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 组件、指令与样式由 vite 插件按需自动引入（见 vite.config.ts），
// 不再全量 import ElementPlus + app.use(ElementPlus)。
// Global styles + design tokens (--pc-* variables)。保持在该处以覆盖 EP 默认 token。
import './style.css'
import router from './router'
import App from './App.vue'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

app.mount('#app')
