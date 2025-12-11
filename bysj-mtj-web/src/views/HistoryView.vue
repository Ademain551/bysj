<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiFetch, API_ORIGIN } from '@/utils/api'

const router = useRouter()
const user = ref<{ username: string } | null>(null)

const q = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const items = ref<Array<{ id: number; predictedClass: string; displayLabel?: string; confidence: number; createdAt: string; imageUrl?: string; advice?: string; disease?: { name?: string } }>>([])
const selectedIds = ref<number[]>([])

const IMG_BASE = API_ORIGIN || 'http://localhost:8099'

async function loadData() {
  if (!user.value) return
  try {
    const params = new URLSearchParams()
    params.set('page', String(page.value - 1))
    params.set('size', String(size.value))
    if (q.value.trim()) params.set('q', q.value.trim())
    const resp = await apiFetch(`/detect/history/${encodeURIComponent(user.value.username)}`, { params })
    if (!resp.ok) throw new Error('获取历史失败')
    const data = await resp.json()
    total.value = Number(data.total || 0)
    items.value = Array.isArray(data.items) ? data.items : []
  } catch (e: any) {
    ElMessage.error(e.message || '加载历史失败')
  }
}

function handleSearch() {
  page.value = 1
  loadData()
}

function handlePageChange(p: number) {
  page.value = p
  loadData()
}

function handleSelectionChange(rows: any[]) {
  selectedIds.value = rows
    .map((r) => r.id)
    .filter((id: any) => typeof id === 'number')
}

async function deleteOne(row: any) {
  if (!user.value || !row || !row.id) return
  try {
    await ElMessageBox.confirm('确认删除该条识别记录吗？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    const resp = await apiFetch(`/detect/history/${encodeURIComponent(user.value.username)}/${row.id}`, {
      method: 'DELETE',
    })
    if (!resp.ok && resp.status !== 204) {
      throw new Error('删除失败')
    }
    ElMessage.success('删除成功')
    await loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

async function deleteSelected() {
  if (!user.value) return
  if (!selectedIds.value.length) {
    ElMessage.warning('请先选择要删除的记录')
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedIds.value.length} 条记录吗？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    const resp = await apiFetch(`/detect/history/${encodeURIComponent(user.value.username)}`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ ids: selectedIds.value }),
    })
    if (!resp.ok) {
      throw new Error('删除失败')
    }
    ElMessage.success('删除成功')
    selectedIds.value = []
    await loadData()
  } catch (e: any) {
    ElMessage.error(e.message || '删除失败')
  }
}

function goKnowledge(row: { id?: number }) {
  if (!row || typeof row.id !== 'number') return
  router.push({ name: 'detect', query: { id: String(row.id) } })
}

onMounted(() => {
  const raw = sessionStorage.getItem('user')
  user.value = raw ? JSON.parse(raw) : null
  if (!user.value) { router.push('/login'); return }
  loadData()
})
</script>

<template>
  <div class="history-page">
    <!-- 顶部：标题 + 搜索框 -->
    <header class="page-header">
      <h2 class="title">识别历史</h2>
      <div class="search">
        <el-input v-model="q" placeholder="按病害名搜索" clearable @keyup.enter="handleSearch" />
        <button class="btn" @click="handleSearch">搜索</button>
        <button class="btn" @click="deleteSelected">删除所选</button>
      </div>
    </header>

    <!-- 中间：识别记录表格 -->
    <section class="table-wrap">
      <el-table :data="items" stripe style="width: 100%" row-key="id" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" />
        <el-table-column label="图片" width="140">
          <template #default="{ row }">
            <el-image :src="row.imageUrl ? (IMG_BASE + row.imageUrl) : 'https://via.placeholder.com/80?text=No+Image'" fit="cover" style="width: 96px; height: 96px; border-radius: 8px;" />
          </template>
        </el-table-column>
        <el-table-column prop="predictedClass" label="识别结果" min-width="160">
          <template #default="{ row }">
            <a href="javascript:void(0)" class="link" @click="goKnowledge(row)">
              {{ row.displayLabel || (row.disease && row.disease.name) || row.predictedClass }}
            </a>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="120">
          <template #default="{ row }">
            {{ (row.confidence * 100).toFixed(1) }}%
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="时间" min-width="180">
          <template #default="{ row }">
            {{ new Date(row.createdAt).toLocaleString() }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <a href="javascript:void(0)" class="link danger" @click="deleteOne(row)">删除</a>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 底部：分页器 -->
    <footer class="pager">
      <el-pagination
        background
        layout="prev, pager, next, jumper, ->, total"
        :total="total"
        :current-page="page"
        :page-size="size"
        @current-change="handlePageChange"
      />
    </footer>
  </div>
</template>

<style scoped>
.history-page { min-height: 100%; padding: 8px 0 24px; background: linear-gradient(180deg, #f0fdf4 0%, #f8fafc 100%); }
.page-header { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 12px; padding: 8px 12px; background: rgba(255,255,255,0.75); backdrop-filter: blur(10px); border-radius: 12px; width: min(1100px, 100%); margin: 0 auto 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.08); }
.title { margin: 0; font-size: 18px; font-weight: 700; }
.search { display: flex; gap: 8px; align-items: center; }
.table-wrap { width: min(1100px, 100%); margin: 0 auto; background: rgba(255,255,255,0.7); border: 1px solid rgba(255,255,255,0.8); border-radius: 12px; padding: 12px; overflow-x: auto; }
.link { color: #2563eb; font-weight: 600; text-decoration: none; }
.link:hover { text-decoration: underline; }
.link.danger { color: #dc2626; }
.pager { width: min(1100px, 100%); margin: 12px auto 0; display: flex; justify-content: flex-end; }

@media (max-width: 900px) {
  .history-page { padding: 8px 0 16px; }
  .page-header { grid-template-columns: 1fr; gap: 8px; }
  .search { width: 100%; }
  .pager { justify-content: center; }
}
</style>