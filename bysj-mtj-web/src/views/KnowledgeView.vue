<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { apiFetch } from '@/utils/api'

interface DiseaseInfo {
  id: number
  plant: string
  name: string
  modelLabel: string
  description?: string
  advice?: string
}

const loading = ref(false)
const error = ref('')
const diseases = ref<DiseaseInfo[]>([])
const activeTab = ref<'disease' | 'bhxx'>('disease')
const searchKeyword = ref('')

const grouped = computed(() => {
  const map = new Map<string, DiseaseInfo[]>()
  for (const d of diseases.value) {
    if (!map.has(d.plant)) map.set(d.plant, [])
    map.get(d.plant)!.push(d)
  }
  return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0]))
})

const filteredGrouped = computed(() => {
  const k = searchKeyword.value.trim().toLowerCase()
  if (!k) return grouped.value
  return grouped.value
    .map(([plant, list]) => {
      const filtered = list.filter((item) => {
        const plantName = (plant || '').toLowerCase()
        const name = (item.name || '').toLowerCase()
        const label = (item.modelLabel || '').toLowerCase()
        return plantName.includes(k) || name.includes(k) || label.includes(k)
      })
      return [plant, filtered] as [string, DiseaseInfo[]]
    })
    .filter(([, list]) => list.length > 0)
})

const diseaseCount = computed(() => diseases.value.length)

async function loadKnowledge() {
  loading.value = true
  error.value = ''
  try {
    const resp = await apiFetch('/knowledge/diseases')
    if (!resp.ok) {
      throw new Error('加载知识库失败')
    }
    const data = await resp.json()
    diseases.value = Array.isArray(data)
      ? data
          .map((item: any) => {
            const normalize = (v: any) => {
              const raw = (v || '').trim()
              return raw && raw !== '无' && raw !== '暂无' && raw !== '未知' ? raw : ''
            }
            const plant = normalize(item.plant)
            const name = normalize(item.name)
            const modelLabel = (item.modelLabel || '').trim()
            const description = normalize(item.description)
            const advice = normalize(item.advice)
            return {
              id: item.id,
              plant,
              name,
              modelLabel,
              description,
              advice,
            } as DiseaseInfo
          })
          // 过滤掉几乎全为空/“无”的记录，避免出现整张都是“无”的卡片
          .filter((d: DiseaseInfo) => {
            // 显式过滤“健康叶片”与“无植物”这类占位数据
            if (d.name === '健康叶片' || d.plant === '无植物') {
              return false
            }
            return !!d.name || !!d.modelLabel || !!d.description || !!d.advice
          })
      : []
  } catch (e: any) {
    error.value = e?.message || '知识库加载失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadKnowledge()
  loadBhxx()
})

function adviceText(d: DiseaseInfo): string {
  if (d.advice && d.advice.length > 0) return d.advice
  return '暂无防治建议，请咨询专家或根据田间实际情况制定方案。'
}

interface BhxxItem {
  id: number
  plantName: string
  diseaseName: string
  distributionArea: string
  distributionTime: string
  preventionMethod: string
  updatedAt?: string
}

const bhxxLoading = ref(false)
const bhxxError = ref('')
const bhxxItems = ref<BhxxItem[]>([])

const bhxxCount = computed(() => bhxxItems.value.length)

const filteredBhxx = computed(() => {
  const k = searchKeyword.value.trim().toLowerCase()
  if (!k) return bhxxItems.value
  return bhxxItems.value.filter((b) => {
    const plantName = (b.plantName || '').toLowerCase()
    const diseaseName = (b.diseaseName || '').toLowerCase()
    const area = (b.distributionArea || '').toLowerCase()
    return plantName.includes(k) || diseaseName.includes(k) || area.includes(k)
  })
})

