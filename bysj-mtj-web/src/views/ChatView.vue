<script setup lang="ts">
import { ref, onMounted, watch, computed, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { apiFetch, assetUrl } from '@/utils/api'
import { ElMessage } from 'element-plus'

type StoredUser = { username: string; nickname?: string; role?: string; userType?: string; avatarUrl?: string; phone?: string }
interface ChatUser { username: string; nickname?: string; role?: string; userType?: string; avatarUrl?: string; phone?: string }
interface ChatRoom { id: number; type: 'direct' | 'group'; name?: string; members?: ChatUser[]; lastMessage?: string; lastTime?: string; unread?: number; system?: boolean }
interface ChatMessage { id: number; roomId: number; sender: string; content: string; createdAt: string; senderInfo?: ChatUser }

const route = useRoute()
const userRaw = sessionStorage.getItem('user')
const currentUser: StoredUser | null = userRaw ? JSON.parse(userRaw) : null
const username = currentUser?.username || ''

const userDirectory = ref<Record<string, ChatUser>>({})

function normalizeUser(raw: any): ChatUser {
  if (!raw) return { username: '' }
  const uname = raw.username || ''
  return {
    username: uname,
    nickname: raw.nickname || '',
    role: raw.role || 'user',
    userType: raw.userType || '',
    phone: raw.phone || '',
    avatarUrl: raw.avatarUrl && raw.avatarUrl.length > 0
      ? raw.avatarUrl
      : `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(uname)}`,
  }
}

function cacheUser(raw: any): ChatUser {
  const info = normalizeUser(raw)
  if (info.username) {
    const existing = userDirectory.value[info.username]
    const merged: ChatUser = {
      username: info.username,
      nickname: info.nickname || existing?.nickname || '',
      role: info.role || existing?.role,
      userType: info.userType || existing?.userType,
      phone: info.phone || existing?.phone || '',
      avatarUrl: info.avatarUrl || existing?.avatarUrl,
    }
    userDirectory.value[info.username] = merged
    return merged
  }
  return info
}

const currentUserInfo = currentUser ? cacheUser(currentUser) : null

const friends = ref<ChatUser[]>([])
const rooms = ref<ChatRoom[]>([])
const activeRoomId = ref<number | null>(null)
const messages = ref<ChatMessage[]>([])

const msgMenu = ref<{ visible: boolean; x: number; y: number; message: ChatMessage | null}>({
  visible: false,
  x: 0,
  y: 0,
  message: null,
})
const forwardingMessage = ref<ChatMessage | null>(null)

function identitySuffix(info?: ChatUser | null): string {
  if (!info) return ''
  if (info.role === 'admin') return '系统管理员'
  if (info.userType === 'expert') return '农林专家'
  if (info.userType === 'farmer') return '种植户'
  return ''
}

function formatDisplayName(userName: string, info?: ChatUser | null): string {
  const cached = info || userDirectory.value[userName]
  const base = cached?.nickname && cached.nickname.length > 0 ? cached.nickname : userName
  const suffix = identitySuffix(cached)
  return suffix ? `${base}（${suffix}）` : base
}

function ensureArrayMembers(raw: any): ChatUser[] {
  if (!Array.isArray(raw)) return []
  return raw.map(cacheUser)
}

const displayMessages = computed(() => {
  const out: Array<{ type: 'time' | 'msg'; text?: string; data?: ChatMessage }> = []
  let lastTs = 0
  for (const m of messages.value) {
    const ts = Date.parse(m.createdAt)
    if (!lastTs || ts - lastTs > 5 * 60 * 1000) {
      out.push({ type: 'time', text: formatTime(m.createdAt) })
      lastTs = ts
    }
    out.push({ type: 'msg', data: m })
  }
  return out
})
const activeRoom = computed(() => {
  return rooms.value.find(r => r.id === activeRoomId.value) || null
})
const isSystemRoom = computed(() => {
  const r = activeRoom.value
  return !!(r && r.type === 'group' && (r.system || r.name === '系统通知'))
})
const composerDisabled = computed(() => !activeRoomId.value || isSystemRoom.value)
const input = ref('')
const inputRef = ref<HTMLTextAreaElement | null>(null)
const friendQuery = ref('')
const friendFeedback = ref<{ type: 'success' | 'error' | ''; text: string }>({ type: '', text: '' })
const addingFriend = ref(false)
let friendFeedbackTimer: number | null = null

function showFriendFeedback(type: 'success' | 'error' | '' = '', text = '') {
  if (friendFeedbackTimer !== null) {
    clearTimeout(friendFeedbackTimer)
    friendFeedbackTimer = null
  }
  friendFeedback.value = { type, text }
  if (text && type) {
    friendFeedbackTimer = window.setTimeout(() => {
      friendFeedback.value = { type: '', text: '' }
      friendFeedbackTimer = null
    }, 2600)
  }
}
let ws: WebSocket | null = null
let reconnectTimer: number | null = null

function resolveWsUrl(name: string): string | null {
  if (!name) return null
  const override = (import.meta.env.VITE_WS_BASE as string | undefined)?.trim()
  const encoded = encodeURIComponent(name)
  if (override) {
    const normalized = override.replace(/\/?$/, '')
    return `${normalized}?username=${encoded}`
  }

  const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
  let host = window.location.host
  if (import.meta.env.DEV) {
    const hostname = window.location.hostname || 'localhost'
    const devPort = '8099'
    host = `${hostname}:${devPort}`
  }
  return `${protocol}://${host}/ws?username=${encoded}`
}

function scheduleReconnect() {
  if (reconnectTimer !== null) return
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null
    connectWS()
  }, 2000)
}

