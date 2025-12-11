<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { apiFetch, apiUrl, assetUrl } from '@/utils/api'

const router = useRouter()
type StoredUser = { username: string; avatarUrl?: string; role?: string; userType?: string }

const user = ref<StoredUser | null>(null)
const active = ref<'users' | 'fzwp' | 'bhxx' | 'guide' | 'logs' | 'announce' | 'model' | 'database'>('users')

// Users
const users = ref<Array<any>>([])
const userEditing = reactive<{ show: boolean; id?: number; username: string; password: string; nickname: string; phone: string; role: string; userType: string; enabled: boolean }>({ 
  show: false, id: undefined, username: '', password: '', nickname: '', phone: '', role: 'user', userType: 'farmer', enabled: true 
})

async function loadUsers() {
  const resp = await apiFetch('/admin/users')
  if (!resp.ok) throw new Error('加载用户失败')
  users.value = await resp.json()
}

function openCreateUser() {
  userEditing.show = true
  userEditing.id = undefined
  userEditing.username = ''
  userEditing.password = '123456'
  userEditing.nickname = ''
  userEditing.phone = ''
  userEditing.role = 'user'
  userEditing.userType = 'farmer'
  userEditing.enabled = true
}

function openEditUser(row: any) {
  userEditing.show = true
  userEditing.id = row.id
  userEditing.username = row.username
  userEditing.password = ''
  userEditing.nickname = row.nickname || ''
  userEditing.phone = row.phone || ''
  userEditing.role = row.role || 'user'
  userEditing.userType = row.userType || 'farmer'
  userEditing.enabled = row.enabled !== false
}

async function saveUser() {
  const payload: any = {
    username: userEditing.username,
    nickname: userEditing.nickname,
    phone: userEditing.phone,
    role: userEditing.role,
    userType: userEditing.userType,
    enabled: userEditing.enabled
  }
  if (userEditing.password) {
    payload.password = userEditing.password
  }
  
  const isCreate = !userEditing.id
  const url = isCreate ? '/admin/users' : `/admin/users/${userEditing.id}`
  const method = isCreate ? 'POST' : 'PUT'
  const resp = await apiFetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
  if (!resp.ok) {
    const err = await resp.json().catch(() => ({}))
    throw new Error(err.error || (isCreate ? '创建失败' : '更新失败'))
  }
  userEditing.show = false
  await loadUsers()
  ElMessage.success('已保存')
}

async function toggleUser(u: any) {
  const resp = await apiFetch(`/admin/users/${u.id}/enabled`, { method: 'PUT', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ enabled: !u.enabled }) })
  if (!resp.ok) throw new Error('更新用户状态失败')
  u.enabled = !u.enabled
}

async function deleteUser(u: any) {
  try {
    await ElMessageBox.confirm(`确认删除用户 ${u.username} ?`, '提示', { type: 'warning' })
    const resp = await apiFetch(`/admin/users/${u.id}`, { method: 'DELETE' })
    if (!resp.ok) {
      const err = await resp.json().catch(() => ({}))
      throw new Error(err.error || '删除用户失败')
    }
    users.value = users.value.filter(x => x.id !== u.id)
    ElMessage.success('已删除')
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') {
      ElMessage.error(err?.message || '删除用户失败')
    }
  }
}

// Guide Articles Management (技术指导)
const guidePage = ref(1)
const guideSize = ref(10)
const guideTotal = ref(0)
const guideList = ref<Array<any>>([])
const guideQ = ref('')
const guideEditing = reactive<{ show: boolean; id?: number; title: string; content: string; coverImageUrl: string }>(
  { show: false, id: undefined, title: '', content: '', coverImageUrl: '' }
)
const guideCoverUploadUrl = apiUrl('/chat/attachments')
const guideImageUploadUrl = guideCoverUploadUrl
const guideImageUrlInput = ref('')
const guideImageUrls = ref<Array<string>>([])
const guideContentInputRef = ref<any>(null)
const guideCommentsVisible = ref(false)
const guideCommentsArticle = ref<any | null>(null)
const guideComments = ref<Array<any>>([])

async function loadGuideArticles() {
  const params = new URLSearchParams()
  params.set('page', String(guidePage.value - 1))
  params.set('size', String(guideSize.value))
  if (guideQ.value.trim()) params.set('q', guideQ.value.trim())
  const resp = await apiFetch(`/admin/guide/articles?${params.toString()}`)
  if (!resp.ok) throw new Error('加载技术指导失败')
  const data = await resp.json()
  guideList.value = data.items || []
  guideTotal.value = data.total || 0
}

function onGuidePageChange(p: number) {
  guidePage.value = p
  loadGuideArticles().catch(err => ElMessage.error(err.message || '加载失败'))
}

function openEditGuide(row: any) {
  guideEditing.show = true
  guideEditing.id = row.id
  guideEditing.title = row.title || ''
  guideEditing.content = row.content || ''
  guideEditing.coverImageUrl = row.coverImageUrl || ''
  guideImageUrls.value = Array.isArray(row.imageUrls) ? row.imageUrls.slice() : []
  guideImageUrlInput.value = ''
}

