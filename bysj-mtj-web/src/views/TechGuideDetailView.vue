<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiFetch, assetUrl } from '@/utils/api'
import { ElMessage } from 'element-plus'

type StoredUser = { username: string; nickname?: string; role?: string; userType?: string; avatarUrl?: string; phone?: string }

type GuideComment = {
  id: number
  content: string
  createdAt: string
  author: { username: string; nickname?: string; userType?: string; avatarUrl?: string; phone?: string }
  parentId?: number | null
  replies?: GuideComment[]
}

const route = useRoute()
const router = useRouter()

const article = ref<any | null>(null)
const loading = ref(false)
const error = ref('')

const userRaw = sessionStorage.getItem('user')
const currentUser = ref<StoredUser | null>(userRaw ? JSON.parse(userRaw) : null)

const commentContent = ref('')
const replyingTo = ref<GuideComment | null>(null)
const submittingComment = ref(false)

const showAuthorCard = ref(false)
const consulting = ref(false)

const articleId = computed(() => Number(route.params.id))
const comments = computed<GuideComment[]>(() => (article.value?.comments as GuideComment[]) || [])
const authorInfo = computed(() => article.value?.author || null)

function ensureLoggedIn(): boolean {
  if (!currentUser.value) {
    router.push('/login')
    return false
  }
  return true
}

function identityLabel(userType?: string | null): string {
  if (userType === 'expert') return '农林专家'
  if (userType === 'farmer') return '种植户'
  return ''
}

function getAvatar(user?: { username: string; avatarUrl?: string } | null): string {
  if (!user) return ''
  if (user.avatarUrl && user.avatarUrl.length > 0) {
    return assetUrl(user.avatarUrl)
  }
  const seed = user.username || 'user'
  return `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(seed)}`
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

async function fetchArticle() {
  const id = articleId.value
  if (!id || Number.isNaN(id)) {
    error.value = '无效的文章编号'
    return
  }
  loading.value = true
  error.value = ''
  try {
    const resp = await apiFetch(`/guide/articles/${id}`)
    if (!resp.ok) throw new Error('加载文章失败')
    const data = await resp.json()
    article.value = data
  } catch (e: any) {
    error.value = e?.message || '加载文章失败'
    ElMessage.error(error.value)
  } finally {
    loading.value = false
  }
}

async function toggleFavoriteArticle() {
  if (!ensureLoggedIn()) return
  const id = articleId.value
  if (!id || Number.isNaN(id)) return
  try {
    const resp = await apiFetch(`/guide/articles/${id}/favorite/toggle`, { method: 'POST' })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '收藏操作失败')
    }
    const favorited = !!(data as any)?.favorited
    if (article.value) {
      ;(article.value as any).favorited = favorited
    }
    ElMessage.success(favorited ? '已收藏该文章' : '已取消收藏')
  } catch (e: any) {
    ElMessage.error(e?.message || '收藏操作失败')
  }
}

function startReply(c: GuideComment) {
  replyingTo.value = c
}

function cancelReply() {
  replyingTo.value = null
}

function findCommentById(list: GuideComment[], id: number): GuideComment | null {
  for (const c of list) {
    if (c.id === id) return c
    if (Array.isArray(c.replies) && c.replies.length) {
      const found = findCommentById(c.replies, id)
      if (found) return found
    }
  }
  return null
}

