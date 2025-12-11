<script setup lang="ts">
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { apiFetch, assetUrl } from '@/utils/api'
import IconHome from '@/components/icons/IconHome.vue'
import IconDetect from '@/components/icons/IconDetect.vue'
import IconHistory from '@/components/icons/IconHistory.vue'
import IconKnowledge from '@/components/icons/IconKnowledge.vue'
import IconChat from '@/components/icons/IconChat.vue'
import IconAdmin from '@/components/icons/IconAdmin.vue'
import IconCart from '@/components/icons/IconCart.vue'

type StoredUser = {
  username: string
  avatarUrl?: string
  role?: string
  userType?: string
  nickname?: string
  createdAt?: string
}

type NavItem = {
  to: string
  label: string
  icon: any
}

const route = useRoute()
const router = useRouter()
const user = ref<StoredUser | null>(null)
const health = ref<{ online: boolean; detail?: any } | null>(null)
let healthTimer: number | null = null

onMounted(() => {
  const raw = sessionStorage.getItem('user')
  user.value = raw ? JSON.parse(raw) : null
  fetchHealth()
  healthTimer = window.setInterval(fetchHealth, 8000)
})

onUnmounted(() => { if (healthTimer) window.clearInterval(healthTimer) })

// 路由变化时同步本地用户信息，确保登录后侧边栏头像即时刷新
watch(
  () => route.fullPath,
  () => {
    const raw = sessionStorage.getItem('user')
    user.value = raw ? JSON.parse(raw) : null
  }
)

async function fetchHealth() {
  try {
    const res = await apiFetch('/detect/health')
    const data = await res.json().catch(() => null)
    // 正确判断模型服务状态：使用fastapiAvailable字段，而不仅仅是HTTP状态码
    const fastapiAvailable = data?.fastapiAvailable === true
    health.value = { online: fastapiAvailable, detail: data }
  } catch {
    health.value = { online: false }
  }
}

// 基础导航菜单（普通用户/专家/农户使用）
const userNav: NavItem[] = [
  { to: '/home', label: '首页', icon: IconHome },
  { to: '/detect', label: '识别', icon: IconDetect },
  { to: '/history', label: '历史', icon: IconHistory },
  { to: '/knowledge', label: '知识库', icon: IconKnowledge },
  { to: '/guide', label: '技术指导', icon: IconKnowledge },
  { to: '/chat', label: '消息', icon: IconChat },
  { to: '/cart', label: '购物车', icon: IconCart },
]

// 管理员导航菜单（仅保留后台管理和消息）
const adminNav: NavItem[] = [
  { to: '/admin', label: '管理后台', icon: IconAdmin },
  { to: '/chat', label: '消息', icon: IconChat },
]

// 根据用户角色返回对应的导航菜单：管理员 = 简化菜单
const nav = computed(() => {
  if (user.value?.role === 'admin') {
    return adminNav
  }
  return userNav
})

const identityLabel = computed(() => {
  if (!user.value) return ''
  if (user.value.role === 'admin') return '系统管理员'
  if (user.value.userType === 'expert') return '农林专家'
  if (user.value.userType === 'farmer') return '种植户'
  return ''
})

function isActive(path: string): boolean {
  if (route.path === path) return true
  // 子路由（如 /guide/123）依旧高亮父级菜单
  return route.path.startsWith(path + '/')
}

function goProfile() {
  if (!user.value) {
    router.push('/login')
  } else {
    router.push('/profile')
  }
}
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <div class="header-left">
        <RouterLink to="/home" class="brand">
          <img src="@/assets/logo.svg" alt="logo" class="logo" />
          <div class="brand-text">
            <div class="title">Plant AI</div>
            <div class="sub">Leaf Disease</div>
          </div>
        </RouterLink>
        <nav class="nav">
          <RouterLink
            v-for="item in nav"
            :key="item.to"
            :to="item.to"
            class="nav-link"
            :class="{ active: isActive(item.to) }"
          >
            <component :is="item.icon" class="nav-icon" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </nav>
      </div>
      <div class="header-right">
        <div class="pill" :class="health?.online ? 'ok' : 'down'">
          <span class="dot"></span>{{ health?.online ? '模型服务 在线' : '模型服务 离线' }}
        </div>
        <RouterLink class="btn primary-cta" to="/detect">开始识别</RouterLink>
        <div v-if="user" class="user" @click="goProfile" style="cursor: pointer;">
          <img
            class="avatar"
            :src="user?.avatarUrl ? assetUrl(user?.avatarUrl) : 'https://api.dicebear.com/7.x/identicon/svg?seed=guest'"
            alt="avatar"
          />
          <div class="user-meta">
            <div class="uname">{{ user?.nickname || user?.username }}</div>
            <div v-if="identityLabel" class="identity">{{ identityLabel }}</div>
          </div>
        </div>
        <div v-else class="user">
          <img
            class="avatar"
            src="https://api.dicebear.com/7.x/identicon/svg?seed=guest"
            alt="avatar"
          />
          <div class="user-meta">
            <div class="uname">未登录</div>
            <RouterLink to="/login" class="ulogin">登录</RouterLink>
          </div>
        </div>
      </div>
    </header>

    <main class="app-main">
      <section class="app-content">
        <RouterView />
      </section>
    </main>
  </div>