async function saveGuide() {
  if (!guideEditing.id) return
  const payload = {
    title: guideEditing.title,
    content: guideEditing.content,
    coverImageUrl: guideEditing.coverImageUrl.trim() || null,
    imageUrls: guideImageUrls.value.slice(),
  }
  const resp = await apiFetch(`/admin/guide/articles/${guideEditing.id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!resp.ok) throw new Error('保存失败')
  guideEditing.show = false
  await loadGuideArticles()
  ElMessage.success('已保存')
}

function onGuideCoverUploadSuccess(res: any) {
  if (res && res.url) {
    guideEditing.coverImageUrl = res.url
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.warning('上传成功，但未返回地址')
  }
}

function onGuideCoverUploadError() {
  ElMessage.error('封面上传失败')
}

function onGuideImageUploadSuccess(res: any) {
  if (res && res.url) {
    guideImageUrls.value.push(res.url)
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.warning('图片上传成功，但未返回地址')
  }
}

function onGuideImageUploadError() {
  ElMessage.error('图片上传失败')
}

function addGuideImageUrl() {
  const url = guideImageUrlInput.value.trim()
  if (!url) return
  guideImageUrls.value.push(url)
  guideImageUrlInput.value = ''
}

function removeGuideImageUrl(idx: number) {
  guideImageUrls.value.splice(idx, 1)
}

function insertAtGuideCursor(text: string) {
  const textarea: HTMLTextAreaElement | undefined = guideContentInputRef.value?.textarea
  const value = guideEditing.content || ''
  if (!textarea) {
    guideEditing.content = value + text
    return
  }
  const start = textarea.selectionStart ?? value.length
  const end = textarea.selectionEnd ?? value.length
  guideEditing.content = value.slice(0, start) + text + value.slice(end)
}

function insertGuideImageToContent(url: string) {
  if (!url) return
  const src = assetUrl(url)
  const snippet = `\n<img src="${src}" alt="图片" />\n`
  insertAtGuideCursor(snippet)
}

async function deleteGuide(row: any) {
  await ElMessageBox.confirm(`确认删除文章 ${row.title} ?`, '提示', { type: 'warning' })
  const resp = await apiFetch(`/admin/guide/articles/${row.id}`, { method: 'DELETE' })
  if (!resp.ok) throw new Error('删除失败')
  await loadGuideArticles()
  ElMessage.success('已删除')
}

async function openGuideComments(row: any) {
  guideCommentsArticle.value = row
  const resp = await apiFetch(`/admin/guide/articles/${row.id}/comments`)
  if (!resp.ok) throw new Error('加载评论失败')
  const data = await resp.json()
  guideComments.value = data.items || []
  guideCommentsVisible.value = true
}

async function deleteGuideComment(c: any) {
  await ElMessageBox.confirm('确认删除该条评论？', '提示', { type: 'warning' })
  const resp = await apiFetch(`/admin/guide/comments/${c.id}`, { method: 'DELETE' })
  if (!resp.ok) throw new Error('删除失败')
  if (guideCommentsArticle.value) {
    const r = await apiFetch(`/admin/guide/articles/${guideCommentsArticle.value.id}/comments`)
    if (r.ok) {
      const data = await r.json()
      guideComments.value = data.items || []
    }
  }
  ElMessage.success('已删除')
}


// Detect logs
const logq = ref('')
const logPage = ref(1)
const logSize = ref(10)
const logTotal = ref(0)
const logs = ref<Array<any>>([])
async function loadLogs() {
  const params = new URLSearchParams()
  params.set('page', String(logPage.value - 1))
  params.set('size', String(logSize.value))
  if (logq.value.trim()) params.set('q', logq.value.trim())
  const resp = await apiFetch(`/admin/detect/logs?${params.toString()}`)
  if (!resp.ok) throw new Error('加载识别记录失败')
  const data = await resp.json()
  logs.value = data.items || []
  logTotal.value = data.total || 0
}

function onLogPageChange(p: number) {
  logPage.value = p
  loadLogs().catch(err => ElMessage.error(err.message || '加载失败'))
}

async function deleteLog(row: any) {
  if (!row || !row.id) return
  try {
    await ElMessageBox.confirm(`确认删除识别记录 #${row.id} ?`, '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    const resp = await apiFetch(`/admin/detect/logs/${row.id}`, { method: 'DELETE' })
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '删除失败')
    }
    ElMessage.success('已删除')
    await loadLogs()
  } catch (err: any) {
    ElMessage.error(err?.message || '删除失败')
  }
}

function goDetectDetail(row: any) {
  if (!row || !row.id) return
  router.push({ name: 'detect', query: { id: String(row.id) } })
}

// Announcements
const announcements = ref<Array<any>>([])
const annEditing = reactive<{ show: boolean; id?: number; title: string; content: string; published: boolean }>({ show: false, id: undefined, title: '', content: '', published: true })
async function loadAnnouncements() {
  const resp = await apiFetch('/admin/announcements')
  if (!resp.ok) throw new Error('加载通知失败')
  announcements.value = await resp.json()
}
function openCreateAnnouncement() {
  annEditing.show = true; annEditing.id = undefined; annEditing.title = ''; annEditing.content = ''; annEditing.published = true
}
function openEditAnnouncement(row: any) {
  annEditing.show = true; annEditing.id = row.id; annEditing.title = row.title; annEditing.content = row.content; annEditing.published = !!row.published
}
async function saveAnnouncement() {
  const payload = { title: annEditing.title, content: annEditing.content, published: annEditing.published }
  const isCreate = !annEditing.id
  const url = isCreate ? '/admin/announcements' : `/admin/announcements/${annEditing.id}`
  const method = isCreate ? 'POST' : 'PUT'
  const resp = await apiFetch(url, { method, headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) })
  if (!resp.ok) throw new Error(isCreate ? '发布失败' : '更新失败')
  annEditing.show = false
  await loadAnnouncements()
  ElMessage.success('已保存')
}
async function deleteAnnouncement(row: any) {
  await ElMessageBox.confirm(`确认删除公告 ${row.title} ?`, '提示', { type: 'warning' })
  const resp = await apiFetch(`/admin/announcements/${row.id}`, { method: 'DELETE' })
  if (!resp.ok) throw new Error('删除失败')
  await loadAnnouncements()
  ElMessage.success('已删除')
}

