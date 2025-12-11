<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { apiFetch, assetUrl } from '@/utils/api'

// State
const router = useRouter()
type StoredUser = { username: string; avatarUrl?: string; role?: string; userType?: string; nickname?: string; createdAt?: string; phone?: string; address?: string }

function goFavItem(itemId: number) {
  router.push({ name: 'shop-item', params: { id: String(itemId) } })
}

function goFavArticle(articleId: number) {
  router.push({ name: 'guide-detail', params: { id: String(articleId) } })
}

const uploadingAvatar = ref(false)
async function onAvatarFileChange(e: Event) {
  if (!userLocal.value) return
  const input = e.target as HTMLInputElement
  const file = input.files && input.files[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    const fd = new FormData()
    fd.append('avatar', file)
    const resp = await apiFetch(`/user/${encodeURIComponent(userLocal.value.username)}/avatar`, { method: 'POST', body: fd })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok || !data?.avatarUrl) throw new Error(data?.message || '上传失败')
    editForm.avatarUrl = data.avatarUrl
    profile.avatarUrl = data.avatarUrl
    const stored: StoredUser = {
      username: profile.username,
      avatarUrl: profile.avatarUrl,
      role: profile.role,
      userType: profile.userType,
      nickname: profile.nickname || profile.username,
      createdAt: profile.createdAt,
      phone: profile.phone,
      address: profile.address,
    }
    userLocal.value = stored
    sessionStorage.setItem('user', JSON.stringify(stored))
    ElMessage.success('头像已更新')
  } catch (e: any) {
    ElMessage.error(e?.message || '头像上传失败')
  } finally {
    uploadingAvatar.value = false
    if (input) input.value = ''
  }
}

async function loadFavoriteItems() {
  if (!userLocal.value) return
  try {
    const resp = await apiFetch('/shop/favorites')
    if (!resp.ok) throw new Error('加载收藏物品失败')
    const data = await resp.json()
    favoriteItems.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载收藏物品失败')
  }
}

async function loadFavoriteArticles() {
  if (!userLocal.value) return
  try {
    const resp = await apiFetch('/guide/articles/favorites')
    if (!resp.ok) throw new Error('加载收藏文章失败')
    const data = await resp.json()
    favoriteArticles.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载收藏文章失败')
  }
}

async function loadExpertTasks() {
  if (!userLocal.value) return
  if (profile.userType !== 'expert') return
  expertTasksLoading.value = true
  try {
    const username = userLocal.value.username
    const resp = await apiFetch(`/detect/feedback/expert/${encodeURIComponent(username)}?status=PENDING_EXPERT`)
    if (!resp.ok) throw new Error('获取待处理反馈失败')
    const data = await resp.json()
    expertTasks.value = Array.isArray(data.items) ? data.items : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载待处理反馈失败')
  } finally {
    expertTasksLoading.value = false
  }
}

async function loadPlants() {
  try {
    const resp = await apiFetch('/knowledge/bhxx/plants')
    if (!resp.ok) throw new Error('加载植物列表失败')
    const data = await resp.json()
    plantOptions.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载植物列表失败')
  }
}

async function loadDiseasesForPlant(plant: string) {
  if (!plant) return
  try {
    const resp = await apiFetch(`/knowledge/bhxx/diseases?plant=${encodeURIComponent(plant)}`)
    if (!resp.ok) throw new Error('加载病害列表失败')
    const data = await resp.json()
    diseaseOptions.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载病害列表失败')
  }
}

function openExpertCorrect(row: any) {
  expertDialog.show = true
  expertDialog.mode = 'correct'
  expertDialog.feedbackId = row.id
  expertDialog.plantName = ''
  expertDialog.diseaseName = ''
  expertDialog.expertComment = ''
}

async function openExpertWrong(row: any) {
  expertDialog.show = true
  expertDialog.mode = 'wrong'
  expertDialog.feedbackId = row.id
  expertDialog.plantName = ''
  expertDialog.diseaseName = ''
  expertDialog.expertComment = ''
  if (!plantOptions.value.length) {
    await loadPlants()
  }
  diseaseOptions.value = []
}