async function fetchFriends() {
  if (!username) {
    friends.value = []
    return
  }
  try {
    const res = await apiFetch(`/friends?username=${encodeURIComponent(username)}`)
    const ct = res.headers.get('content-type') || ''
    if (!res.ok) {
      const raw = await res.text().catch(() => '')
      console.error('好友列表请求失败', res.status, raw)
      friends.value = []
      return
    }
    if (!ct.includes('application/json')) {
      const raw = await res.text().catch(() => '')
      console.error('好友列表响应类型不是 JSON:', ct, raw)
      friends.value = []
      return
    }
    const data = await res.json()
    friends.value = Array.isArray(data) ? data.map(cacheUser) : []
  } catch (err) {
    console.error('好友接口请求异常', err)
    friends.value = []
  }
}

async function fetchRooms() {
  try {
    const res = await apiFetch(`/chat/rooms?username=${encodeURIComponent(username)}`)
    const ct = res.headers.get('content-type') || ''
    const raw = await res.text().catch(() => '')
    if (!res.ok) {
      console.error('拉取房间失败', res.status, raw)
      rooms.value = []
      return
    }
    if (!raw) {
      console.warn('房间接口返回空响应')
      rooms.value = []
      return
    }
    let list: any = []
    if (ct.includes('application/json')) {
      try {
        list = JSON.parse(raw)
      } catch (e) {
        console.error('房间列表 JSON 解析失败', e, raw)
        rooms.value = []
        return
      }
    } else {
      console.error('房间接口响应类型不是 JSON:', ct, raw)
      rooms.value = []
      return
    }
    const previous = activeRoomId.value
    rooms.value = (Array.isArray(list) ? list : []).map((r: any) => ({
      id: r.id,
      type: r.type,
      name: r.name,
      createdAt: r.createdAt,
      members: ensureArrayMembers(r.members),
      lastMessage: r.lastMessage,
      lastTime: r.lastTime,
      unread: 0,
      system: !!r.system,
    }))
    rooms.value.forEach(r => {
      (r.members || []).forEach(member => cacheUser(member))
    })
    await fetchRoomPreviews()
    const hasPrevious = previous !== null && rooms.value.some(r => r.id === previous)
    activeRoomId.value = hasPrevious ? previous : null
    if (!activeRoomId.value && rooms.value.length) {
      const sorted = [...rooms.value].sort((a, b) => new Date(b.lastTime || 0).getTime() - new Date(a.lastTime || 0).getTime())
      const first = sorted[0]
      if (first) activeRoomId.value = first.id
    }
  } catch (err) {
    console.error('房间接口请求异常', err)
    rooms.value = []
  }
}

async function fetchRoomPreviews() {
  if (!rooms.value.length) return
  const previews = await Promise.all(
    rooms.value.map(r => apiFetch(`/chat/rooms/${r.id}/messages?limit=1`).then(res => res.json()).catch(() => []))
  )
  previews.forEach((msgs: any, idx: number) => {
    const r = rooms.value[idx]
    const m = Array.isArray(msgs) && msgs.length ? msgs[msgs.length - 1] : null
    if (r && m) updateRoomPreview(r.id, m.content, m.createdAt, false)
  })
  if (!activeRoomId.value) {
    const sorted = [...rooms.value].sort((a, b) => new Date(b.lastTime || 0).getTime() - new Date(a.lastTime || 0).getTime())
    const first = sorted[0]
    if (first) activeRoomId.value = first.id
  }
}

async function fetchMessages(roomId: number) {
  try {
    const res = await apiFetch(`/chat/rooms/${roomId}/messages?limit=50`)
    const ct = res.headers.get('content-type') || ''
    const raw = await res.text().catch(() => '')
    if (!res.ok) {
      console.error('拉取消息失败', res.status, raw)
      messages.value = []
      return
    }
    if (!raw) {
      console.warn('消息接口返回空响应')
      messages.value = []
      return
    }
    let list: any = []
    if (ct.includes('application/json')) {
      try {
        list = JSON.parse(raw)
      } catch (e) {
        console.error('消息列表 JSON 解析失败', e, raw)
        messages.value = []
        return
      }
    } else {
      console.error('消息接口响应类型不是 JSON:', ct, raw)
      messages.value = []
      return
    }
    messages.value = Array.isArray(list)
      ? list.map((raw: any) => {
          const senderInfo = raw?.senderInfo ? cacheUser(raw.senderInfo) : cacheUser({ username: raw.sender })
          return {
            id: raw.id,
            roomId: raw.roomId,
            sender: raw.sender,
            content: raw.content,
            createdAt: raw.createdAt,
            senderInfo,
          } as ChatMessage
        })
      : []
    const last = messages.value[messages.value.length - 1]
    if (last) {
      updateRoomPreview(roomId, last.content, last.createdAt, false)
    }
    const r = rooms.value.find(r => r.id === roomId)
    if (r) r.unread = 0
    scrollToBottom()
  } catch (err) {
    console.error('消息接口请求异常', err)
    messages.value = []
  }
}