async function loadBhxx() {
  bhxxLoading.value = true
  bhxxError.value = ''
  try {
    const resp = await apiFetch('/knowledge/bhxx')
    if (!resp.ok) throw new Error('加载分布知识失败')
    const data = await resp.json()
    bhxxItems.value = Array.isArray(data)
      ? data
          .map((it: any) => ({
          id: it.id,
          plantName: (() => {
            const raw = (it.plantName || '').trim()
            return raw && raw !== '无' && raw !== '未知' ? raw : ''
          })(),
          diseaseName: it.diseaseName || '',
          distributionArea: (() => {
            const raw = (it.distributionArea || '').trim()
            return raw && raw !== '无' && raw !== '暂无' && raw !== '未知' ? raw : ''
          })(),
          distributionTime: (() => {
            const raw = (it.distributionTime || '').trim()
            return raw && raw !== '无' && raw !== '暂无' && raw !== '未知' ? raw : ''
          })(),
          preventionMethod: (() => {
            const raw = (it.preventionMethod || '').trim()
            return raw && raw !== '无' && raw !== '暂无' && raw !== '未知' ? raw : ''
          })(),
          updatedAt: it.updatedAt,
        }))
          // 过滤掉“健康叶片”与“无植物”这类占位数据
          .filter((b: BhxxItem) => b.diseaseName !== '健康叶片' && b.plantName !== '无植物')
      : []
  } catch (e: any) {
    bhxxError.value = e?.message || '分布知识加载失败'
  } finally {
    bhxxLoading.value = false
  }
}
</script>

<template>
  <div class="knowledge-page">
    <section class="hero">
      <h1>植物病虫害知识库</h1>
      <p>
        收录模型可识别的 <strong>{{ diseaseCount }}</strong> 个病虫害类别，并关联
        <strong>{{ bhxxCount }}</strong> 条分布与防治知识，涵盖常见果蔬与粮食作物。
      </p>
    </section>

    <section class="toolbar">
      <div class="tabs">
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'disease' }"
          @click="activeTab = 'disease'"
        >
          病虫害知识
        </button>
        <button
          type="button"
          class="tab-btn"
          :class="{ active: activeTab === 'bhxx' }"
          @click="activeTab = 'bhxx'"
        >
          分布与防治
        </button>
      </div>
      <div class="toolbar-right">
        <div class="chips">
          <span class="chip">
            <span class="chip-label">病虫害</span>
            <span class="chip-value">{{ diseaseCount }}</span>
          </span>
          <span class="chip">
            <span class="chip-label">分布记录</span>
            <span class="chip-value">{{ bhxxCount }}</span>
          </span>
        </div>
        <input
          v-model="searchKeyword"
          class="search-input"
          type="search"
          placeholder="按作物、病害名称或标签快速检索"
        />
      </div>
    </section>

    <section v-if="activeTab === 'disease'">
      <section v-if="loading" class="state loading">正在加载知识库...</section>
      <section v-else-if="error" class="state error">{{ error }}</section>
      <section v-else class="catalog">
        <p v-if="!filteredGrouped.length" class="empty">
          暂无符合条件的病虫害知识，请调整检索条件或稍后再试。
        </p>
        <div
          v-else
          v-for="([plant, list], idx) in filteredGrouped"
          :key="plant + idx"
          class="plant-block"
        >
          <header class="plant-header">
            <h2>{{ plant }}</h2>
            <span class="meta">共 {{ list.length }} 种病虫害</span>
          </header>
          <div class="cards">
            <article v-for="item in list" :key="item.id" class="knowledge-card">
              <header class="card-head">
                <h3>{{ item.name }}</h3>
                <span v-if="item.modelLabel" class="tag">模型标签：{{ item.modelLabel }}</span>
              </header>
              <p class="desc">
                {{ item.description || '暂无详尽描述，可结合检测结果与实地观察判断。' }}
              </p>
              <p class="advice">{{ adviceText(item) }}</p>
            </article>
          </div>
        </div>
      </section>
    </section>

    <section v-else>
      <section v-if="bhxxLoading" class="state loading">正在加载分布知识...</section>
      <section v-else-if="bhxxError" class="state error">{{ bhxxError }}</section>
      <section v-else class="catalog">
        <p v-if="!filteredBhxx.length" class="empty">
          暂无符合条件的分布知识，请调整检索条件或稍后再试。
        </p>
        <div v-else class="plant-block">
          <header class="plant-header">
            <h2>病害分布与预防（{{ filteredBhxx.length }} 条）</h2>
            <span class="meta">来源：bhxx 表</span>
          </header>
          <div class="cards">
            <article v-for="row in filteredBhxx" :key="row.id" class="knowledge-card">
              <header class="card-head">
                <h3>{{ row.diseaseName }}</h3>
                <span v-if="row.plantName" class="tag">作物：{{ row.plantName }}</span>
              </header>
              <p v-if="row.distributionArea" class="desc">分布区域：{{ row.distributionArea }}</p>
              <p v-if="row.distributionTime" class="desc">分布时间：{{ row.distributionTime }}</p>
              <p v-if="row.preventionMethod" class="advice">防治：{{ row.preventionMethod }}</p>
            </article>
          </div>
        </div>
      </section>
    </section>
  </div>