function goToDetectResult(row: any) {
  const id = row?.detectResult?.id
  if (!id) {
    ElMessage.warning('该反馈缺少识别记录ID')
    return
  }
  router.push({ path: '/detect', query: { id: String(id) } })
}

async function onExpertPlantChange(value: string) {
  expertDialog.plantName = value
  expertDialog.diseaseName = ''
  await loadDiseasesForPlant(value)
}

async function submitExpertDecision() {
  if (!userLocal.value) return
  if (!expertDialog.feedbackId) return
  const username = userLocal.value.username
  try {
    let resp: Response
    if (expertDialog.mode === 'correct') {
      resp = await apiFetch(`/detect/feedback/${expertDialog.feedbackId}/confirm-correct`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          expertUsername: username,
          expertComment: expertDialog.expertComment || '',
        }),
      })
    } else {
      if (!expertDialog.plantName || !expertDialog.diseaseName) {
        ElMessage.warning('请选择植物和病害')
        return
      }
      resp = await apiFetch(`/detect/feedback/${expertDialog.feedbackId}/confirm-wrong`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          expertUsername: username,
          plantName: expertDialog.plantName,
          diseaseName: expertDialog.diseaseName,
          expertComment: expertDialog.expertComment || '',
        }),
      })
    }
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '提交处理结果失败')
    }
    ElMessage.success('已提交处理结果')
    expertDialog.show = false
    await loadExpertTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '提交处理结果失败')
  }
}

function handleResize() {
  try {
    if (barChart) barChart.resize()
    if (pieChart) pieChart.resize()
  } catch {}
}

const userLocal = ref<StoredUser | null>(null)
const profile = reactive({
  username: '',
  nickname: '',
  phone: '',
  address: '',
  avatarUrl: '',
  role: '',
  userType: '',
  createdAt: '',
})

const avatarSrc = computed(() => {
  return profile.avatarUrl ? assetUrl(profile.avatarUrl) : `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(profile.username || 'user')}`
})

// Dialogs and forms
const showEdit = ref(false)
const editForm = reactive<{ nickname: string; phone: string; address: string; avatarUrl: string }>({
  nickname: '',
  phone: '',
  address: '',
  avatarUrl: '',
})

const pwdForm = reactive<{ oldPassword: string; newPassword: string }>({
  oldPassword: '',
  newPassword: '',
})

const expertTasks = ref<Array<any>>([])
const expertTasksLoading = ref(false)
const plantOptions = ref<string[]>([])
const diseaseOptions = ref<string[]>([])
const expertDialog = reactive<{ show: boolean; mode: 'correct' | 'wrong'; feedbackId?: number; plantName: string; diseaseName: string; expertComment: string }>(
  { show: false, mode: 'correct', feedbackId: undefined, plantName: '', diseaseName: '', expertComment: '' }
)

const favoriteItems = ref<Array<any>>([])
const favoriteArticles = ref<Array<any>>([])

const orders = ref<Array<any>>([])
const ordersLoading = ref(false)

async function loadOrdersSummary() {
  if (!userLocal.value) return
  ordersLoading.value = true
  try {
    const resp = await apiFetch('/shop/orders')
    if (!resp.ok) throw new Error('加载订单失败')
    const data = await resp.json()
    orders.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e.message || '加载订单失败')
  } finally {
    ordersLoading.value = false
  }
}

const ordersPendingPay = computed(() => orders.value.filter((o) => o.status === 'CREATED'))
const ordersPendingReceive = computed(() => orders.value.filter((o) => o.status === 'PAID'))
const ordersCompleted = computed(() => orders.value.filter((o) => o.status === 'COMPLETED'))

function goOrdersPage(status?: string) {
  if (status) {
    router.push({ path: '/orders', query: { status } })
  } else {
    router.push('/orders')
  }
}

function goOrderAction(order: any) {
  if (!order) return
  if (order.status === 'CREATED') {
    router.push({ name: 'shop-pay', params: { id: String(order.id) } })
  } else {
    router.push({ name: 'pay-result', params: { id: String(order.id) } })
  }
}