async function submitComment() {
  if (!ensureLoggedIn()) return
  if (!article.value) return
  const id = articleId.value
  const text = commentContent.value.trim()
  if (!text) {
    ElMessage.warning('评论内容不能为空')
    return
  }
  submittingComment.value = true
  try {
    const body: any = {
      authorUsername: currentUser.value!.username,
      content: text,
    }
    if (replyingTo.value) {
      body.parentId = replyingTo.value.id
    }
    const resp = await apiFetch(`/guide/articles/${id}/comments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
    if (!resp.ok) {
      const t = await resp.text().catch(() => '')
      throw new Error(t || '发表评论失败')
    }
    const created = await resp.json()
    if (!Array.isArray(article.value.comments)) {
      article.value.comments = []
    }
    if (created.parentId) {
      const parent = findCommentById(article.value.comments, created.parentId)
      if (parent) {
        if (!Array.isArray(parent.replies)) parent.replies = []
        parent.replies.push(created)
      } else {
        article.value.comments.push(created)
      }
    } else {
      article.value.comments.push(created)
    }
    commentContent.value = ''
    replyingTo.value = null
    ElMessage.success('评论已发布')
  } catch (e: any) {
    ElMessage.error(e?.message || '发表评论失败')
  } finally {
    submittingComment.value = false
  }
}

async function addFriendByUsername(name: string) {
  if (!ensureLoggedIn()) return
  try {
    const resp = await apiFetch('/friends/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requester: currentUser.value!.username, target: name }),
    })
    const data = await resp.json().catch(() => null)
    if (!resp.ok || (data && data.success === false)) {
      const msg = data?.message || (data?.error as string) || '添加好友失败'
      throw new Error(msg)
    }
    const msg = data?.message || '已添加为好友'
    ElMessage.success(msg)
  } catch (e: any) {
    ElMessage.error(e?.message || '添加好友失败')
  }
}

async function consultAuthor() {
  if (!ensureLoggedIn()) return
  if (!authorInfo.value) return
  consulting.value = true
  try {
    await apiFetch('/friends/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ requester: currentUser.value!.username, target: authorInfo.value.username }),
    })
    const resp = await apiFetch('/chat/rooms/direct', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userA: currentUser.value!.username, userB: authorInfo.value.username }),
    })
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '创建会话失败')
    }
    const data = await resp.json()
    const roomId = data?.id
    if (!roomId) {
      throw new Error('未获取到会话编号')
    }
    router.push({ name: 'chat', query: { roomId: String(roomId) } })
  } catch (e: any) {
    ElMessage.error(e?.message || '咨询失败')
  } finally {
    consulting.value = false
  }
}

function goBack() {
  router.back()
}

onMounted(() => {
  fetchArticle()
})
</script>

<template>
  <div class="detail-page">
    <header class="top-bar">
      <button class="link" type="button" @click="goBack">返回</button>
      <h2 v-if="article">{{ article.title }}</h2>
      <button
        v-if="article"
        class="link fav-btn"
        type="button"
        @click="toggleFavoriteArticle"
      >
        {{ (article as any).favorited ? '已收藏' : '收藏' }}
      </button>
    </header>

    <section v-if="loading" class="state">正在加载文章...</section>
    <section v-else-if="error" class="state error">{{ error }}</section>

    <section v-else-if="article" class="article">
      <header class="article-head">
        <div class="author" @click="showAuthorCard = true">
          <img class="avatar" :src="getAvatar(authorInfo || null)" alt="author" />
          <div class="info">
            <div class="name-line">
              <span class="name">{{ authorInfo?.nickname || authorInfo?.username }}</span>
              <span v-if="identityLabel(authorInfo?.userType)" class="tag">{{ identityLabel(authorInfo?.userType) }}</span>
            </div>
            <div class="meta">
              <span>作者账号：{{ authorInfo?.username }}</span>
              <span v-if="article.createdAt">发布时间：{{ formatTime(article.createdAt) }}</span>
            </div>
          </div>
        </div>
      </header>

      <section class="body">
        <div class="content" v-html="article.content"></div>
        <div
          v-if="
            Array.isArray(article.imageUrls) &&
            article.imageUrls.length &&
            !String(article.content || '').includes('<img')
          "
          class="images"
        >
          <img v-for="(url, idx) in article.imageUrls" :key="url + idx" :src="assetUrl(url)" alt="image" />
        </div>
      </section>

      <section v-if="Array.isArray(article.recommendations) && article.recommendations.length" class="recs">
        <h3>物品推荐</h3>
        <div class="rec-list">
          <article v-for="rec in article.recommendations" :key="rec.id" class="rec-card">
            <div v-if="rec.item && rec.item.imageUrl" class="rec-cover">
              <img :src="assetUrl(rec.item.imageUrl)" alt="item" />
            </div>
            <div class="rec-main">
              <h4>{{ rec.item?.itemName }}</h4>
              <p class="text">适用病害：{{ rec.item?.targetDisease || '未知' }}</p>
              <p class="text">主要功能：{{ rec.item?.mainFunction || '暂无' }}</p>
              <p v-if="rec.item?.price != null" class="price">参考价格：{{ rec.item.price }}</p>
            </div>
          </article>
        </div>
      </section>

      <section class="comments">
        <h3>评论</h3>
        <div class="editor">
          <textarea
            v-model="commentContent"
            rows="3"
            placeholder="发表你的看法或向作者提问"
          />
          <div class="editor-footer">
            <div class="replying" v-if="replyingTo">
              正在回复：{{ replyingTo.author.nickname || replyingTo.author.username }}
              <button class="link" type="button" @click="cancelReply">取消回复</button>
            </div>
            <button
              class="btn primary"
              type="button"
              :disabled="submittingComment || !commentContent.trim()"
              @click="submitComment"
            >
              {{ submittingComment ? '提交中...' : '发表评论' }}
            </button>
          </div>
        </div>

        <div v-if="comments.length" class="comment-list">
          <div v-for="c in comments" :key="c.id" class="comment">
            <div class="avatar-wrap" @click.stop="addFriendByUsername(c.author.username)">
              <img class="avatar" :src="getAvatar(c.author)" alt="commenter" />
            </div>
            <div class="comment-main">
              <div class="line1">
                <span class="name">{{ c.author.nickname || c.author.username }}</span>
                <span class="tag" v-if="identityLabel(c.author.userType)">{{ identityLabel(c.author.userType) }}</span>
                <span class="time">{{ formatTime(c.createdAt) }}</span>
                <button class="reply-btn" type="button" @click="startReply(c)">回复</button>
              </div>
              <p class="text">{{ c.content }}</p>

              <div v-if="c.replies && c.replies.length" class="replies">
                <div v-for="r in c.replies" :key="r.id" class="reply">
                  <div class="avatar-wrap small" @click.stop="addFriendByUsername(r.author.username)">
                    <img class="avatar" :src="getAvatar(r.author)" alt="reply" />
                  </div>
                  <div class="comment-main">
                    <div class="line1">
                      <span class="name">{{ r.author.nickname || r.author.username }}</span>
                      <span class="tag" v-if="identityLabel(r.author.userType)">{{ identityLabel(r.author.userType) }}</span>
                      <span class="time">{{ formatTime(r.createdAt) }}</span>
                      <button class="reply-btn" type="button" @click="startReply(r)">回复</button>
                    </div>
                    <p class="text">{{ r.content }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
        <p v-else class="no-comments">暂无评论，快来抢沙发～</p>
      </section>
    </section>

    <el-drawer v-model="showAuthorCard" title="作者信息" direction="rtl" size="320px">
      <div v-if="authorInfo" class="author-card">
        <img class="avatar big" :src="getAvatar(authorInfo)" alt="author" />
        <h3>{{ authorInfo.nickname || authorInfo.username }}</h3>
        <p class="id">账号：{{ authorInfo.username }}</p>
        <p class="role" v-if="identityLabel(authorInfo.userType)">{{ identityLabel(authorInfo.userType) }}</p>
        <p class="phone" v-if="authorInfo.phone">联系电话：{{ authorInfo.phone }}</p>
        <button class="btn primary" type="button" :disabled="consulting" @click="consultAuthor">
          {{ consulting ? '正在发起咨询...' : '咨询TA' }}
        </button>
      </div>
      <p v-else>暂无作者信息</p>
    </el-drawer>
  </div>
</template>

<style scoped>
.detail-page { min-height: 100%; padding: 16px 18px 24px; background: linear-gradient(180deg,#eff6ff,#f9fafb); }
.top-bar { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.top-bar h2 { margin: 0; font-size: 1.3rem; }
.link { border: none; background: transparent; color: #2563eb; cursor: pointer; font-size: 0.9rem; padding: 0; }

.state { margin-top: 24px; text-align: center; color: #6b7280; font-size: 0.95rem; }
.state.error { color: #b91c1c; }

.article { display: grid; gap: 18px; background: rgba(255,255,255,0.96); border-radius: 16px; padding: 16px 18px 20px; box-shadow: 0 8px 24px rgba(148,163,184,0.35); }
.article-head { display: flex; align-items: center; }
.author { display: flex; align-items: center; gap: 10px; cursor: pointer; }
.avatar { width: 40px; height: 40px; border-radius: 999px; object-fit: cover; background: #e5e7eb; }
.info { display: grid; gap: 2px; }
.name-line { display: flex; align-items: center; gap: 6px; }
.name { font-weight: 600; }
.tag { padding: 2px 8px; border-radius: 999px; background: #e0f2fe; color: #0369a1; font-size: 0.75rem; }
.meta { font-size: 0.8rem; color: #6b7280; display: flex; flex-wrap: wrap; gap: 8px; }

.body .content { white-space: pre-wrap; line-height: 1.6; font-size: 0.95rem; color: #111827; margin: 0; }
.images { margin-top: 10px; display: grid; grid-template-columns: repeat(auto-fill,minmax(160px,1fr)); gap: 8px; }
.images img { width: 100%; height: 140px; object-fit: cover; border-radius: 10px; }

.recs h3 { margin: 0 0 8px; font-size: 1rem; }
.rec-list { display: grid; grid-template-columns: repeat(auto-fill,minmax(220px,1fr)); gap: 10px; }
.rec-card { display: grid; grid-template-columns: 90px minmax(0,1fr); gap: 8px; padding: 8px 10px; border-radius: 10px; background: #f9fafb; border: 1px solid #e5e7eb; }
.rec-cover img { width: 100%; height: 80px; object-fit: cover; border-radius: 8px; }
.rec-main h4 { margin: 0 0 4px; font-size: 0.95rem; }
.rec-main .text { margin: 0 0 2px; font-size: 0.82rem; color: #4b5563; }
.rec-main .price { margin: 0; font-size: 0.82rem; color: #16a34a; }

.comments h3 { margin: 0 0 10px; font-size: 1rem; }
.editor { border: 1px solid #e5e7eb; border-radius: 10px; padding: 8px 10px; background: #f9fafb; display: grid; gap: 6px; }
.editor textarea { width: 100%; resize: vertical; min-height: 72px; border-radius: 6px; border: 1px solid #e5e7eb; padding: 6px 8px; font-size: 0.9rem; }
.editor-footer { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.replying { font-size: 0.8rem; color: #4b5563; display: flex; align-items: center; gap: 6px; }
.btn { border: none; border-radius: 999px; padding: 6px 14px; font-size: 0.85rem; cursor: pointer; background: #e5e7eb; color: #111827; }
.btn.primary { background: #22c55e; color: #ecfdf5; }

.comment-list { margin-top: 12px; display: grid; gap: 10px; }
.comment { display: grid; grid-template-columns: auto minmax(0,1fr); gap: 8px; }
.avatar-wrap { cursor: pointer; }
.avatar-wrap.small .avatar { width: 30px; height: 30px; }
.comment-main { display: grid; gap: 2px; }
.line1 { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; font-size: 0.8rem; color: #4b5563; }
.line1 .name { font-weight: 500; color: #111827; }
.line1 .time { margin-left: auto; color: #9ca3af; }
.text { margin: 0 0 2px; font-size: 0.88rem; color: #111827; }
.replies { margin-top: 6px; padding-left: 36px; display: grid; gap: 6px; }
.reply { display: grid; grid-template-columns: auto minmax(0,1fr); gap: 6px; }
.reply-btn { border: none; background: transparent; color: #2563eb; cursor: pointer; font-size: 0.78rem; padding: 0 4px; }
.reply-btn:hover { text-decoration: underline; }
.no-comments { margin-top: 6px; font-size: 0.85rem; color: #9ca3af; }

.author-card { display: grid; gap: 8px; text-align: center; }
.author-card .avatar.big { width: 72px; height: 72px; margin: 0 auto 4px; }
.author-card h3 { margin: 0; }
.author-card .id, .author-card .role, .author-card .phone { margin: 0; font-size: 0.85rem; color: #4b5563; }

@media (max-width: 768px) {
  .article { padding: 12px 12px 16px; }
}
</style>