function connectWS() {
  if (!username) return
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return
  const url = resolveWsUrl(username)
  if (!url) return
  if (reconnectTimer !== null) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  try {
    ws = new WebSocket(url)
  } catch (err) {
    console.error('WebSocket 创建失败', err)
    scheduleReconnect()
    return
  }
  ws.onopen = () => {
    console.log('WebSocket 已连接')
    if (reconnectTimer !== null) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }
  ws.onmessage = (ev) => {
    let data: any = null
    try {
      data = JSON.parse(ev.data)
    } catch (err) {
      console.warn('WebSocket 消息解析失败', err, ev.data)
      return
    }
    if (data?.type === 'message') {
      const senderInfo = data.senderInfo ? cacheUser(data.senderInfo) : cacheUser({ username: data.sender })
      if (data.roomId === activeRoomId.value) {
        messages.value.push({
          id: data.id || Date.now(),
          roomId: data.roomId,
          sender: data.sender,
          content: data.content,
          createdAt: data.createdAt,
          senderInfo,
        })
        updateRoomPreview(data.roomId, data.content, data.createdAt, false)
        scrollToBottom()
      } else {
        updateRoomPreview(data.roomId, data.content, data.createdAt, true)
      }
    } else if (data?.type === 'recall') {
      const roomId = data.roomId as number | undefined
      const messageId = data.messageId as number | undefined
      if (!roomId || !messageId) return
      if (roomId === activeRoomId.value) {
        const target = messages.value.find(m => m.id === messageId)
        if (target) {
          target.content = data.content || '此消息已撤回'
          if (typeof data.createdAt === 'string' && data.createdAt) {
            target.createdAt = data.createdAt
          }
        }
        const last = messages.value[messages.value.length - 1]
        if (last) {
          updateRoomPreview(last.roomId, last.content, last.createdAt, false)
        }
      }
    } else if (data?.type === 'delete') {
      const roomId = data.roomId as number | undefined
      const messageId = data.messageId as number | undefined
      if (!roomId || !messageId) return
      if (roomId === activeRoomId.value) {
        const idx = messages.value.findIndex(m => m.id === messageId)
        if (idx !== -1) {
          messages.value.splice(idx, 1)
          const last = messages.value[messages.value.length - 1]
          if (last) {
            updateRoomPreview(last.roomId, last.content, last.createdAt, false)
          } else {
            updateRoomPreview(roomId, '', new Date().toISOString(), false)
          }
        }
      }
    }
  }
  ws.onerror = (err) => {
    console.warn('WebSocket 发生错误', err)
  }
  ws.onclose = () => {
    console.log('WebSocket 已断开，尝试重连')
    ws = null
    scheduleReconnect()
  }
}

function scrollToBottom() {
  setTimeout(() => {
    const el = document.querySelector('.messages')
    if (el) el.scrollTop = el.scrollHeight
  }, 0)
}

async function sendTextMessage(text: string) {
  const content = text.trim()
  if (!content || !activeRoomId.value) return
  if (isSystemRoom.value) {
    ElMessage.warning('系统通知会话仅用于接收通知，不能发送消息')
    return
  }
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify({ roomId: activeRoomId.value, content }))
  } else {
    await apiFetch(`/chat/rooms/${activeRoomId.value}/messages`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ sender: username, content }),
    })
    await fetchMessages(activeRoomId.value)
  }
  updateRoomPreview(activeRoomId.value, content, new Date().toISOString(), false)
}

async function send() {
  const text = input.value.trim()
  if (!text) return
  await sendTextMessage(text)
  input.value = ''
}

function formatTime(iso: string): string {
  const d = new Date(iso)
  const now = new Date()
  const sameDay = d.toDateString() === now.toDateString()
  const pad = (n: number) => n.toString().padStart(2, '0')
  if (sameDay) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${d.getMonth() + 1}/${d.getDate()} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
function updateRoomPreview(roomId: number, content: string, createdAt: string, incUnread = false) {
  const r = rooms.value.find(r => r.id === roomId)
  if (r) {
    r.lastMessage = content
    r.lastTime = createdAt
    if (incUnread) r.unread = (r.unread || 0) + 1
  }
}
function getAvatar(name: string, info?: ChatUser | null): string {
  const candidate = info || userDirectory.value[name]
  if (candidate?.avatarUrl) return assetUrl(candidate.avatarUrl)
  if (name === username && currentUserInfo?.avatarUrl) return assetUrl(currentUserInfo.avatarUrl)
  return `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(name)}`
}
function formatAttachmentUrl(url?: string) {
  return url ? assetUrl(url) : ''
}
function handleKeyDown(e: KeyboardEvent) {
  if (isSystemRoom.value) return
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    send()
  }
}