// ECharts refs
const barRef = ref<HTMLDivElement | null>(null)
const pieRef = ref<HTMLDivElement | null>(null)
let barChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

// Placeholder recognition stats (replace with API when ready)
const recognitionStats = reactive<{ months: string[]; counts: number[]; byType: Array<{ name: string; value: number }> }>({
  months: [],
  counts: [],
  byType: [],
})

async function fetchStats() {
  if (!userLocal.value) return
  const username = userLocal.value.username
  try {
    const resp = await apiFetch(`/detect/stats/${encodeURIComponent(username)}`)
    if (!resp.ok) throw new Error('获取识别统计失败')
    const data = await resp.json()
    const srcMonths: string[] = Array.isArray(data.months) ? data.months : []
    const srcCounts: number[] = Array.isArray(data.counts) ? data.counts : []
    const monthMap: Record<string, number> = {}
    for (let i = 0; i < srcMonths.length; i++) {
      const k = String(srcMonths[i])
      const v = Number(srcCounts[i] || 0)
      monthMap[k] = (monthMap[k] || 0) + v
    }

    const now = new Date()
    const last12: string[] = []
    for (let i = 11; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1)
      const ym = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
      last12.push(ym)
    }
    recognitionStats.months = last12
    recognitionStats.counts = last12.map((m) => monthMap[m] || 0)
    recognitionStats.byType = Array.isArray(data.byType) ? data.byType : []

    if (barChart) {
      barChart.setOption({
        xAxis: { type: 'category', data: recognitionStats.months },
        series: [
          { name: '识别次数', type: 'bar', data: recognitionStats.counts, itemStyle: { color: '#4CAF50' } },
        ],
      })
    }
    if (pieChart) {
      pieChart.setOption({
        series: [
          {
            name: '种类占比',
            type: 'pie',
            radius: '60%',
            data: recognitionStats.byType,
            emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } },
          },
        ],
      })
    }
  } catch (e: any) {
    ElMessage.error(e.message || '加载识别统计失败')
  }
}

function initCharts() {
  if (barRef.value) {
    barChart = echarts.init(barRef.value)
    barChart.setOption({
      tooltip: {},
      grid: { left: 40, right: 20, top: 20, bottom: 30 },
      xAxis: { type: 'category', data: recognitionStats.months },
      yAxis: { type: 'value' },
      series: [
        { name: '识别次数', type: 'bar', data: recognitionStats.counts, itemStyle: { color: '#4CAF50' } },
      ],
    })
  }
  if (pieRef.value) {
    pieChart = echarts.init(pieRef.value)
    pieChart.setOption({
      tooltip: { trigger: 'item' },
      legend: { top: 'bottom' },
      series: [
        {
          name: '种类占比',
          type: 'pie',
          radius: '60%',
          data: recognitionStats.byType,
          emphasis: { itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0, 0, 0, 0.5)' } },
        },
      ],
    })
  }
}

function genAvatar() {
  const seed = profile.username || 'user'
  const url = `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(seed)}`
  editForm.avatarUrl = url
}

async function fetchProfile() {
  if (!userLocal.value) return
  const username = userLocal.value.username
  try {
    const resp = await apiFetch(`/user/${encodeURIComponent(username)}`)
    if (!resp.ok) throw new Error('获取用户信息失败')
    const data = await resp.json()
    profile.username = data.username
    profile.nickname = data.nickname || ''
    profile.phone = data.phone || ''
    profile.address = data.address || ''
    profile.avatarUrl = data.avatarUrl || userLocal.value?.avatarUrl || ''
    profile.role = data.role || userLocal.value?.role || 'user'
    profile.userType = data.userType || userLocal.value?.userType || ''
    profile.createdAt = data.createdAt || ''
    // init edit form defaults
    editForm.nickname = profile.nickname
    editForm.phone = profile.phone
    editForm.address = profile.address
    editForm.avatarUrl = profile.avatarUrl
  } catch (e: any) {
    ElMessage.error(e.message || '加载用户信息失败')
  }
}