</template>

<style scoped>
.knowledge-page { display: grid; gap: 24px; padding: 24px 0 48px; background: linear-gradient(180deg, #f0fdf4 0%, #f8fafc 100%); }
.hero { background: #ffffffcc; border-radius: 18px; padding: 28px; box-shadow: 0 12px 32px rgba(34, 197, 94, 0.08); border: 1px solid rgba(34, 197, 94, 0.12); }
.hero h1 { margin: 0 0 8px; font-size: 1.9rem; color: #14532d; }
.hero p { margin: 0; color: #065f46; font-size: 1rem; line-height: 1.6; }
.hero strong { font-weight: 700; color: #0f766e; }

.toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 16px; padding: 12px 18px; background: rgba(255, 255, 255, 0.9); border-radius: 14px; box-shadow: 0 4px 16px rgba(15, 118, 110, 0.06); border: 1px solid rgba(148, 163, 184, 0.25); }
.tabs { display: flex; gap: 8px; }
.tab-btn { border-radius: 999px; padding: 6px 16px; border: 1px solid transparent; background: transparent; cursor: pointer; font-size: 0.95rem; color: #0f172a; transition: all 0.18s ease-in-out; }
.tab-btn:hover { background: rgba(34, 197, 94, 0.06); }
.tab-btn.active { background: #22c55e; color: #ecfdf5; border-color: #16a34a; box-shadow: 0 0 0 1px rgba(22, 163, 74, 0.35); }
.toolbar-right { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.chips { display: flex; gap: 8px; flex-wrap: wrap; }
.chip { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px; background: rgba(16, 185, 129, 0.1); color: #047857; font-size: 0.82rem; }
.chip-label { opacity: 0.9; }
.chip-value { font-weight: 600; }
.search-input { min-width: 220px; padding: 6px 10px; border-radius: 999px; border: 1px solid rgba(148, 163, 184, 0.9); font-size: 0.9rem; outline: none; }
.search-input:focus { border-color: #22c55e; box-shadow: 0 0 0 1px rgba(34, 197, 94, 0.35); }

.state { padding: 32px; border-radius: 16px; text-align: center; font-size: 1.05rem; }
.state.loading { background: rgba(191, 219, 254, 0.45); color: #1d4ed8; }
.state.error { background: rgba(254, 202, 202, 0.45); color: #b91c1c; }

.catalog { display: grid; gap: 28px; }
.plant-block { background: rgba(255, 255, 255, 0.95); border-radius: 18px; padding: 22px; box-shadow: 0 8px 24px rgba(15, 118, 110, 0.08); border: 1px solid rgba(45, 212, 191, 0.15); }
.plant-header { display: flex; align-items: baseline; justify-content: space-between; gap: 12px; margin-bottom: 18px; }
.plant-header h2 { margin: 0; font-size: 1.4rem; color: #047857; }
.plant-header .meta { color: #059669; font-size: 0.95rem; }

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
.knowledge-card { background: linear-gradient(145deg, rgba(240, 253, 250, 0.9), rgba(219, 234, 254, 0.9)); border: 1px solid rgba(34, 197, 94, 0.2); border-radius: 14px; padding: 16px; display: grid; gap: 10px; min-height: 180px; }
.card-head { display: grid; gap: 6px; }
.card-head h3 { margin: 0; font-size: 1.1rem; color: #0f172a; }
.tag { font-size: 0.8rem; color: #0ea5e9; background: rgba(14, 165, 233, 0.12); padding: 2px 8px; border-radius: 999px; width: fit-content; }
.desc { margin: 0; color: #1f2937; font-size: 0.95rem; line-height: 1.5; }
.advice { margin: 0; color: #065f46; font-size: 0.9rem; background: rgba(16, 185, 129, 0.14); border-left: 3px solid #10b981; padding: 8px 10px; border-radius: 10px; }
.empty { margin: 4px 2px 0; font-size: 0.9rem; color: #6b7280; }

@media (max-width: 768px) {
  .cards { grid-template-columns: 1fr; }
  .knowledge-page { padding: 18px; }
  .toolbar { padding: 10px 12px; }
  .search-input { flex: 1 1 100%; min-width: 0; }
}
</style>