const showEmoji = ref(false)
const emojis = ['😀', '😁', '😂', '🤣', '😃', '😄', '😉', '😊', '😍', '😘', '😎', '😭', '😡', '👍', '👌', '🙏', '🌿', '🍅']
const fileInputRef = ref<HTMLInputElement | null>(null)

function toggleEmojiPanel() {
  showEmoji.value = !showEmoji.value
}

function insertEmoji(emoji: string) {
  const el = inputRef.value
  if (!el) {
    input.value += emoji
    return
  }
  const start = el.selectionStart ?? input.value.length
  const end = el.selectionEnd ?? start
  const v = input.value
  input.value = v.slice(0, start) + emoji + v.slice(end)
  nextTick(() => {
    el.focus()
    const pos = start + emoji.length
    el.selectionStart = el.selectionEnd = pos
  })
}

async function onFileChange(ev: Event) {
  const inputEl = ev.target as HTMLInputElement
  const file = inputEl.files?.[0]
  if (!file || !activeRoomId.value) return
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await apiFetch('/chat/attachments', { method: 'POST', body: fd })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new Error(text || '上传失败')
    }
    const data = await res.json()
    const url: string = data.url
    const originalName: string = data.originalName || file.name
    const ct: string = data.contentType || ''
    const isImage = ct.startsWith('image/')
    const content = isImage ? `[图片] ${url}` : `[附件] ${originalName} ${url}`
    await sendTextMessage(content)
  } catch (e: any) {
    ElMessage.error(e?.message || '附件上传失败')
  } finally {
    inputEl.value = ''
  }
}

function openFilePicker() {
  if (!activeRoomId.value) {
    ElMessage.warning('请先选择一个会话')
    return
  }
  if (isSystemRoom.value) {
    ElMessage.warning('系统通知会话不支持发送附件')
    return
  }
  fileInputRef.value?.click()
}

function parseAttachment(content: string): { kind: 'image' | 'file' | 'none'; url?: string; name?: string } {
  if (!content) return { kind: 'none' }
  if (content.startsWith('[图片] ')) {
    const url = content.slice('[图片] '.length).trim()
    return { kind: 'image', url }
  }
  if (content.startsWith('[附件] ')) {
    const rest = content.slice('[附件] '.length).trim()
    const lastSpace = rest.lastIndexOf(' ')
    if (lastSpace > 0) {
      const name = rest.slice(0, lastSpace)
      const url = rest.slice(lastSpace + 1)
      return { kind: 'file', url, name }
    }
    return { kind: 'file', url: rest, name: '附件' }
  }
  return { kind: 'none' }
}

async function openRoom(roomId: number) {
  if (forwardingMessage.value && username) {
    const source = forwardingMessage.value
    try {
      const room = rooms.value.find(r => r.id === roomId)
      if (room && room.type === 'group' && (room.system || room.name === '系统通知')) {
        ElMessage.warning('系统通知会话不支持接收转发')
      } else {
        const res = await apiFetch(`/chat/rooms/${roomId}/messages`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ sender: username, content: source.content }),
        })
        if (!res.ok) {
          const text = await res.text().catch(() => '')
          throw new Error(text || '转发失败')
        }
        ElMessage.success('转发成功')
      }
    } catch (err: any) {
      console.error('转发失败', err)
      ElMessage.error(err?.message || '转发失败')
    } finally {
      forwardingMessage.value = null
    }
  }
  activeRoomId.value = roomId
}

function roomDisplayName(r: ChatRoom): string {
  if (r.type === 'group') {
    if (r.name) return r.name
    const names = (r.members || []).map(m => formatDisplayName(m.username, m))
    return names.length ? names.join('、') : '群聊'
  }
  const members = Array.isArray(r.members) ? r.members : []
  const others = members.filter(m => m.username !== username)
  if (others.length === 0) {
    return currentUserInfo ? formatDisplayName(currentUserInfo.username, currentUserInfo) : '单聊'
  }
  return others.map(m => formatDisplayName(m.username, m)).join('、')
}

function openMsgMenu(ev: MouseEvent, msg: ChatMessage) {
  ev.preventDefault()
  ev.stopPropagation()
  msgMenu.value = {
    visible: true,
    x: ev.clientX,
    y: ev.clientY,
    message: msg,
  }
}

function closeMsgMenu() {
  msgMenu.value.visible = false
  msgMenu.value.message = null
}

function startForward(msg: ChatMessage) {
  forwardingMessage.value = msg
  closeMsgMenu()
  ElMessage.info('请选择左侧会话作为转发目标')
}

function cancelForward() {
  forwardingMessage.value = null
}

