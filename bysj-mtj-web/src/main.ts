import './assets/main.css'

import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import { loadAMap } from '@/utils/amap'

const app = createApp(App)

app.use(router)
app.use(ElementPlus)

;(async () => {
  try {
    await loadAMap('geo')
  } catch {}
  app.mount('#app')
})()