async function saveProfile() {
  if (!userLocal.value) return
  const username = userLocal.value.username
  try {
    const resp = await apiFetch(`/user/${encodeURIComponent(username)}`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(editForm),
    })
    if (!resp.ok) throw new Error('保存资料失败')
    // Update local profile and sessionStorage
    profile.nickname = editForm.nickname
    profile.phone = editForm.phone
    profile.address = editForm.address
    profile.avatarUrl = editForm.avatarUrl
    const stored: StoredUser = {
      username: profile.username,
      avatarUrl: profile.avatarUrl,
      role: profile.role,
      userType: profile.userType,
      nickname: profile.nickname || profile.username,
      createdAt: profile.createdAt,
      phone: profile.phone,
      address: profile.address,
    }
    userLocal.value = stored
    sessionStorage.setItem('user', JSON.stringify(stored))
    ElMessage.success('资料已更新')
    showEdit.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function changePassword() {
  if (!userLocal.value) return
  const username = userLocal.value.username
  try {
    const resp = await apiFetch(`/user/${encodeURIComponent(username)}/password`, {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(pwdForm),
    })
    if (!resp.ok) {
      const text = await resp.text()
      throw new Error(text || '修改密码失败')
    }
    ElMessage.success('密码修改成功')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
  } catch (e: any) {
    ElMessage.error(e.message || '密码修改失败')
  }
}

function openEdit() {
  showEdit.value = true
}

function logout() {
  try {
    sessionStorage.removeItem('user')
    sessionStorage.removeItem('role')
    sessionStorage.removeItem('username')
  } catch {}
  try {
    apiFetch('/logout', { method: 'POST' })
  } catch {}
  router.push('/login')
}

onMounted(async () => {
  const u = sessionStorage.getItem('user')
  userLocal.value = u ? JSON.parse(u) : null
  if (!userLocal.value) {
    router.push('/login')
    return
  }
  await fetchProfile()
  if (profile.userType === 'farmer') {
    await loadOrdersSummary()
  }
  // 管理员不需要收藏与识别统计
  if (!isAdmin.value) {
    await fetchStats()
    await loadFavoriteItems()
    await loadFavoriteArticles()
    initCharts()
  }
  await loadExpertTasks()
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', handleResize)
  }
})

onUnmounted(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', handleResize)
  }
})

const identityLabel = computed(() => {
  if (profile.role === 'admin') return '系统管理员'
  if (profile.userType === 'expert') return '农林专家'
  if (profile.userType === 'farmer') return '种植户'
  return '普通用户'
})

const isAdmin = computed(() => profile.role === 'admin')
const isExpert = computed(() => profile.userType === 'expert')
</script>