// Model Management
const modelStatus = ref<any>(null)
const modelLoading = ref(false)
const modelErrors = ref<Array<any>>([])
const modelErrorsLoading = ref(false)
async function loadModelStatus() {
  modelLoading.value = true
  try {
    const resp = await apiFetch('/admin/model/status')
    if (!resp.ok) throw new Error('加载模型状态失败')
    modelStatus.value = await resp.json()
  } catch (err: any) {
    ElMessage.error(err.message || '加载模型状态失败')
  } finally {
    modelLoading.value = false
  }
}

async function triggerTraining() {
  await ElMessageBox.confirm('确认触发模型训练？这将花费较长时间。', '提示', { type: 'warning' })
  try {
    const resp = await apiFetch('/admin/model/train', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({}) })
    if (!resp.ok) throw new Error('触发训练失败')
    const data = await resp.json()
    ElMessage.success(data.message || '训练请求已提交')
  } catch (err: any) {
    ElMessage.error(err.message || '触发训练失败')
  }
}

async function loadModelErrors() {
  modelErrorsLoading.value = true
  try {
    const resp = await apiFetch('/admin/model/errors')
    if (!resp.ok) throw new Error('加载错误样本失败')
    const data = await resp.json()
    modelErrors.value = Array.isArray(data.items) ? data.items : []
  } catch (err: any) {
    ElMessage.error(err.message || '加载错误样本失败')
  } finally {
    modelErrorsLoading.value = false
  }
}

async function addErrorToDataset(row: any) {
  try {
    await ElMessageBox.confirm(`确认将样本 #${row.id} 加入模型数据集？`, '提示', { type: 'warning' })
  } catch {
    return
  }

  let retrainNow = false
  try {
    await ElMessageBox.confirm('是否同时立即触发模型重新训练？', '提示', {
      type: 'info',
      confirmButtonText: '是',
      cancelButtonText: '否',
    })
    retrainNow = true
  } catch {
    retrainNow = false
  }

  try {
    const resp = await apiFetch(`/admin/model/errors/${row.id}/add-to-dataset`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ retrainNow }),
    })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '操作失败')
    }
    ElMessage.success('样本已加入数据集')
    await loadModelErrors()
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  }
}

async function ignoreError(row: any) {
  try {
    await ElMessageBox.confirm(`确认忽略样本 #${row.id} ? 该样本将不再出现在错误样本列表中。`, '提示', { type: 'warning' })
  } catch {
    return
  }

  try {
    const resp = await apiFetch(`/admin/model/errors/${row.id}/ignore`, { method: 'POST' })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '操作失败')
    }
    ElMessage.success('已忽略该样本')
    await loadModelErrors()
  } catch (err: any) {
    ElMessage.error(err.message || '操作失败')
  }
}

// Database Management
const tables = ref<Array<any>>([])
const selectedTable = ref<string | null>(null)
const tableInfo = ref<Array<any>>([])
const tableCount = ref<number>(0)
async function loadTables() {
  try {
    const resp = await apiFetch('/admin/database/tables')
    if (!resp.ok) throw new Error('加载表列表失败')
    tables.value = await resp.json()
  } catch (err: any) {
    ElMessage.error(err.message || '加载表列表失败')
  }
}

async function loadTableInfo(tableName: string) {
  selectedTable.value = tableName
  try {
    const [infoResp, countResp] = await Promise.all([
      apiFetch(`/admin/database/tables/${tableName}/info`),
      apiFetch(`/admin/database/tables/${tableName}/count`)
    ])
    if (!infoResp.ok || !countResp.ok) throw new Error('加载表信息失败')
    tableInfo.value = await infoResp.json()
    const countData = await countResp.json()
    tableCount.value = countData.count || 0
  } catch (err: any) {
    ElMessage.error(err.message || '加载表信息失败')
  }
}

async function clearTable(tableName: string) {
  await ElMessageBox.confirm(`确认清空表 ${tableName} 的所有数据？此操作不可恢复！`, '警告', { type: 'warning' })
  try {
    const resp = await apiFetch(`/admin/database/tables/${tableName}/clear`, { method: 'DELETE' })
    if (!resp.ok) {
      const err = await resp.json().catch(() => ({}))
      throw new Error(err.error || '清空表失败')
    }
    ElMessage.success('表已清空')
    await loadTableInfo(tableName)
    await loadTables()
  } catch (err: any) {
    ElMessage.error(err.message || '清空表失败')
  }
}

// Fzwp Management (防治物品)
const fzwpPage = ref(1)
const fzwpSize = ref(10)
const fzwpTotal = ref(0)
const fzwpList = ref<Array<any>>([])
const fzwpQ = ref('')
const fzwpEditing = reactive<{ show: boolean; id?: number; itemName: string; price: string; listedAt: string; imageUrl: string; plantName: string; targetDisease: string }>({
  show: false,
  id: undefined,
  itemName: '',
  price: '',
  listedAt: '',
  imageUrl: '',
  plantName: '',
  targetDisease: '',
})
const fzwpPlantOptions = ref<string[]>([])
const fzwpDiseaseOptions = ref<string[]>([])
const fzwpPlantLoading = ref(false)
const fzwpDiseaseLoading = ref(false)

async function loadFzwp() {
  const params = new URLSearchParams()
  params.set('page', String(fzwpPage.value - 1))
  params.set('size', String(fzwpSize.value))
  if (fzwpQ.value.trim()) params.set('q', fzwpQ.value.trim())
  const resp = await apiFetch(`/admin/fzwp?${params.toString()}`)
  if (!resp.ok) throw new Error('加载防治物品失败')
  const data = await resp.json()
  fzwpList.value = data.items || []
  fzwpTotal.value = data.total || 0
}

