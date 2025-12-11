<script setup lang="ts">
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { apiFetch, apiUrl, assetUrl } from '@/utils/api'
import { ElMessage } from 'element-plus'

type StoredUser = { username: string; nickname?: string; role?: string; userType?: string; avatarUrl?: string }

interface GuideArticleBrief {
  id: number
  title: string
  summary: string
  coverImageUrl?: string | null
  createdAt: string
  author?: { username: string; nickname?: string; userType?: string; avatarUrl?: string }
}

interface FzwpOption {
  id: number
  itemName: string
  targetDisease: string
}

const router = useRouter()

const user = ref<StoredUser | null>(null)
const loading = ref(false)
const q = ref('')
const page = ref(1)
const size = ref(10)
const total = ref(0)
const items = ref<GuideArticleBrief[]>([])

const showEditor = ref(false)
const editorSubmitting = ref(false)
const editor = reactive<{ title: string; content: string; coverImageUrl: string; imageUrlInput: string; imageUrls: string[]; recommendedItemIds: number[] }>(
  { title: '', content: '', coverImageUrl: '', imageUrlInput: '', imageUrls: [], recommendedItemIds: [] }
)

const contentInputRef = ref<any>(null)

const fzwpLoading = ref(false)
const fzwpOptions = ref<FzwpOption[]>([])

const coverUploadUrl = apiUrl('/chat/attachments')
const imageUploadUrl = coverUploadUrl

const isExpert = computed(() => user.value?.userType === 'expert')

function loadUser() {
  const raw = sessionStorage.getItem('user')
  user.value = raw ? JSON.parse(raw) : null
}

function identityLabel(userType?: string | null): string {
  if (userType === 'expert') return '农林专家'
  if (userType === 'farmer') return '种植户'
  return ''
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const da = String(d.getDate()).padStart(2, '0')
  const hh = String(d.getHours()).padStart(2, '0')
  const mm = String(d.getMinutes()).padStart(2, '0')
  return `${y}-${m}-${da} ${hh}:${mm}`
}