async function recallMessage(msg: ChatMessage) {
  closeMsgMenu()
  if (!msg.id || !username) return
  try {
    const res = await apiFetch(`/chat/messages/${msg.id}/recall`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username }),
    })
    if (!res.ok) {
      const text = await res.text().catch(() => '')
      throw new Error(text || '撤回失败')
    }
    const target = messages.value.find(m => m.id === msg.id)
    if (target) {
      target.content = '此消息已撤回'
    }
    const last = messages.value[messages.value.length - 1]
    if (last) {
      updateRoomPreview(last.roomId, last.content, last.createdAt, false)
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '撤回失败')
  }
}

async function deleteMessage(msg: ChatMessage) {
  closeMsgMenu()
  if (!msg.id || !username) return
  const ok = window.confirm('确定要删除这条消息吗？')
  if (!ok) return
  try {
    const res = await apiFetch(`/chat/messages/${msg.id}?username=${encodeURIComponent(username)}`, {
      method: 'DELETE',
    })
    if (!res.ok && res.status !== 204) {
      const text = await res.text().catch(() => '')
      throw new Error(text || '删除失败')
    }
    const idx = messages.value.findIndex(m => m.id === msg.id)
    if (idx !== -1) {
      messages.value.splice(idx, 1)
      const last = messages.value[messages.value.length - 1]
      if (last) {
        updateRoomPreview(last.roomId, last.content, last.createdAt, false)
      }
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

async function addFriend() {
  if (!friendQuery.value.trim()) {
    showFriendFeedback('error', '请输入对方账号或手机号')
    return
  }
  if (!currentUser?.username) {
    showFriendFeedback('error', '请先登录')
    return
  }
  addingFriend.value = true
  try {
    const resp = await apiFetch('/friends/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        requester: currentUser.username,
        target: friendQuery.value.trim()
      })
    })
    const data = await resp.json()
    if (!resp.ok || !data.success) {
      throw new Error(data.message || '添加好友失败')
    }
    showFriendFeedback('success', data.message || '添加好友成功')
    friendQuery.value = ''
    // Refresh friends list and rooms
    await fetchFriends()
    await fetchRooms()
  } catch (e: any) {
    showFriendFeedback('error', e.message || '添加好友失败')
  } finally {
    addingFriend.value = false
  }
}

async function openDirectWith(otherUsername: string) {
  if (!username || !otherUsername) return
  try {
    const resp = await apiFetch('/chat/rooms/direct', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userA: username, userB: otherUsername })
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
    // Check if room already exists in local list
    const existing = rooms.value.find(r => r.id === roomId)
    if (!existing) {
      await fetchRooms() // Refresh rooms list
    }
    activeRoomId.value = roomId
  } catch (e: any) {
    ElMessage.error(e.message || '打开会话失败')
  }
}

onMounted(async () => {
  await fetchFriends()
  await fetchRooms()
  const rawRoomId = route.query.roomId
  if (rawRoomId) {
    const rid = Number(Array.isArray(rawRoomId) ? rawRoomId[0] : rawRoomId)
    if (!Number.isNaN(rid)) {
      const exists = rooms.value.some(r => r.id === rid)
      if (exists) {
        activeRoomId.value = rid
      }
    }
  }
  // 依赖 activeRoomId 的 watch 自动拉取消息，避免重复
  connectWS()
})

onUnmounted(() => {
  if (ws) {
    try {
      ws.close()
    } catch (err) {
      console.warn('关闭 WebSocket 失败', err)
    }
    ws = null
  }
  if (reconnectTimer !== null) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (friendFeedbackTimer !== null) {
    clearTimeout(friendFeedbackTimer)
    friendFeedbackTimer = null
  }
})

watch(activeRoomId, async (rid) => { if (rid) await fetchMessages(rid) })
</script>

