<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { loadAMap } from '@/utils/amap'
import { currentLocation } from '@/utils/location'
import { setWeather } from '@/utils/weather'

const loading = ref(true)
const error = ref<string | null>(null)

const city = ref('')
const weatherText = ref('')
const temperature = ref('')
const windDirection = ref('')
const windPower = ref('')
const humidity = ref('')
const reportTime = ref('')

let weatherInstance: any = null
async function ensureWeather() {
  if (weatherInstance) return
  const AMap = await loadAMap('weather')
  await new Promise<void>((resolve) => { AMap.plugin(['AMap.Weather'], () => resolve()) })
  weatherInstance = new AMap.Weather()
}

async function queryWeather(target: string) {
  await ensureWeather()
  await new Promise<void>((resolve) => {
    const cb = (a: any, b: any) => {
      let ok = false
      let data: any = null
      if (typeof a === 'string') { ok = a === 'complete'; data = b }
      else { ok = !a; data = b }
      if (ok && data) {
        city.value = data.city || city.value
        weatherText.value = data.weather || ''
        temperature.value = data.temperature ? `${data.temperature}℃` : ''
        windDirection.value = data.windDirection || ''
        windPower.value = data.windPower ? `${data.windPower}级` : ''
        humidity.value = data.humidity ? `${data.humidity}%` : ''
        reportTime.value = data.reportTime || ''
        setWeather({
          city: city.value,
          text: weatherText.value,
          temperature: temperature.value,
          windDirection: windDirection.value,
          windPower: windPower.value,
          humidity: humidity.value,
          reportTime: reportTime.value,
        })
        error.value = null
      } else {
        error.value = '获取实况天气失败'
      }
      resolve()
    }
    try { weatherInstance.getLive(target, cb) } catch { error.value = '天气服务异常'; resolve() }
  })
}

async function init() {
  try {
    const target = currentLocation.adcode || currentLocation.city
    if (target) {
      loading.value = true
      await queryWeather(target)
      loading.value = false
    } else {
      loading.value = false
    }
    watch(() => currentLocation.updatedAt, async () => {
      const t = currentLocation.adcode || currentLocation.city
      if (!t) return
      loading.value = true
      await queryWeather(t)
      loading.value = false
    })
  } catch (e: any) {
    error.value = e?.message || '初始化失败'
    loading.value = false
  }
}

onMounted(() => { init() })
</script>

<template>
  <div class="widget">
    <h3 class="section-title">天气</h3>
    <div v-if="loading" class="meta">加载中...</div>
    <div v-else-if="error" class="meta error">{{ error }}</div>
    <div v-else class="meta">
      <div class="row"><strong>{{ city }}</strong> · {{ reportTime }}</div>
      <div class="row big">{{ weatherText }} · {{ temperature }}</div>
      <div class="row">风向 {{ windDirection }} · 风力 {{ windPower }} · 湿度 {{ humidity }}</div>
    </div>
  </div>
</template>

<style scoped>
.widget { padding: 16px; }
.meta { color: #2b3a55; font-size: 14px; }
.meta.error { color: #b63c2d; }
.row { margin: 6px 0; }
.row.big { font-size: 1.25rem; font-weight: 700; color: #25c46b; }
</style>