</template>

<style scoped>
.app-shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.app-header {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 8px 22px;
  background:
    radial-gradient(140% 200% at 0% 0%, rgba(56, 189, 248, 0.18), transparent 60%),
    radial-gradient(140% 220% at 100% 0%, rgba(34, 197, 94, 0.16), transparent 55%),
    linear-gradient(90deg, #ffffff, #f3f4ff);
  border-bottom: 1px solid rgba(148, 163, 184, 0.35);
  box-shadow:
    0 12px 30px rgba(15, 23, 42, 0.10),
    0 0 0 1px rgba(148, 163, 184, 0.28);
  backdrop-filter: blur(18px);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 24px;
  min-width: 0;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}
.logo { width: 29px; height: 29px; }
.brand-text .title { font-weight: 840; letter-spacing: 0.12em; color: #0f172a; font-size: 1.06rem; text-transform: uppercase; text-shadow: 0 0 12px rgba(59,130,246,0.35); }
.brand-text .sub { font-size: 11.5px; color: #6b7280; letter-spacing: 0.25em; text-transform: uppercase; }

.nav {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.nav-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-radius: 6px;
  font-size: 0.95rem;
  color: #4b5563;
  text-decoration: none;
  position: relative;
  transition:
    background-color 0.16s ease,
    color 0.16s ease,
    box-shadow 0.22s ease,
    transform 0.08s ease;
}
.nav-link:hover {
  background: rgba(239, 246, 255, 0.95);
  color: var(--brand-secondary);
  box-shadow: 0 0 0 1px rgba(191,219,254,0.8);
}
.nav-link.active {
  background: linear-gradient(120deg, rgba(59,130,246,1), rgba(34,197,94,1));
  color: #ffffff;
  box-shadow:
    0 8px 22px rgba(37,99,235,0.35),
    0 0 0 1px rgba(191,219,254,0.9);
}

.nav-link::after {
  content: '';
  position: absolute;
  left: 10px;
  right: 10px;
  bottom: -4px;
  height: 2px;
  border-radius: 999px;
  background: linear-gradient(90deg, #22c55e, #38bdf8);
  transform: scaleX(0);
  transform-origin: center;
  transition: transform 0.16s ease;
}

.nav-link.active::after {
  transform: scaleX(1);
}

.nav-icon {
  width: 17px;
  height: 17px;
  filter: drop-shadow(0 0 6px rgba(56,189,248,0.5));
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 0.8rem;
  background: rgba(248, 250, 252, 0.96);
  color: #4b5563;
  box-shadow: 0 0 0 1px rgba(148,163,184,0.4);
}
.pill.ok {
  background: #ecfdf5;
  color: #16a34a;
}
.pill.down {
  background: #fef2f2;
  color: #b91c1c;
}
.pill .dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: currentColor;
}

.primary-cta {
  white-space: nowrap;
}

.user {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  object-fit: cover;
  background: #fff;
  box-shadow: 0 1px 6px rgba(15, 23, 42, 0.18);
}
.user-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.uname {
  font-size: 0.85rem;
  font-weight: 600;
  color: #111827;
}
.identity {
  font-size: 0.75rem;
  color: #6b7280;
}
.ulogin {
  font-size: 0.75rem;
  color: #2563eb;
  text-decoration: none;
}

.app-main {
  flex: 1;
  padding: 20px 24px 32px;
}

.app-content {
  width: min(1200px, 100%);
  margin: 0 auto;
}

@media (max-width: 900px) {
  .app-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 10px;
  }
  .header-right {
    width: 100%;
    flex-wrap: wrap;
    justify-content: space-between;
  }
  .nav {
    flex-wrap: nowrap;
    overflow-x: auto;
  }
  .app-main {
    padding: 16px 12px 24px;
  }
  .app-content {
    width: 100%;
  }
}
</style>