async function fetchArticles() {
  loading.value = true
  try {
    const params = new URLSearchParams()
    params.set('page', String(page.value - 1))
    params.set('size', String(size.value))
    if (q.value.trim()) params.set('q', q.value.trim())
    const resp = await apiFetch('/guide/articles', { params })
    if (!resp.ok) throw new Error('加载技术指导失败')
    const data = await resp.json()
    total.value = Number(data.total || 0)
    items.value = Array.isArray(data.items) ? data.items : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载技术指导失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchArticles()
}

function handlePageChange(p: number) {
  page.value = p
  fetchArticles()
}

async function fetchFzwpOptions() {
  if (fzwpOptions.value.length) return
  fzwpLoading.value = true
  try {
    const resp = await apiFetch('/knowledge/fzwp?page=0&size=100')
    if (!resp.ok) throw new Error('加载防治物品失败')
    const data = await resp.json()
    const list = Array.isArray(data.items) ? data.items : []
    fzwpOptions.value = list.map((it: any) => ({
      id: it.id,
      itemName: it.itemName || '',
      targetDisease: it.targetDisease || '',
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载防治物品失败')
  } finally {
    fzwpLoading.value = false
  }
}

function onCoverUploadSuccess(res: any) {
  if (res && res.url) {
    editor.coverImageUrl = res.url
    ElMessage.success('封面上传成功')
  } else {
    ElMessage.warning('上传成功，但未返回地址')
  }
}

function onCoverUploadError() {
  ElMessage.error('封面上传失败')
}

function onImageUploadSuccess(res: any) {
  if (res && res.url) {
    editor.imageUrls.push(res.url)
    ElMessage.success('图片上传成功')
  } else {
    ElMessage.warning('图片上传成功，但未返回地址')
  }
}

function onImageUploadError() {
  ElMessage.error('图片上传失败')
}

function insertAtCursor(text: string) {
  const textarea: HTMLTextAreaElement | undefined = contentInputRef.value?.textarea
  const value = editor.content || ''
  if (!textarea) {
    editor.content = value + text
    return
  }
  const start = textarea.selectionStart ?? value.length
  const end = textarea.selectionEnd ?? value.length
  editor.content = value.slice(0, start) + text + value.slice(end)
}

function insertImageToContent(url: string) {
  if (!url) return
  const src = assetUrl(url)
  const snippet = `\n<img src="${src}" alt="图片" />\n`
  insertAtCursor(snippet)
}

function openEditor() {
  if (!user.value) {
    router.push('/login')
    return
  }
  if (!isExpert.value) {
    ElMessage.error('只有农林专家可以发布技术指导文章')
    return
  }
  editor.title = ''
  editor.content = ''
  editor.coverImageUrl = ''
  editor.imageUrlInput = ''
  editor.imageUrls = []
  editor.recommendedItemIds = []
  showEditor.value = true
  fetchFzwpOptions()
}

function addImageUrl() {
  const url = editor.imageUrlInput.trim()
  if (!url) return
  editor.imageUrls.push(url)
  editor.imageUrlInput = ''
}

function removeImageUrl(idx: number) {
  editor.imageUrls.splice(idx, 1)
}

async function submitArticle() {
  if (!user.value) {
    router.push('/login')
    return
  }
  const title = editor.title.trim()
  const content = editor.content.trim()
  if (!title || !content) {
    ElMessage.warning('标题和内容不能为空')
    return
  }
  editorSubmitting.value = true
  try {
    const body = {
      authorUsername: user.value.username,
      title,
      content,
      coverImageUrl: editor.coverImageUrl.trim() || null,
      imageUrls: editor.imageUrls.slice(),
      recommendedItemIds: editor.recommendedItemIds.slice(),
    }
    const resp = await apiFetch('/guide/articles', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '发布失败')
    }
    ElMessage.success('发布成功')
    showEditor.value = false
    await fetchArticles()
  } catch (e: any) {
    ElMessage.error(e?.message || '发布失败')
  } finally {
    editorSubmitting.value = false
  }
}

function goDetail(item: GuideArticleBrief) {
  router.push({ name: 'guide-detail', params: { id: item.id } })
}

onMounted(() => {
  loadUser()
  fetchArticles()
})
</script>

<template>
  <div class="guide-page">
    <header class="guide-header">
      <div class="left">
        <h2>技术指导</h2>
        <p>查看农林专家发布的技术指导文章，了解防治方案与推荐物品。</p>
      </div>
      <div class="right">
        <div class="search">
          <input v-model="q" type="search" placeholder="按标题或正文关键字搜索" @keyup.enter="handleSearch" />
          <button class="btn" type="button" @click="handleSearch">搜索</button>
        </div>
        <button v-if="isExpert" class="btn primary" type="button" @click="openEditor">发布技术指导</button>
      </div>
    </header>

    <section class="guide-list" v-if="!loading && items.length">
      <article v-for="item in items" :key="item.id" class="guide-card" @click="goDetail(item)">
        <div class="cover" v-if="item.coverImageUrl">
          <img :src="assetUrl(item.coverImageUrl || '')" alt="cover" />
        </div>
        <div class="content">
          <h3>{{ item.title }}</h3>
          <p class="summary">{{ item.summary }}</p>
          <div class="meta">
            <div class="author" v-if="item.author">
              <span class="avatar">{{ (item.author.nickname || item.author.username)[0] }}</span>
              <span class="name">{{ item.author.nickname || item.author.username }}</span>
              <span class="tag" v-if="identityLabel(item.author.userType)">
                {{ identityLabel(item.author.userType) }}
              </span>
            </div>
            <span class="time">{{ formatTime(item.createdAt) }}</span>
          </div>
        </div>
      </article>
    </section>

    <section v-else-if="loading" class="state">正在加载技术指导...</section>
    <section v-else class="state">暂无技术指导文章</section>

    <footer class="pager" v-if="total > size">
      <el-pagination
        background
        layout="prev, pager, next, jumper, ->, total"
        :total="total"
        :current-page="page"
        :page-size="size"
        @current-change="handlePageChange"
      />
    </footer>

    <el-dialog v-model="showEditor" title="发布技术指导" width="640px">
      <div class="form">
        <div class="field">
          <label>标题</label>
          <el-input v-model="editor.title" placeholder="请输入文章标题" />
        </div>
        <div class="field">
          <label>封面图片（可选）</label>
          <div class="image-input">
            <el-upload
              :action="coverUploadUrl"
              name="file"
              :show-file-list="false"
              :on-success="onCoverUploadSuccess"
              :on-error="onCoverUploadError"
              accept=".jpg,.jpeg,.png,.gif,.webp,.bmp,.dng"
            >
              <button class="btn" type="button">选择封面图片</button>
            </el-upload>
            <el-input v-model="editor.coverImageUrl" placeholder="也可以粘贴网络图片地址" />
          </div>
          <div v-if="editor.coverImageUrl" class="cover-preview">
            <img :src="assetUrl(editor.coverImageUrl)" alt="封面预览" />
          </div>
        </div>
        <div class="field">
          <label>正文</label>
          <el-input
            v-model="editor.content"
            type="textarea"
            :autosize="{ minRows: 6, maxRows: 12 }"
            placeholder="请输入技术指导内容，可包含步骤、注意事项等"
            ref="contentInputRef"
          />
        </div>
        <div class="field">
          <label>插图 URL（可选，多张）</label>
          <div class="image-input">
            <el-input v-model="editor.imageUrlInput" placeholder="粘贴图片地址后点击添加" @keyup.enter.native.prevent="addImageUrl" />
            <button class="btn" type="button" @click="addImageUrl">添加</button>
            <el-upload
              :action="imageUploadUrl"
              name="file"
              :show-file-list="false"
              :on-success="onImageUploadSuccess"
              :on-error="onImageUploadError"
              accept=".jpg,.jpeg,.png,.gif,.webp,.bmp,.dng"
            >
              <button class="btn" type="button">上传图片</button>
            </el-upload>
          </div>
          <ul v-if="editor.imageUrls.length" class="image-list">
            <li v-for="(url, idx) in editor.imageUrls" :key="url + idx">
              <span class="url">{{ url }}</span>
              <button type="button" class="link" @click="insertImageToContent(url)">插入到正文</button>
              <button type="button" class="link" @click="removeImageUrl(idx)">移除</button>
            </li>
          </ul>
        </div>
        <div class="field">
          <label>物品推荐（可选，从 fzwp 中选择）</label>
          <el-select
            v-model="editor.recommendedItemIds"
            multiple
            filterable
            :loading="fzwpLoading"
            placeholder="选择推荐物品"
          >
            <el-option
              v-for="opt in fzwpOptions"
              :key="opt.id"
              :label="opt.itemName + (opt.targetDisease ? '（' + opt.targetDisease + '）' : '')"
              :value="opt.id"
            />
          </el-select>
        </div>
      </div>
      <template #footer>
        <button class="btn" type="button" @click="showEditor = false">取消</button>
        <button class="btn primary" type="button" :disabled="editorSubmitting" @click="submitArticle">
          {{ editorSubmitting ? '提交中...' : '发布' }}
        </button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.guide-page { min-height: 100%; padding: 16px 18px 24px; background: linear-gradient(180deg,#f0fdf4,#eff6ff); }
.guide-header { display: flex; justify-content: space-between; gap: 16px; align-items: center; margin-bottom: 16px; padding: 12px 16px; border-radius: 14px; background: rgba(255,255,255,0.95); box-shadow: 0 8px 24px rgba(15,118,110,0.08); }
.guide-header .left h2 { margin: 0 0 4px; font-size: 1.4rem; }
.guide-header .left p { margin: 0; font-size: 0.9rem; color: #4b5563; }
.guide-header .right { display: flex; flex-direction: column; gap: 8px; align-items: flex-end; }
.search { display: flex; gap: 6px; }
.search input { padding: 6px 10px; border-radius: 999px; border: 1px solid #cbd5e1; font-size: 0.9rem; min-width: 220px; }
.btn { border: none; border-radius: 999px; padding: 6px 14px; font-size: 0.9rem; cursor: pointer; background: #e5e7eb; color: #111827; }
.btn.primary { background: #22c55e; color: #ecfdf5; }
.btn.primary:disabled { opacity: 0.7; cursor: default; }

.guide-list { display: grid; gap: 12px; margin-top: 8px; }
.guide-card { display: grid; grid-template-columns: 160px minmax(0,1fr); gap: 12px; padding: 10px 12px; border-radius: 14px; background: rgba(255,255,255,0.96); box-shadow: 0 6px 18px rgba(148,163,184,0.25); cursor: pointer; }
.guide-card:hover { box-shadow: 0 10px 24px rgba(34,197,94,0.25); transform: translateY(-1px); transition: all 0.15s ease; }
.cover img { width: 100%; height: 120px; object-fit: cover; border-radius: 10px; }
.content h3 { margin: 0 0 4px; font-size: 1.1rem; color: #111827; }
.summary { margin: 0 0 8px; font-size: 0.9rem; color: #4b5563; line-height: 1.4; max-height: 3.2em; overflow: hidden; text-overflow: ellipsis; }
.meta { display: flex; justify-content: space-between; align-items: center; font-size: 0.8rem; color: #6b7280; }
.author { display: flex; align-items: center; gap: 6px; }
.avatar { width: 24px; height: 24px; border-radius: 999px; background: #22c55e33; display: inline-flex; align-items: center; justify-content: center; font-size: 0.8rem; font-weight: 600; color: #15803d; }
.name { font-weight: 500; }
.tag { padding: 2px 8px; border-radius: 999px; background: #e0f2fe; color: #0369a1; font-size: 0.75rem; }
.time { white-space: nowrap; }

.state { margin-top: 24px; text-align: center; color: #6b7280; font-size: 0.95rem; }

.pager { margin-top: 16px; display: flex; justify-content: flex-end; }

.form { display: grid; gap: 12px; }
.field { display: grid; gap: 6px; }
.field label { font-size: 0.9rem; font-weight: 500; }
.image-input { display: flex; gap: 6px; align-items: center; }
.image-list { margin: 6px 0 0; padding: 0; list-style: none; display: grid; gap: 4px; }
.image-list li { display: flex; justify-content: space-between; align-items: center; font-size: 0.8rem; }
.image-list .url { flex: 1; margin-right: 6px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link { border: none; background: transparent; color: #2563eb; cursor: pointer; font-size: 0.8rem; }

@media (max-width: 900px) {
  .guide-header { flex-direction: column; align-items: flex-start; }
  .guide-card { grid-template-columns: 1fr; }
  .cover img { height: 160px; }
}
</style>