<template>
  <div class="profile-page">
    <!-- Top: avatar + username -->
    <div class="top-row">
      <div class="left">
        <img :src="avatarSrc" class="avatar" alt="avatar" />
        <div class="name-area">
          <div class="username">{{ profile.nickname || profile.username }}</div>
          <div class="sub">@{{ profile.username }} · {{ identityLabel }}</div>
        </div>
      </div>
      <div class="right">
        <button class="btn" @click="openEdit">修改信息</button>
        <button class="btn outline" @click="logout" style="margin-left:8px;">退出登录</button>
      </div>
    </div>

    <div v-if="!isAdmin" class="card info-card">
      <div class="card-title">我的收藏</div>
      <div class="fav-section">
        <div class="fav-block">
          <div class="fav-title">推荐用药 / 物品</div>
          <div v-if="!favoriteItems.length" class="fav-empty">暂无收藏物品</div>
          <div v-else class="fav-list">
            <div
              v-for="it in favoriteItems"
              :key="it.id"
              class="fav-item"
              @click="goFavItem(it.itemId)"
            >
              <div class="fav-main">
                <div class="fav-name">{{ it.itemName }}</div>
                <div class="fav-meta">
                  <span v-if="it.plantName">{{ it.plantName }}</span>
                  <span v-if="it.targetDisease" class="dot">应对：{{ it.targetDisease }}</span>
                </div>
              </div>
              <div class="fav-price" v-if="it.price != null">¥ {{ it.price }}</div>
            </div>
          </div>
        </div>
        <div class="fav-block">
          <div class="fav-title">技术指导文章</div>
          <div v-if="!favoriteArticles.length" class="fav-empty">暂无收藏文章</div>
          <div v-else class="fav-list">
            <div
              v-for="a in favoriteArticles"
              :key="a.id"
              class="fav-item"
              @click="goFavArticle(a.articleId)"
            >
              <div class="fav-main">
                <div class="fav-name">{{ a.title }}</div>
                <div class="fav-meta">
                  <span>{{ a.createdAt ? new Date(a.createdAt).toLocaleString() : '' }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div v-if="profile.userType === 'farmer'" class="card info-card">
      <div class="card-title-row">
        <div class="card-title">我的订单</div>
        <button class="link-btn" type="button" @click="goOrdersPage()">查看全部</button>
      </div>
      <div v-if="ordersLoading" class="order-summary-loading">正在加载订单...</div>
      <div v-else class="order-summary">
        <div class="order-col">
          <div class="order-col-title clickable" @click="goOrdersPage('CREATED')">待付款（{{ ordersPendingPay.length }}）</div>
          <div v-if="!ordersPendingPay.length" class="order-empty">暂无待付款订单</div>
          <div v-else class="order-list">
            <div
              v-for="o in ordersPendingPay.slice(0, 3)"
              :key="'p-' + o.id"
              class="order-item"
              @click="goOrderAction(o)"
            >
              <span class="order-id">订单号：{{ o.id }}</span>
              <span class="order-amount">¥ {{ (o.totalAmount || 0).toFixed(2) }}</span>
            </div>
          </div>
        </div>
        <div class="order-col">
          <div class="order-col-title clickable" @click="goOrdersPage('PAID')">待收货（{{ ordersPendingReceive.length }}）</div>
          <div v-if="!ordersPendingReceive.length" class="order-empty">暂无待收货订单</div>
          <div v-else class="order-list">
            <div
              v-for="o in ordersPendingReceive.slice(0, 3)"
              :key="'r-' + o.id"
              class="order-item"
              @click="goOrderAction(o)"
            >
              <span class="order-id">订单号：{{ o.id }}</span>
              <span class="order-amount">¥ {{ (o.totalAmount || 0).toFixed(2) }}</span>
            </div>
          </div>
        </div>
        <div class="order-col">
          <div class="order-col-title clickable" @click="goOrdersPage('COMPLETED')">已完成（{{ ordersCompleted.length }}）</div>
          <div v-if="!ordersCompleted.length" class="order-empty">暂无已完成订单</div>
          <div v-else class="order-list">
            <div
              v-for="o in ordersCompleted.slice(0, 3)"
              :key="'c-' + o.id"
              class="order-item"
              @click="goOrderAction(o)"
            >
              <span class="order-id">订单号：{{ o.id }}</span>
              <span class="order-amount">¥ {{ (o.totalAmount || 0).toFixed(2) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Middle: Info card -->
    <div class="card info-card">
      <div class="card-title">用户资料</div>
      <div class="rows">
        <div class="row"><span class="label">身份</span><span class="value">{{ identityLabel }}</span></div>
        <div class="row"><span class="label">手机号</span><span class="value">{{ profile.phone || '未设置' }}</span></div>
        <div class="row" v-if="profile.userType === 'farmer'">
          <span class="label">收货地址</span>
          <span class="value addr-value">
            <span>{{ profile.address || '未填写，用于收货的详细地址' }}</span>
            <button class="addr-edit-btn" type="button" @click="openEdit">填写 / 修改</button>
          </span>
        </div>
        <div class="row"><span class="label">注册时间</span><span class="value">{{ profile.createdAt ? new Date(profile.createdAt).toLocaleString() : '未知' }}</span></div>
      </div>
    </div>

    <div v-if="profile.userType === 'expert'" class="card info-card">
      <div class="card-title">待处理纠错反馈</div>
      <div v-if="expertTasksLoading" style="font-size: 13px; color: #6b7280; padding: 8px 0;">加载中...</div>
      <div v-else-if="!expertTasks.length" style="font-size: 13px; color: #6b7280; padding: 8px 0;">暂无待处理反馈</div>
      <el-table v-else :data="expertTasks" stripe style="width: 100%; margin-top: 8px;">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column label="农户" min-width="140">
          <template #default="{ row }">
            {{ row.farmer?.nickname || row.farmer?.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="原识别结果" min-width="180">
          <template #default="{ row }">
            {{ row.detectResult?.predictedClass || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="提交时间" min-width="180">
          <template #default="{ row }">
            {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="320">
          <template #default="{ row }">
            <button class="btn outline" style="margin-right:4px;" @click="goToDetectResult(row)">查看识别结果</button>
            <button class="btn" @click="openExpertCorrect(row)">识别无误</button>
            <button class="btn outline" style="margin-left:4px;" @click="openExpertWrong(row)">识别错误</button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- Bottom: charts -->
    <div v-if="!isAdmin" class="charts-row">
      <div class="chart card">
        <div class="card-title">识别次数（按月）</div>
        <div ref="barRef" class="chart-box"></div>
      </div>
      <div class="chart card">
        <div class="card-title">种类占比</div>
        <div ref="pieRef" class="chart-box"></div>
      </div>
    </div>

    <!-- Edit modal -->
    <el-dialog v-model="showEdit" title="修改用户信息" width="520">
      <div class="edit-form">
        <label>昵称</label>
        <input v-model="editForm.nickname" placeholder="请输入昵称" />

        <label>手机号</label>
        <input v-model="editForm.phone" placeholder="请输入手机号（11位）" />

        <label>收货地址</label>
        <textarea v-model="editForm.address" placeholder="用于收货的详细地址" rows="2" />

        <label>头像地址</label>
        <div class="avatar-row">
          <input v-model="editForm.avatarUrl" placeholder="可粘贴图片URL" />
          <button class="btn secondary" @click="genAvatar">随机生成</button>
        </div>
        <input type="file" accept="image/*" @change="onAvatarFileChange" :disabled="uploadingAvatar" />

        <div class="pwd-title">修改密码</div>
        <label>旧密码</label>
        <input type="password" v-model="pwdForm.oldPassword" placeholder="旧密码" />
        <label>新密码</label>
        <input type="password" v-model="pwdForm.newPassword" placeholder="新密码（至少6位）" />
      </div>
      <template #footer>
        <div class="dialog-actions">
          <button class="btn" @click="saveProfile">保存资料</button>
          <button class="btn outline" @click="changePassword">修改密码</button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="expertDialog.show" :title="expertDialog.mode === 'correct' ? '确认识别结果无误' : '确认识别错误并标注'" width="520">
      <div class="edit-form">
        <template v-if="expertDialog.mode === 'wrong'">
          <label>植物
            <el-select v-model="expertDialog.plantName" placeholder="请选择植物" @change="onExpertPlantChange">
              <el-option v-for="p in plantOptions" :key="p" :label="p" :value="p" />
            </el-select>
          </label>
          <label>病害
            <el-select v-model="expertDialog.diseaseName" placeholder="请选择病害">
              <el-option v-for="d in diseaseOptions" :key="d" :label="d" :value="d" />
            </el-select>
          </label>
        </template>
        <label>专家说明
          <textarea v-model="expertDialog.expertComment" placeholder="可填写本次判断依据" rows="3" />
        </label>
      </div>
      <template #footer>
        <div class="dialog-actions">
          <button class="btn" @click="submitExpertDecision">提交</button>
          <button class="btn outline" @click="expertDialog.show = false">取消</button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100%;
  padding: 24px 0 32px;
  max-width: 1200px;
  margin: 0 auto;
  background: linear-gradient(180deg, #f0fdf4 0%, #f8fafc 100%);
}
.top-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.left { display: flex; align-items: center; gap: 16px; }
.right { display: flex; align-items: center; }
.avatar { width: 72px; height: 72px; border-radius: 50%; box-shadow: 0 4px 12px rgba(0,0,0,0.15); }
.username { font-size: 20px; font-weight: 600; }
.sub { font-size: 12px; color: #6b7280; }

.card { background: rgba(255,255,255,0.7); backdrop-filter: blur(8px); border: 1px solid rgba(255,255,255,0.6); border-radius: 12px; padding: 16px; }
.card-title { font-weight: 600; margin-bottom: 8px; }

.card-title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.link-btn { border: none; background: transparent; color: #2563eb; font-size: 12px; cursor: pointer; }
.link-btn:hover { text-decoration: underline; }

.info-card { margin-bottom: 16px; }
.rows { display: grid; grid-template-columns: 1fr; gap: 8px; }
.row { display: flex; justify-content: space-between; padding: 8px 0; border-bottom: 1px dashed #e5e7eb; }
.row:last-child { border-bottom: none; }
.label { color: #6b7280; }
.value { font-weight: 500; }
.addr-value { display: inline-flex; align-items: center; gap: 8px; }
.addr-edit-btn {
  margin-left: 8px;
  padding: 2px 8px;
  border-radius: 999px;
  border: 1px solid #d1d5db;
  background: #f9fafb;
  font-size: 12px;
  cursor: pointer;
  color: #2563eb;
}
.addr-edit-btn:hover {
  background: #eff6ff;
}

.order-summary { display: grid; grid-template-columns: repeat(3, 1fr); gap: 12px; font-size: 0.85rem; }
.order-summary-loading { font-size: 0.85rem; color: #6b7280; padding: 4px 0; }
.order-col { padding: 6px 8px; border-radius: 10px; background: #f9fafb; border: 1px solid #e5e7eb; }
.order-col-title { font-weight: 600; margin-bottom: 4px; }
.order-col-title.clickable { cursor: pointer; color: #2563eb; }
.order-col-title.clickable:hover { text-decoration: underline; }
.order-empty { font-size: 0.8rem; color: #9ca3af; }
.order-list { display: grid; gap: 4px; margin-top: 2px; }
.order-item { display: flex; justify-content: space-between; align-items: center; padding: 3px 4px; border-radius: 8px; background: #ffffff; cursor: pointer; }
.order-item:hover { background: #eff6ff; }
.order-id { color: #374151; }
.order-amount { color: #16a34a; }

.charts-row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.chart-box { width: 100%; height: 280px; }

.edit-form { display: grid; gap: 8px; }
.edit-form input { padding: 8px 10px; border: 1px solid #e5e7eb; border-radius: 8px; }
.avatar-row { display: grid; grid-template-columns: 1fr auto; gap: 8px; }
.pwd-title { margin-top: 8px; font-weight: 600; }

.fav-section { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-top: 4px; }
.fav-block { border-radius: 10px; padding: 8px 10px; background: #f9fafb; border: 1px solid #e5e7eb; }
.fav-title { font-size: 0.9rem; font-weight: 600; margin-bottom: 4px; }
.fav-empty { font-size: 0.8rem; color: #9ca3af; }
.fav-list { display: grid; gap: 6px; margin-top: 4px; }
.fav-item { display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 4px 6px; border-radius: 8px; background: #ffffff; cursor: pointer; }
.fav-item:hover { background: #eff6ff; }
.fav-main { display: grid; gap: 2px; }
.fav-name { font-size: 0.9rem; font-weight: 500; color: #111827; }
.fav-meta { font-size: 0.8rem; color: #6b7280; display: flex; gap: 6px; align-items: center; }
.fav-meta .dot::before { content: '·'; margin: 0 2px 0 0; }
.fav-price { font-size: 0.85rem; color: #16a34a; }

@media (max-width: 900px) {
  .profile-page { padding: 16px 0 24px; }
  .top-row { flex-direction: column; align-items: flex-start; gap: 12px; }
  .avatar { width: 56px; height: 56px; }
  .charts-row { grid-template-columns: 1fr; }
  .chart-box { height: 220px; }
}
</style>