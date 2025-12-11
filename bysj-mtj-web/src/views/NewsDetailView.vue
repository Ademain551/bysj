<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch } from '@/utils/api'

type NewsDetail = {
  id: number
  title: string
  content: string
  createdAt?: string
  updatedAt?: string
}

const route = useRoute()
const router = useRouter()

const loading = ref(true)
const error = ref('')
const news = ref<NewsDetail | null>(null)

async function loadDetail() {
  const idParam = route.params.id
  const id = Array.isArray(idParam) ? idParam[0] : idParam
  if (!id) {
    error.value = '参数错误'
    loading.value = false
    return
  }
  try {
    const resp = await apiFetch(`/announcements/${id}`)
    if (!resp.ok) {
      error.value = '加载失败'
      loading.value = false
      return
    }
    news.value = await resp.json()
  } catch (e: any) {
    error.value = e?.message || '加载失败'
  } finally {
    loading.value = false
  }
}

function goBack() {
  router.back()
}

onMounted(() => {
  loadDetail()
})
</script>

<template>
  <div class="news-detail">
    <div class="news-detail-inner card">
      <button class="back-btn" @click="goBack">返回</button>
      <div v-if="loading" class="status">加载中...</div>
      <div v-else-if="error" class="status error">{{ error }}</div>
      <article v-else-if="news">
        <h1 class="title">{{ news.title }}</h1>
        <p v-if="news.createdAt" class="meta">
          发布时间：{{ new Date(news.createdAt).toLocaleString() }}
        </p>
        <div class="content">
          {{ news.content }}
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.news-detail {
  min-height: 100%;
  display: flex;
  justify-content: center;
  padding: 24px 12px;
}
.news-detail-inner {
  width: min(900px, 100%);
  padding: 24px;
}
.back-btn {
  margin-bottom: 16px;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid #d1d5db;
  background: #f9fafb;
  cursor: pointer;
  font-size: 0.9rem;
}
.title {
  margin: 0 0 8px 0;
  font-size: 1.6rem;
}
.meta {
  margin: 0 0 16px 0;
  color: #6b7280;
  font-size: 0.9rem;
}
.content {
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 1rem;
  color: #111827;
}
.status {
  color: #6b7280;
}
.status.error {
  color: #ef4444;
}
</style>