<template>
  <!-- 侧栏：微信风格对话列表 -->
  <div class="chat">
    <div
      v-if="msgMenu.visible && msgMenu.message"
      class="msg-menu"
      :style="{ top: msgMenu.y + 'px', left: msgMenu.x + 'px' }"
      @click.stop
    >
      <button
        type="button"
        class="msg-menu-item"
        @click="startForward(msgMenu.message)"
      >
        转发
      </button>
      <button
        v-if="msgMenu.message.sender === username"
        type="button"
        class="msg-menu-item"
        @click="recallMessage(msgMenu.message)"
      >
        撤回
      </button>
      <button
        v-if="msgMenu.message.sender === username"
        type="button"
        class="msg-menu-item danger"
        @click="deleteMessage(msgMenu.message)"
      >
        删除
      </button>
    </div>
    <aside class="sidebar">
      <header>消息</header>
      <div class="new-chat">
        <input
          v-model="friendQuery"
          type="text"
          placeholder="输入对方账号或手机号添加好友"
        />
        <button @click="addFriend" :disabled="addingFriend">
          {{ addingFriend ? '添加中...' : '添加好友' }}
        </button>
      </div>
      <div v-if="forwardingMessage" class="forward-tip">
        <span>正在转发一条消息，请在下方选择目标会话</span>
        <button type="button" @click.stop="cancelForward">取消</button>
      </div>
      <p v-if="friendFeedback.text" :class="['friend-feedback', friendFeedback.type]">
        {{ friendFeedback.text }}
      </p>
      <div class="friends-section">
        <div class="section-title">好友</div>
        <ul class="friend-list">
          <li v-if="friends.length === 0" class="empty">暂无好友</li>
          <li v-for="f in friends" :key="f.username">
            <button @click="openDirectWith(f.username)">
              {{ formatDisplayName(f.username, f) }}
            </button>
          </li>
        </ul>
      </div>
      <div class="section-title rooms-title">会话</div>
      <ul class="rooms">
        <li v-for="r in rooms" :key="r.id" :class="{active: r.id === activeRoomId}" @click="openRoom(r.id)">
          <div class="room-avatar">{{ (roomDisplayName(r)[0] || '群') }}</div>
          <div class="room-main">
            <div class="room-top">
              <span class="name">{{ roomDisplayName(r) }}</span>
              <span class="time">{{ r.lastTime ? formatTime(r.lastTime) : '' }}</span>
            </div>
            <div class="room-bottom">
              <span class="preview">{{ r.lastMessage || (r.type === 'group' ? '进入群聊' : '开始聊天') }}</span>
              <span v-if="r.unread" class="badge">{{ r.unread }}</span>
            </div>
          </div>
        </li>
      </ul>
    </aside>

    <!-- 主面板：消息气泡、时间分隔、头像 -->
    <main class="panel">
      <header class="panel-header" v-if="activeRoom">
        <div class="room-title">{{ roomDisplayName(activeRoom) }}</div>
        <div class="room-meta">
          <span v-if="activeRoom.type === 'group'">
            {{ (activeRoom.members || []).length }} 人群聊
          </span>
          <span v-else>单聊</span>
        </div>
      </header>
      <div class="messages" @click="closeMsgMenu">
        <template v-if="!activeRoomId">
          <div class="empty-tip">请选择左侧会话或添加好友后开始聊天</div>
        </template>
        <template v-else-if="!displayMessages.length">
          <div class="empty-tip">暂无消息，发送第一条问候吧～</div>
        </template>
        <template v-else>
          <template v-for="(item, idx) in displayMessages" :key="idx">
            <div v-if="item.type === 'time'" class="time-sep">{{ item.text }}</div>
            <div
              v-else
              class="msg"
              :class="item.data!.sender === username ? 'me' : 'other'"
              @contextmenu.prevent.stop="openMsgMenu($event, item.data!)"
            >
              <img
                v-if="item.data!.sender !== username"
                class="avatar"
                :src="getAvatar(item.data!.sender, item.data!.senderInfo)"
                :alt="item.data!.sender"
              />
              <div class="bubble">
                <div class="sender-line">
                  <span class="sender">{{ formatDisplayName(item.data!.sender, item.data!.senderInfo) }}</span>
                  <span class="msg-time">{{ formatTime(item.data!.createdAt) }}</span>
                </div>
                <div class="text">
                  <template v-if="parseAttachment(item.data!.content).kind === 'image'">
                    <img :src="formatAttachmentUrl(parseAttachment(item.data!.content).url)" class="image-attachment" />
                  </template>
                  <template v-else-if="parseAttachment(item.data!.content).kind === 'file'">
                    <a :href="formatAttachmentUrl(parseAttachment(item.data!.content).url)" class="file-attachment" target="_blank" rel="noopener">
                      {{ parseAttachment(item.data!.content).name || '附件' }}
                    </a>
                  </template>
                  <template v-else>
                    {{ item.data!.content }}
                  </template>
                </div>
              </div>
              <img
                v-if="item.data!.sender === username"
                class="avatar"
                :src="getAvatar(item.data!.sender, item.data!.senderInfo)"
                :alt="item.data!.sender"
              />
            </div>
          </template>
        </template>
      </div>
      <footer class="composer" :class="{ disabled: composerDisabled }">
        <div class="tools">
          <button title="表情" @click="toggleEmojiPanel">😊</button>
          <button title="附件" @click="openFilePicker">📎</button>
          <div v-if="showEmoji" class="emoji-panel">
            <button
              v-for="e in emojis"
              :key="e"
              type="button"
              class="emoji-btn"
              @click="insertEmoji(e)"
            >{{ e }}</button>
          </div>
          <input ref="fileInputRef" type="file" class="file-input" @change="onFileChange" />
        </div>
        <textarea
          v-model="input"
          ref="inputRef"
          class="input"
          rows="2"
          :disabled="composerDisabled"
          :placeholder="!activeRoomId
            ? '请选择左侧会话后再发送消息'
            : (isSystemRoom ? '此为系统通知会话，仅用于接收系统通知，无法发送消息' : '输入消息，Enter发送，Shift+Enter换行')"
          @keydown="handleKeyDown"
        />
        <button class="send" @click="send" :disabled="!input.trim() || !activeRoomId || isSystemRoom">发送</button>
      </footer>
    </main>
  </div>
</template>

