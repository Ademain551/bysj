<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { apiFetch } from '@/utils/api'
import { currentLocation } from '@/utils/location'
import { currentWeather } from '@/utils/weather'

type WarnItem = {
  plantName: string
  diseaseName: string
  preventionMethod: string
  match: any
}

const loading = ref(false)
const error = ref<string | null>(null)
const items = ref<WarnItem[]>([])

function areaString() {
  return (currentLocation.city || currentLocation.address || '').trim()
}

function monthNumber() {
  return new Date().getMonth() + 1
}

async function load() {
  const area = areaString()
  const params = new URLSearchParams()
  if (area) params.set('area', area)
  params.set('month', String(monthNumber()))
  if (currentWeather.text) params.set('weather', currentWeather.text)
  params.set('limit', '6')
  loading.value = true
  error.value = null
  try {
    const res = await apiFetch('/warn/diseases', { params })
    if (!res.ok) throw new Error('加载预警失败')
    const data = await res.json()
    items.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    error.value = e?.message || '加载失败'
    items.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  if (areaString()) load()
})

watch(() => currentLocation.updatedAt, () => { load() })
watch(() => currentWeather.updatedAt, () => { if (items.value.length === 0) load() })
</script>

<template>
  <div class="widget">
    <h3 class="section-title">病害预警</h3>
    <div v-if="loading" class="meta">分析中...</div>
    <div v-else-if="error" class="meta error">{{ error }}</div>
    <ul v-else class="list">
      <li v-for="(it, idx) in items" :key="idx" class="row">
        <div class="title">{{ it.diseaseName }}<span class="plant" v-if="it.plantName"> · {{ it.plantName }}</span></div>
        <div class="method">预防：{{ it.preventionMethod || '暂无' }}</div>
      </li>
      <li v-if="!items.length" class="row">暂无匹配预警</li>
    </ul>
  </div>
</template>

<style scoped>
.widget { padding: 16px; }
.meta { color: #2b3a55; font-size: 14px; }
.meta.error { color: #b63c2d; }
.list { margin: 0; padding: 0; list-style: none; }
.row { margin: 8px 0; }
.title { font-weight: 700; color: #25c46b; }
.plant { color: #387fc4; font-weight: 600; }
.method { margin-top: 4px; color: #3b3f4a; font-size: 13.5px; }
</style>
