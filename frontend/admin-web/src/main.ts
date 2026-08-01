import { createApp } from 'vue'
import { createPinia } from 'pinia'
// Element Plus 组件、指令与样式由 vite 插件按需自动引入（见 vite.config.ts），
// 不再全量 import ElementPlus + app.use(ElementPlus)。
// Global styles + design tokens (--pc-* variables)。保持在该处以覆盖 EP 默认 token。
import './style.css'
// 按需引入补丁：unplugin-vue-components 只能自动注入「模板中出现的组件」的样式，
// 但 ElMessage / ElMessageBox / ElNotification / ElLoading 是通过 JS API 调用的
// （见 utils/feedback.ts），它们的样式不会被自动导入，需要显式引入，否则会丢失
// 居中、宽度限制、遮罩定位等关键样式（表现为确认框瘫在左上角并铺满宽度）。
import 'element-plus/es/components/message/style/css'
import 'element-plus/es/components/message-box/style/css'
import 'element-plus/es/components/notification/style/css'
import 'element-plus/es/components/loading/style/css'
import router from './router'
import App from './App.vue'

const app = createApp(App)
const pinia = createPinia()

app.use(pinia)
app.use(router)

app.mount('#app')