<style scoped>
.chat {
  display: grid;
  grid-template-columns: 260px 1fr;
  min-height: 680px;
  width: 100%;
  max-width: none;
  margin: 0;
  background:
    radial-gradient(140% 180% at 0% 0%, rgba(56, 189, 248, 0.16), transparent 60%),
    radial-gradient(120% 200% at 100% 0%, rgba(34, 197, 94, 0.14), transparent 60%),
    linear-gradient(135deg, #f4f7ff, #f9fafb);
  border-radius: 18px;
  box-shadow:
    0 18px 45px rgba(15, 23, 42, 0.12),
    0 0 0 1px rgba(148, 163, 184, 0.35);
  overflow: hidden;
}

.sidebar {
  border-right: 1px solid rgba(226, 232, 240, 0.9);
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(24px);
}
.sidebar header {
  padding: 12px 16px;
  font-weight: 600;
  color: #0f172a;
  border-bottom: 1px solid rgba(226, 232, 240, 1);
  font-size: 14px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.new-chat { display: grid; grid-template-columns: 1fr auto; gap: 4px; padding: 8px 10px 10px; border-bottom: 1px solid #d6d6d6; background: #ededed; }
.new-chat input { padding: 6px 8px; border: 1px solid #d6d6d6; border-radius: 3px; font-size: 13px; outline: none; background-color: #fbfbfb; }
.new-chat input:focus { border-color: #41b035; background-color: #fff; }
.new-chat button { padding: 6px 10px; background: #41b035; color: #fff; border: none; border-radius: 3px; font-size: 12px; cursor: pointer; white-space: nowrap; }
.new-chat button:hover { background: #38962e; }
.new-chat button:disabled { opacity: 0.7; cursor: default; }
.forward-tip { padding: 6px 10px; background: #fef9c3; color: #92400e; font-size: 12px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #facc15; }
.forward-tip button { border: none; background: transparent; color: #b45309; cursor: pointer; font-size: 12px; padding: 2px 4px; border-radius: 3px; }
.forward-tip button:hover { background: rgba(250, 204, 21, 0.25); }
.friend-feedback { margin: 4px 10px 0; font-size: 0.8rem; }
.friend-feedback.success { color: #41b035; }
.friend-feedback.error { color: #dc2626; }
.friends-section { padding: 6px 10px 8px; border-bottom: 1px solid #d6d6d6; background: #f5f5f5; }
.section-title { font-size: 0.8rem; font-weight: 600; color: #4b5563; margin: 2px 2px 4px; }
.friend-list { list-style: none; margin: 0; padding: 2px 0 0; display: grid; gap: 4px; }
.friend-list li button { width: 100%; text-align: left; border: none; background: transparent; padding: 4px 6px; border-radius: 3px; cursor: pointer; color: #1f2937; font-size: 0.8rem; }
.friend-list li button:hover { background: #e0e0e0; }
.friend-list li.empty { color: #a0a0a0; font-size: 0.8rem; padding: 2px 0 4px; }
.rooms-title { margin-top: 4px; padding-left: 10px; }
.rooms { list-style: none; margin: 0; padding: 0; flex: 1; overflow-y: auto; }
.rooms li { padding: 8px 10px; border-bottom: 1px solid #dedede; cursor: pointer; display: grid; grid-template-columns: 40px 1fr; gap: 8px; align-items: center; transition: background 0.12s ease; }
.rooms li:hover { background: #dfdfdf; }
.rooms li.active { background: #ffffff; }
.room-avatar { width: 36px; height: 36px; border-radius: 4px; background: #d6d6d6; display: flex; align-items: center; justify-content: center; font-weight: 600; color: #1f2937; }
.room-main { display: grid; gap: 2px; }
.room-top { display: flex; justify-content: space-between; align-items: center; }
.rooms .name { color: #111827; font-weight: 600; font-size: 0.9rem; }
.rooms .time { color: #9ca3af; font-size: 0.75em; }
.room-bottom { display: flex; justify-content: space-between; align-items: center; }
.rooms .preview { color: #6b7280; font-size: 0.8em; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.rooms .badge { background: #ef4444; color: #fff; border-radius: 10px; padding: 1px 5px; font-size: 11px; }

.panel {
  display: grid;
  grid-template-rows: auto 1fr auto;
  min-height: 0;
  background:
    linear-gradient(145deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.98));
  backdrop-filter: blur(14px);
}

.panel-header {
  padding: 12px 18px;
  border-bottom: 1px solid rgba(226, 232, 240, 1);
  background: linear-gradient(120deg, rgba(255, 255, 255, 0.98), rgba(239, 246, 255, 0.98));
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.room-title {
  font-weight: 600;
  color: #0f172a;
  font-size: 14px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.room-meta {
  font-size: 12px;
  color: #6b7280;
}

.messages {
  padding: 16px 20px 10px;
  overflow: auto;
  background:
    radial-gradient(120% 180% at 100% 0%, rgba(59, 130, 246, 0.18), transparent 60%),
    linear-gradient(180deg, #f9fafb, #eff6ff);
}
.time-sep {
  text-align: center;
  font-size: 12px;
  color: #9ca3af;
  margin: 10px 0;
}
.msg {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  margin: 8px 0;
}
.msg.other { justify-content: flex-start; }
.msg.me { justify-content: flex-end; }
.avatar {
  width: 32px;
  height: 32px;
  border-radius: 999px;
  background: #e5e7eb;
  object-fit: cover;
  box-shadow: 0 4px 12px rgba(148, 163, 184, 0.4);
}
.bubble {
  position: relative;
  max-width: 62%;
  padding: 8px 12px;
  border-radius: 16px;
  background: #ffffff;
  color: #111827;
  border: 1px solid rgba(226, 232, 240, 1);
  box-shadow: 0 8px 20px rgba(15, 23, 42, 0.04);
}

.bubble .sender-line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 11px;
  color: #6b7280;
  margin-bottom: 4px;
}
.bubble .text { white-space: pre-wrap; line-height: 1.5; }
.msg.me .bubble {
  background: linear-gradient(135deg, #3b82f6, #22c55e);
  border-color: rgba(191, 219, 254, 1);
  color: #f9fafb;
}
.msg.me .bubble .sender-line { flex-direction: row-reverse; }
.msg.me .bubble .text { text-align: right; }
.msg-time { font-size: 10px; opacity: 0.9; }

.msg.other .bubble::before {
  content: '';
  position: absolute;
  left: -7px;
  top: 18px;
  border-width: 7px;
  border-style: solid;
  border-color: transparent #ffffff transparent transparent;
  filter: drop-shadow(-1px 0 0 rgba(226, 232, 240, 1));
}
.msg.me .bubble::before {
  content: '';
  position: absolute;
  right: -7px;
  top: 18px;
  border-width: 7px;
  border-style: solid;
  border-color: transparent transparent transparent #3b82f6;
}

.empty-tip {
  margin-top: 80px;
  text-align: center;
  color: #9ca3af;
  font-size: 0.9rem;
}

.msg-menu { position: fixed; z-index: 50; background: #ffffff; border: 1px solid #e5e7eb; border-radius: 6px; box-shadow: 0 10px 30px rgba(15, 23, 42, 0.18); padding: 4px 0; min-width: 120px; }
.msg-menu-item { width: 100%; padding: 6px 12px; text-align: left; border: none; background: transparent; font-size: 13px; color: #374151; cursor: pointer; }
.msg-menu-item:hover { background: #eff6ff; }
.msg-menu-item.danger { color: #dc2626; }
.msg-menu-item.danger:hover { background: #fee2e2; }

.composer {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 8px;
  padding: 10px 16px;
  border-top: 1px solid rgba(226, 232, 240, 1);
  background: linear-gradient(180deg, #f9fafb, #eff6ff);
  position: relative;
}
.tools button {
  border: none;
  background: transparent;
  font-size: 18px;
  cursor: pointer;
  padding: 4px;
  border-radius: 999px;
  color: #4b5563;
}
.tools button:hover {
  background: #e5f0ff;
  box-shadow: 0 0 0 1px rgba(148, 163, 184, 0.5);
}
.input {
  width: 100%;
  resize: none;
  padding: 8px 10px;
  border: 1px solid rgba(209, 213, 219, 1);
  border-radius: 999px;
  font-size: 13px;
  outline: none;
  background: #ffffff;
  color: #111827;
}
.input:focus {
  border-color: rgba(59, 130, 246, 1);
  box-shadow:
    0 0 0 1px rgba(59, 130, 246, 1),
    0 0 0 4px rgba(59, 130, 246, 0.26);
  background: #ffffff;
}
.send {
  padding: 7px 18px;
  background: linear-gradient(135deg, rgba(59, 130, 246, 1), rgba(34, 197, 94, 1));
  color: #ecfdf5;
  border: none;
  border-radius: 999px;
  font-weight: 500;
  cursor: pointer;
  font-size: 13px;
  box-shadow: 0 10px 24px rgba(37, 99, 235, 0.35);
}
.send:hover {
  background: linear-gradient(135deg, rgba(59, 130, 246, 1), rgba(52, 211, 153, 1));
}
.send:disabled {
  opacity: 0.55;
  cursor: default;
  box-shadow: none;
}

.composer.disabled { opacity: 0.65; }

.emoji-panel { position: absolute; bottom: 52px; left: 14px; background: #fff; border: 1px solid #d6d6d6; border-radius: 4px; padding: 6px; display: grid; grid-template-columns: repeat(6, 1fr); gap: 2px; box-shadow: 0 4px 12px rgba(0,0,0,0.18); z-index: 10; }
.emoji-btn { border: none; background: transparent; font-size: 18px; cursor: pointer; padding: 2px; border-radius: 3px; }
.emoji-btn:hover { background: #f3f4f6; }
.file-input { display: none; }

.image-attachment { max-width: 100%; height: auto; border-radius: 4px; }
.file-attachment { text-decoration: none; color: #2563eb; font-size: 0.9rem; }
.file-attachment:hover { text-decoration: underline; }

@media (max-width: 900px) {
  .chat { grid-template-columns: 1fr; grid-template-rows: auto 1fr; margin: 8px; max-width: 100%; }
  .sidebar { position: static; }
  .messages { padding: 10px 12px 6px; }
  .bubble { max-width: 80%; }
}
</style>