async function loadFzwpPlants() {
  if (fzwpPlantOptions.value.length) return
  fzwpPlantLoading.value = true
  try {
    const resp = await apiFetch('/knowledge/bhxx/plants')
    if (!resp.ok) throw new Error('加载植物列表失败')
    const data = await resp.json()
    fzwpPlantOptions.value = Array.isArray(data) ? data : []
  } catch (err: any) {
    ElMessage.error(err?.message || '加载植物列表失败')
  } finally {
    fzwpPlantLoading.value = false
  }
}

async function loadFzwpDiseases(plant: string) {
  fzwpDiseaseOptions.value = []
  if (!plant) return
  fzwpDiseaseLoading.value = true
  try {
    const resp = await apiFetch(`/knowledge/bhxx/diseases?plant=${encodeURIComponent(plant)}`)
    if (!resp.ok) throw new Error('加载病害列表失败')
    const data = await resp.json()
    fzwpDiseaseOptions.value = Array.isArray(data) ? data : []
  } catch (err: any) {
    ElMessage.error(err?.message || '加载病害列表失败')
  } finally {
    fzwpDiseaseLoading.value = false
  }
}

async function onFzwpPlantChange(value: string) {
  fzwpEditing.targetDisease = ''
  await loadFzwpDiseases(value)
}

function onFzwpPageChange(p: number) {
  fzwpPage.value = p
  loadFzwp().catch(err => ElMessage.error(err.message || '加载失败'))
}

async function openCreateFzwp() {
  fzwpEditing.show = true
  fzwpEditing.id = undefined
  fzwpEditing.itemName = ''
  fzwpEditing.price = ''
  fzwpEditing.listedAt = ''
  fzwpEditing.imageUrl = ''
  fzwpEditing.plantName = ''
  fzwpEditing.targetDisease = ''
  await loadFzwpPlants()
  fzwpDiseaseOptions.value = []
}

async function openEditFzwp(row: any) {
  fzwpEditing.show = true
  fzwpEditing.id = row.id
  fzwpEditing.itemName = row.itemName || ''
  fzwpEditing.price = row.price != null ? String(row.price) : ''
  fzwpEditing.listedAt = row.listedAt || ''
  fzwpEditing.imageUrl = row.imageUrl || ''
  fzwpEditing.plantName = row.plantName || ''
  fzwpEditing.targetDisease = row.targetDisease || ''
  await loadFzwpPlants()
  if (fzwpEditing.plantName) {
    await loadFzwpDiseases(fzwpEditing.plantName)
  } else {
    fzwpDiseaseOptions.value = []
  }
}

async function saveFzwp() {
  const payload: any = {
    itemName: fzwpEditing.itemName,
    plantName: fzwpEditing.plantName || null,
    targetDisease: fzwpEditing.targetDisease || null,
    imageUrl: fzwpEditing.imageUrl || null,
  }
  if (fzwpEditing.price.trim()) {
    const num = Number(fzwpEditing.price)
    if (!Number.isNaN(num)) payload.price = num
  }

  const isCreate = !fzwpEditing.id
  const url = isCreate ? '/admin/fzwp' : `/admin/fzwp/${fzwpEditing.id}`
  const method = isCreate ? 'POST' : 'PUT'
  const resp = await apiFetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
  if (!resp.ok) throw new Error(isCreate ? '新增失败' : '更新失败')
  fzwpEditing.show = false
  await loadFzwp()
  ElMessage.success('已保存')
}

async function deleteFzwp(row: any) {
  await ElMessageBox.confirm(`确认删除防治物品 ${row.itemName} ?`, '提示', { type: 'warning' })
  const resp = await apiFetch(`/admin/fzwp/${row.id}`, { method: 'DELETE' })
  if (!resp.ok) throw new Error('删除失败')
  await loadFzwp()
  ElMessage.success('已删除')
}

const fzwpUploadUrl = apiUrl('/admin/fzwp/image')

function onFzwpUploadSuccess(res: any) {
  if (res && res.imageUrl) {
    fzwpEditing.imageUrl = res.imageUrl
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.warning('图片上传成功，但未返回地址')
  }
}

function onFzwpUploadError() {
  ElMessage.error('图片上传失败')
}

// Bhxx Management
const bhxxPage = ref(1)
const bhxxSize = ref(10)
const bhxxTotal = ref(0)
const bhxxList = ref<Array<any>>([])
async function loadBhxx() {
  const params = new URLSearchParams()
  params.set('page', String(bhxxPage.value - 1))
  params.set('size', String(bhxxSize.value))
  const resp = await apiFetch(`/admin/bhxx?${params.toString()}`)
  if (!resp.ok) throw new Error('加载数据失败')
  const data = await resp.json()
  bhxxList.value = data.items || []
  bhxxTotal.value = data.total || 0
}

function logout() {
  sessionStorage.removeItem('user')
  try {
    apiFetch('/logout', { method: 'POST' })
  } catch {}
  router.push('/login')
}

onMounted(async () => {
  const raw = sessionStorage.getItem('user')
  user.value = raw ? JSON.parse(raw) : null
  if (!user.value) { router.push('/login'); return }
  if (user.value.role !== 'admin') {
    ElMessage.error('需要管理员权限')
    router.push('/home')
    return
  }
  await Promise.all([loadUsers(), loadFzwp(), loadBhxx(), loadLogs(), loadAnnouncements(), loadModelStatus(), loadModelErrors(), loadTables(), loadGuideArticles()])
})
</script>

