<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { loadAMap } from '@/utils/amap'
import { setLocation } from '@/utils/location'

const loading = ref(true)
const error = ref<string | null>(null)
const address = ref('')
const position = ref<{ lng: number; lat: number } | null>(null)
const accuracy = ref<number | null>(null)

const mapEl = ref<HTMLElement | null>(null)
let map: any = null

async function init() {
  try {
    const AMap = await loadAMap('geo')
    await new Promise<void>((resolve) => {
      AMap.plugin(['AMap.Geolocation', 'AMap.Geocoder'], () => resolve())
    })
    const geolocation = new AMap.Geolocation({
      enableHighAccuracy: true,
      timeout: 10000,
      showCircle: false,
      showButton: false,
      zoomToAccuracy: true,
    })
    geolocation.getCurrentPosition((status: any, result: any) => {
      if (status === 'complete') {
        const pos = result.position || result.lnglat
        if (pos) {
          position.value = { lng: pos.lng, lat: pos.lat }
          setLocation({ lng: pos.lng, lat: pos.lat })
          accuracy.value = result.accuracy || null
          const geocoder = new AMap.Geocoder()
          geocoder.getAddress([pos.lng, pos.lat], (s: any, r: any) => {
            if (s === 'complete' && r?.regeocode?.formattedAddress) {
              address.value = r.regeocode.formattedAddress
              const ac = r.regeocode.addressComponent || {}
              const adcode = ac.adcode || ''
              let cityName = ac.city || ''
              if (!cityName) cityName = ac.province || ''
              setLocation({ address: address.value, adcode, city: cityName })
            }
            try {
              if (!mapEl.value) throw new Error('map container not ready')
              map = new AMap.Map(mapEl.value, { center: [pos.lng, pos.lat], zoom: 15, resizeEnable: false })
              if (map && typeof map.setStatus === 'function') {
                map.setStatus({ dragEnable: false, zoomEnable: false, doubleClickZoom: false, keyboardEnable: false })
              }
              const marker = new AMap.Marker({ position: [pos.lng, pos.lat] })
              map.add(marker)
            } catch {}
            loading.value = false
          })
          return
        }
      }
      error.value = '定位失败'
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
    <h3 class="section-title">地理位置</h3>
    <div ref="mapEl" class="map"></div>
    <div v-if="loading" class="meta">定位中...</div>
    <div v-else-if="error" class="meta error">{{ error }}</div>
    <div v-else class="meta">
      <div class="row">{{ address }}</div>
      <div class="row">经度 {{ position?.lng?.toFixed(6) }} · 纬度 {{ position?.lat?.toFixed(6) }}<span v-if="accuracy"> · 精度 ±{{ Math.round(accuracy) }}m</span></div>
    </div>
  </div>
</template>

<style scoped>
.widget { padding: 16px; }
.meta { color: #2b3a55; font-size: 14px; }
.meta.error { color: #b63c2d; }
.row { margin: 6px 0; }
.map { width: 100%; height: 180px; border-radius: 14px; box-shadow: 0 2px 10px #00000012; margin-bottom: 8px; }
</style>