<template>
  <div class="admin-page">
    <!-- Top bar -->
    <header class="topbar">
      <div class="left">系统管理后台</div>
      <div class="right">
        <img :src="user?.avatarUrl || 'https://api.dicebear.com/7.x/identicon/svg?seed=admin'" alt="avatar" class="avatar" />
        <span class="name">{{ user?.username }}</span>
        <button class="btn" @click="logout">退出登录</button>
      </div>
    </header>

    <div class="body">
      <!-- Left menu -->
      <aside class="sidebar">
        <el-menu :default-active="active" @select="(key: any) => active = key">
          <el-menu-item index="users">用户管理</el-menu-item>
          <el-menu-item index="fzwp">防治物品</el-menu-item>
          <el-menu-item index="guide">技术指导</el-menu-item>
          <el-menu-item index="bhxx">病害分布</el-menu-item>
          <el-menu-item index="logs">识别记录</el-menu-item>
          <el-menu-item index="announce">通知管理</el-menu-item>
          <el-menu-item index="model">模型管理</el-menu-item>
          <el-menu-item index="database">数据库管理</el-menu-item>
        </el-menu>
      </aside>

      <!-- Main content -->
      <main class="content">
        <!-- Users -->
        <section v-if="active==='users'">
          <div class="section-head">
            <h3>用户管理</h3>
            <button class="btn secondary" @click="openCreateUser">新增用户</button>
          </div>
          <el-table :data="users" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" width="160" />
            <el-table-column prop="nickname" label="昵称" width="160" />
            <el-table-column prop="phone" label="手机号" min-width="200" />
            <el-table-column prop="role" label="角色" width="120" />
            <el-table-column prop="userType" label="用户类型" width="120" />
            <el-table-column label="状态" width="140">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '启用' : '禁用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="280">
              <template #default="{ row }">
                <button class="btn" @click="openEditUser(row)">编辑</button>
                <button class="btn" @click="toggleUser(row)">{{ row.enabled ? '禁用' : '启用' }}</button>
                <button class="btn danger" @click="deleteUser(row)">删除</button>
              </template>
            </el-table-column>
          </el-table>

          <el-dialog v-model="userEditing.show" :title="userEditing.id ? '编辑用户' : '新增用户'">
            <div class="form">
              <label>用户名<el-input v-model="userEditing.username" :disabled="!!userEditing.id" /></label>
              <label>密码<el-input v-model="userEditing.password" type="password" :placeholder="userEditing.id ? '留空则不修改' : ''" /></label>
              <label>昵称<el-input v-model="userEditing.nickname" /></label>
              <label>手机号<el-input v-model="userEditing.phone" /></label>
              <label>角色
                <el-select v-model="userEditing.role">
                  <el-option label="普通用户" value="user" />
                  <el-option label="管理员" value="admin" />
                </el-select>
              </label>
              <label>用户类型
                <el-select v-model="userEditing.userType">
                  <el-option label="种植户" value="farmer" />
                  <el-option label="专家" value="expert" />
                  <el-option label="管理员" value="admin" />
                </el-select>
              </label>
              <label class="row">启用<el-switch v-model="userEditing.enabled" /></label>
            </div>
            <template #footer>
              <button class="btn" @click="saveUser">保存</button>
              <button class="btn outline" @click="userEditing.show=false">取消</button>
            </template>
          </el-dialog>
        </section>

        <!-- Guide Articles -->
        <section v-if="active==='guide'">
          <div class="section-head">
            <h3>技术指导管理</h3>
            <div class="actions">
              <el-input v-model="guideQ" placeholder="按标题或内容搜索" clearable @keyup.enter="loadGuideArticles" />
              <button class="btn" @click="loadGuideArticles">搜索</button>
            </div>
          </div>
          <el-table :data="guideList" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" min-width="220" />
            <el-table-column label="封面" width="120">
              <template #default="{ row }">
                <el-image
                  v-if="row.coverImageUrl"
                  :src="assetUrl(row.coverImageUrl)"
                  fit="cover"
                  style="width: 60px; height: 60px; border-radius: 6px;"
                />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="作者" min-width="180">
              <template #default="{ row }">
                <span>{{ row.author?.nickname || row.author?.username || '-' }}</span>
                <span v-if="row.author?.userType" style="margin-left: 6px; font-size: 12px; color: #6b7280;">
                  （{{ row.author.userType === 'expert' ? '专家' : (row.author.userType === 'farmer' ? '种植户' : row.author.userType) }}）
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="发布时间" width="190">
              <template #default="{ row }">
                <span v-if="row.createdAt">{{ new Date(row.createdAt).toLocaleString() }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="commentCount" label="评论数" width="100" />
            <el-table-column label="操作" width="260">
              <template #default="{ row }">
                <button class="btn" @click="openEditGuide(row)">编辑</button>
                <button class="btn" @click="openGuideComments(row)">查看评论</button>
                <button class="btn danger" @click="deleteGuide(row)">删除</button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              background
              layout="prev, pager, next, ->, total"
              :total="guideTotal"
              :current-page="guidePage"
              :page-size="guideSize"
              @current-change="onGuidePageChange"
            />
          </div>

          <el-dialog v-model="guideEditing.show" title="编辑技术指导文章" width="640px">
            <div class="form">
              <label>标题<el-input v-model="guideEditing.title" /></label>
              <label>封面图片（可选）</label>
              <div class="image-input">
                <el-upload
                  :action="guideCoverUploadUrl"
                  name="file"
                  :show-file-list="false"
                  :on-success="onGuideCoverUploadSuccess"
                  :on-error="onGuideCoverUploadError"
                  accept=".jpg,.jpeg,.png,.gif,.webp,.bmp,.dng"
                >
                  <button class="btn" type="button">上传封面图片</button>
                </el-upload>
                <el-input v-model="guideEditing.coverImageUrl" placeholder="也可以输入或粘贴封面图片地址" />
              </div>
              <div v-if="guideEditing.coverImageUrl" style="margin-top: 4px;">
                <el-image :src="assetUrl(guideEditing.coverImageUrl)" fit="cover" style="width: 100px; height: 70px; border-radius: 6px;" />
              </div>
              <label>插图（可选，多张）</label>
              <div class="image-input">
                <el-input v-model="guideImageUrlInput" placeholder="粘贴图片地址后点击添加" @keyup.enter.native.prevent="addGuideImageUrl" />
                <button class="btn" type="button" @click="addGuideImageUrl">添加</button>
                <el-upload
                  :action="guideImageUploadUrl"
                  name="file"
                  :show-file-list="false"
                  :on-success="onGuideImageUploadSuccess"
                  :on-error="onGuideImageUploadError"
                  accept=".jpg,.jpeg,.png,.gif,.webp,.bmp,.dng"
                >
                  <button class="btn" type="button">上传图片</button>
                </el-upload>
              </div>
              <ul v-if="guideImageUrls.length" class="image-list">
                <li v-for="(url, idx) in guideImageUrls" :key="url + idx">
                  <span class="url">{{ url }}</span>
                  <button type="button" class="link" @click="insertGuideImageToContent(url)">插入到正文</button>
                  <button type="button" class="link" @click="removeGuideImageUrl(idx)">移除</button>
                </li>
              </ul>
              <label>正文<el-input v-model="guideEditing.content" type="textarea" :rows="8" ref="guideContentInputRef" /></label>
            </div>
            <template #footer>
              <button class="btn" @click="saveGuide">保存</button>
              <button class="btn outline" @click="guideEditing.show=false">取消</button>
            </template>
          </el-dialog>

          <el-dialog v-model="guideCommentsVisible" :title="guideCommentsArticle ? '评论管理 - ' + guideCommentsArticle.title : '评论管理'" width="720px">
            <el-table :data="guideComments" stripe style="width: 100%">
              <el-table-column prop="id" label="ID" width="80" />
              <el-table-column label="用户" width="180">
                <template #default="{ row }">
                  <span>{{ row.author?.nickname || row.author?.username || '-' }}</span>
                  <span v-if="row.author?.userType" style="margin-left: 6px; font-size: 12px; color: #6b7280;">
                    （{{ row.author.userType === 'expert' ? '专家' : (row.author.userType === 'farmer' ? '种植户' : row.author.userType) }}）
                  </span>
                </template>
              </el-table-column>
              <el-table-column prop="content" label="内容" min-width="260" />
              <el-table-column prop="parentId" label="父评论ID" width="100" />
              <el-table-column prop="createdAt" label="时间" width="180">
                <template #default="{ row }">
                  <span v-if="row.createdAt">{{ new Date(row.createdAt).toLocaleString() }}</span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120">
                <template #default="{ row }">
                  <button class="btn danger" @click="deleteGuideComment(row)">删除</button>
                </template>
              </el-table-column>
            </el-table>
          </el-dialog>
        </section>

        <!-- Fzwp -->
        <section v-if="active==='fzwp'">
          <div class="section-head">
            <h3>防治物品管理</h3>
            <div class="actions">
              <el-input v-model="fzwpQ" placeholder="按物品名称或应对病害搜索" clearable @keyup.enter="loadFzwp" />
              <button class="btn" @click="loadFzwp">搜索</button>
              <button class="btn secondary" @click="openCreateFzwp">新增物品</button>
            </div>
          </div>
          <el-table :data="fzwpList" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="itemName" label="物品名称" width="200" />
            <el-table-column prop="plantName" label="可用于植物" width="160" />
            <el-table-column prop="price" label="价格" width="120">
              <template #default="{ row }">
                <span v-if="row.price != null">{{ row.price }} 元</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="listedAt" label="上架时间" width="200">
              <template #default="{ row }">
                <span v-if="row.listedAt">{{ new Date(row.listedAt).toLocaleString() }}</span>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column prop="targetDisease" label="可用于病害" min-width="200" />
            <el-table-column prop="imageUrl" label="图片" width="120">
              <template #default="{ row }">
                <el-image v-if="row.imageUrl" :src="assetUrl(row.imageUrl)" fit="cover" style="width: 60px; height: 60px; border-radius: 8px" />
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <button class="btn" @click="openEditFzwp(row)">编辑</button>
                <button class="btn danger" @click="deleteFzwp(row)">删除</button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination
              background
              layout="prev, pager, next, ->, total"
              :total="fzwpTotal"
              :current-page="fzwpPage"
              :page-size="fzwpSize"
              @current-change="onFzwpPageChange"
            />
          </div>

          <el-dialog v-model="fzwpEditing.show" title="防治物品">
            <div class="form">
              <label>物品名称<el-input v-model="fzwpEditing.itemName" /></label>
              <label>物品价格（元）<el-input v-model="fzwpEditing.price" /></label>
              <label>上架时间
                <span>
                  {{
                    fzwpEditing.listedAt
                      ? new Date(fzwpEditing.listedAt).toLocaleString()
                      : '-'
                  }}
                </span>
              </label>
              <label>物品图片
                <el-upload
                  :action="fzwpUploadUrl"
                  name="file"
                  :show-file-list="false"
                  :on-success="onFzwpUploadSuccess"
                  :on-error="onFzwpUploadError"
                  accept=".jpg,.jpeg,.png,.gif,.webp,.bmp,.dng"
                >
                  <button class="btn secondary" type="button">上传图片</button>
                </el-upload>
                <el-image
                  v-if="fzwpEditing.imageUrl"
                  :src="assetUrl(fzwpEditing.imageUrl)"
                  fit="cover"
                  style="width: 80px; height: 80px; border-radius: 8px; margin-top: 8px"
                />
              </label>
              <label>可用于植物
                <el-select
                  v-model="fzwpEditing.plantName"
                  filterable
                  clearable
                  placeholder="请选择植物"
                  :loading="fzwpPlantLoading"
                  @change="onFzwpPlantChange"
                >
                  <el-option
                    v-for="name in fzwpPlantOptions"
                    :key="name"
                    :label="name"
                    :value="name"
                  />
                </el-select>
              </label>
              <label>可用于病害
                <el-select
                  v-model="fzwpEditing.targetDisease"
                  filterable
                  clearable
                  placeholder="请先选择植物，再选择病害"
                  :disabled="!fzwpEditing.plantName"
                  :loading="fzwpDiseaseLoading"
                >
                  <el-option
                    v-for="name in fzwpDiseaseOptions"
                    :key="name"
                    :label="name"
                    :value="name"
                  />
                </el-select>
              </label>
            </div>
            <template #footer>
              <button class="btn" @click="saveFzwp">保存</button>
              <button class="btn outline" @click="fzwpEditing.show=false">取消</button>
            </template>
          </el-dialog>
        </section>

        <!-- Bhxx -->
        <section v-if="active==='bhxx'">
          <div class="section-head">
            <h3>植物病害分布情况</h3>
            <button class="btn" @click="loadBhxx">刷新</button>
          </div>
          <el-table :data="bhxxList" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="plantName" label="植物名称" width="150" />
            <el-table-column prop="diseaseName" label="病害名称" width="250" />
            <el-table-column prop="distributionArea" label="分布区域" min-width="200" />
            <el-table-column prop="distributionTime" label="分布时间" width="200" />
            <el-table-column prop="preventionMethod" label="防治方法" min-width="300" />
          </el-table>
          <div class="pager">
            <el-pagination background layout="prev, pager, next, ->, total" :total="bhxxTotal" :current-page="bhxxPage" :page-size="bhxxSize" @current-change="(p: number) => { bhxxPage = p; loadBhxx() }" />
          </div>
        </section>

        <!-- Detect logs -->
        <section v-if="active==='logs'">
          <div class="section-head">
            <h3>识别记录</h3>
            <div class="actions">
              <el-input v-model="logq" placeholder="按识别结果搜索" clearable @keyup.enter="loadLogs" />
              <button class="btn" @click="loadLogs">搜索</button>
            </div>
          </div>
          <el-table :data="logs" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" width="160" />
            <el-table-column prop="predictedClass" label="识别结果" min-width="180">
              <template #default="{ row }">
                <a href="javascript:void(0)" class="link" @click="goDetectDetail(row)">{{ row.predictedClass }}</a>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="120">
              <template #default="{ row }">{{ (row.confidence*100).toFixed(1) }}%</template>
            </el-table-column>
            <el-table-column prop="createdAt" label="时间" min-width="180">
              <template #default="{ row }">{{ new Date(row.createdAt).toLocaleString() }}</template>
            </el-table-column>
            <el-table-column label="操作" width="200">
              <template #default="{ row }">
                <button class="btn outline" @click="goDetectDetail(row)">查看</button>
                <button class="btn danger" style="margin-left:8px;" @click="deleteLog(row)">删除</button>
              </template>
            </el-table-column>
          </el-table>
          <div class="pager">
            <el-pagination background layout="prev, pager, next, ->, total" :total="logTotal" :current-page="logPage" :page-size="logSize" @current-change="onLogPageChange" />
          </div>
        </section>

        <!-- Notifications -->
        <section v-if="active==='announce'">
          <div class="section-head">
            <h3>通知管理</h3>
            <div class="actions">
              <button class="btn secondary" @click="openCreateAnnouncement">发布通知</button>
            </div>
          </div>
          <el-table :data="announcements" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="title" label="标题" width="240" />
            <el-table-column prop="content" label="内容" min-width="300" />
            <el-table-column label="状态" width="140">
              <template #default="{ row }"><el-tag :type="row.published ? 'success' : 'info'">{{ row.published ? '已发布' : '草稿' }}</el-tag></template>
            </el-table-column>
            <el-table-column label="操作" width="220">
              <template #default="{ row }">
                <button class="btn" @click="openEditAnnouncement(row)">编辑</button>
                <button class="btn danger" @click="deleteAnnouncement(row)">删除</button>
              </template>
            </el-table-column>
          </el-table>

          <el-dialog v-model="annEditing.show" title="通知">
            <div class="form">
              <label>标题<el-input v-model="annEditing.title" /></label>
              <label>内容<el-input v-model="annEditing.content" type="textarea" :rows="6" /></label>
              <label class="row">状态<el-switch v-model="annEditing.published" /></label>
            </div>
            <template #footer>
              <button class="btn" @click="saveAnnouncement">保存</button>
              <button class="btn outline" @click="annEditing.show=false">取消</button>
            </template>
          </el-dialog>
        </section>

        <!-- Model Management -->
        <section v-if="active==='model'">
          <div class="section-head">
            <h3>模型管理</h3>
            <div class="actions">
              <button class="btn" @click="loadModelStatus">刷新状态</button>
              <button class="btn secondary" @click="triggerTraining">触发训练</button>
            </div>
          </div>
          <div v-if="modelLoading" class="loading">加载中...</div>
          <div v-else-if="modelStatus" class="model-status">
            <div class="status-item">
              <span class="label">FastAPI 服务:</span>
              <el-tag :type="modelStatus.fastapiAvailable ? 'success' : 'danger'">
                {{ modelStatus.fastapiAvailable ? '在线' : '离线' }}
              </el-tag>
            </div>
            <div class="status-item">
              <span class="label">模型路径:</span>
              <span>{{ modelStatus.modelPath || 'N/A' }}</span>
            </div>
            <div class="status-item">
              <span class="label">最后训练时间:</span>
              <span>{{ modelStatus.lastTrainTime || 'N/A' }}</span>
            </div>
            <div v-if="modelStatus.error" class="status-item error">
              <span class="label">错误:</span>
              <span>{{ modelStatus.error }}</span>
            </div>
          </div>

          <div style="margin-top: 20px; border-top: 1px dashed #e5e7eb; padding-top: 12px;">
            <div class="section-head" style="margin-bottom: 8px;">
              <h3 style="font-size: 16px;">模型错误样本管理</h3>
              <div class="actions">
                <button class="btn" @click="loadModelErrors">刷新列表</button>
              </div>
            </div>
            <div v-if="modelErrorsLoading" class="loading">加载错误样本中...</div>
            <div v-else-if="!modelErrors.length" style="font-size: 13px; color: #6b7280; padding: 4px 0;">暂无待处理的错误样本</div>
            <el-table v-else :data="modelErrors" stripe style="width: 100%;">
              <el-table-column prop="id" label="ID" width="70" />
              <el-table-column label="农户" min-width="120">
                <template #default="{ row }">
                  {{ row.farmer?.nickname || row.farmer?.username || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="专家" min-width="120">
                <template #default="{ row }">
                  {{ row.expert?.nickname || row.expert?.username || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="图片预览" width="120">
                <template #default="{ row }">
                  <el-image
                    v-if="row.detectResult?.imageUrl"
                    :src="assetUrl(row.detectResult.imageUrl)"
                    fit="cover"
                    style="width: 64px; height: 64px; border-radius: 6px;"
                  />
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="原识别结果" min-width="180">
                <template #default="{ row }">
                  {{ row.detectResult?.predictedClass || '-' }}
                </template>
              </el-table-column>
              <el-table-column label="专家标注" min-width="180">
                <template #default="{ row }">
                  {{ row.correctPlant && row.correctDisease ? (row.correctPlant + ' · ' + row.correctDisease) : '-' }}
                </template>
              </el-table-column>
              <el-table-column prop="createdAt" label="反馈时间" min-width="180">
                <template #default="{ row }">
                  {{ row.createdAt ? new Date(row.createdAt).toLocaleString() : '-' }}
                </template>
              </el-table-column>
              <el-table-column label="操作" width="260">
                <template #default="{ row }">
                  <button class="btn" @click="addErrorToDataset(row)">加入数据集</button>
                  <button class="btn outline" style="margin-left:4px;" @click="ignoreError(row)">忽略</button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>

        <!-- Database Management -->
        <section v-if="active==='database'">
          <div class="section-head">
            <h3>数据库管理</h3>
            <button class="btn" @click="loadTables">刷新</button>
          </div>
          <div class="database-container">
            <div class="table-list">
              <h4>数据表列表</h4>
              <el-table :data="tables" stripe style="width: 100%">
                <el-table-column prop="TABLE_NAME" label="表名" width="200" />
                <el-table-column prop="TABLE_ROWS" label="行数" width="120" />
                <el-table-column label="操作" width="200">
                  <template #default="{ row }">
                    <button class="btn" @click="loadTableInfo(row.TABLE_NAME)">查看</button>
                    <button class="btn danger" @click="clearTable(row.TABLE_NAME)">清空</button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
            <div v-if="selectedTable" class="table-info">
              <h4>表信息: {{ selectedTable }}</h4>
              <p>记录数: {{ tableCount }}</p>
              <el-table :data="tableInfo" stripe style="width: 100%">
                <el-table-column prop="COLUMN_NAME" label="列名" width="200" />
                <el-table-column prop="DATA_TYPE" label="数据类型" width="150" />
                <el-table-column prop="IS_NULLABLE" label="可空" width="100" />
                <el-table-column prop="COLUMN_DEFAULT" label="默认值" width="150" />
                <el-table-column prop="COLUMN_COMMENT" label="注释" min-width="200" />
              </el-table>
            </div>
          </div>
        </section>
      </main>
    </div>
  </div>
</template>

<style scoped>
.admin-page { min-height: 100%; padding: 8px 0 24px; background: #f7fafc; }
.topbar { height: 56px; display: flex; align-items: center; justify-content: space-between; padding: 0 16px; margin-bottom: 12px; background: #fff; border-radius: 12px; box-shadow: 0 2px 10px rgba(0,0,0,0.06); }
.topbar .left { font-weight: 700; }
.topbar .right { display: flex; align-items: center; gap: 8px; }
.avatar { width: 28px; height: 28px; border-radius: 50%; }
.name { color: #374151; font-weight: 600; }
.body { display: grid; grid-template-columns: 220px 1fr; gap: 12px; padding: 12px; }
.sidebar { background: #fff; border-radius: 12px; padding: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.06); }
.content { background: #fff; border-radius: 12px; padding: 16px; box-shadow: 0 2px 10px rgba(0,0,0,0.06); min-width: 0; overflow-x: auto; }
.section-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.section-head h3 { margin: 0; }
.actions { display: flex; gap: 8px; align-items: center; }
.pager { margin-top: 16px; display: flex; justify-content: flex-end; }
.form { display: grid; gap: 12px; }
label { display: grid; gap: 4px; font-size: 14px; color: #374151; }
label.row { display: flex; align-items: center; gap: 8px; }
.model-status { display: grid; gap: 12px; padding: 16px; background: #f9fafb; border-radius: 8px; }
.status-item { display: flex; align-items: center; gap: 8px; }
.status-item .label { font-weight: 600; min-width: 120px; }
.status-item.error { color: #ef4444; }
.database-container { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.table-list, .table-info { background: #f9fafb; padding: 16px; border-radius: 8px; }
.table-list h4, .table-info h4 { margin: 0 0 12px 0; }
.loading { padding: 20px; text-align: center; color: #6b7280; }

@media (max-width: 1100px) {
  .body { grid-template-columns: 1fr; }
  .sidebar { position: static; }
  .pager { justify-content: center; }
  .database-container { grid-template-columns: 1fr; }
}
</style>

