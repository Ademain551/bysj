<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiFetch } from '@/utils/api'

const router = useRouter()
const username = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

function normalizeUserInfo(data: any) {
  const payload = data?.data || {}
  const name = payload.username || username.value
  return {
    username: name,
    role: payload.role || 'user',
    userType: payload.userType || 'farmer',
    nickname: payload.nickname || '',
    phone: payload.phone || '',
    avatarUrl:
      payload.avatarUrl && payload.avatarUrl.length > 0
        ? payload.avatarUrl
        : `https://api.dicebear.com/7.x/identicon/svg?seed=${encodeURIComponent(name)}`,
    createdAt: payload.createdAt || new Date().toISOString(),
  }
}

async function handleLogin() {
  error.value = ''
  loading.value = true
  try {
    const res = await apiFetch('/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: username.value, password: password.value }),
    })
    const data = await res.json()
    if (!res.ok || !data?.success) {
      throw new Error(data?.message || '账号或密码错误')
    }
    const userInfo = normalizeUserInfo(data)
    sessionStorage.setItem('user', JSON.stringify(userInfo))
    // 管理员登录后跳转到管理后台，普通用户跳转到首页
    if (userInfo.role === 'admin') {
      router.push('/admin')
    } else {
      router.push('/home')
    }
  } catch (e: any) {
    error.value = e?.message || '账号或密码错误'
  } finally {
    loading.value = false
  }
}

function goRegister() {
  router.push('/register')
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-left">
        <h1 class="auth-title">农作物叶片病虫害识别系统</h1>
        <p class="auth-sub">面向种植户与农林专家的一体化识别与知识服务平台。</p>
        <ul class="auth-highlights">
          <li>· 支持本地上传与实时拍照，多模型智能识别</li>
          <li>· 知识库联动，快速查看病害详情与防治建议</li>
          <li>· 历史记录与聊天服务，方便追踪与沟通</li>
        </ul>
      </section>

      <section class="auth-right">
        <div class="auth-card">
          <h2 class="card-title">账号登录</h2>
          <form class="form" @submit.prevent="handleLogin">
            <label class="field">
              <span class="label">账号或手机号</span>
              <input v-model.trim="username" type="text" placeholder="请输入账号或手机号" required />
            </label>

            <label class="field">
              <span class="label">密码</span>
              <input v-model="password" type="password" placeholder="请输入密码" required />
            </label>

            <button class="btn auth-submit" type="submit" :disabled="loading">
              {{ loading ? '正在登录...' : '登录' }}
            </button>

            <p v-if="error" class="error">{{ error }}</p>
          </form>

          <p class="switch-line">
            没有账号？
            <button type="button" class="link-btn" @click="goRegister">立即注册</button>
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
}

/* 额外的柔化遮罩，避免背景过于抢眼 */
.auth-shell {
  width: min(960px, 100%);
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  border-radius: 16px;
  border: 1px solid #e5e6eb;
  background: #ffffff;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.10);
  overflow: hidden;
}

.auth-left {
  padding: 32px 32px 32px 40px;
  background:
    radial-gradient(120% 120% at 0% 0%, rgba(34, 197, 94, 0.16), transparent 60%),
    radial-gradient(120% 120% at 100% 100%, rgba(22, 119, 255, 0.18), transparent 55%),
    #f5f7fb;
  border-right: 1px solid #eef0f5;
}

.auth-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
}

.auth-sub {
  margin: 0 0 18px;
  font-size: 14px;
  color: #4b5563;
}

.auth-highlights {
  margin: 0;
  padding: 0;
  list-style: none;
  font-size: 13px;
  color: #1f2937;
  display: grid;
  gap: 6px;
}

.auth-right {
  padding: 32px 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.auth-card {
  width: 100%;
  max-width: 360px;
}

.card-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.form {
  display: grid;
  gap: 14px;
}

.field {
  display: grid;
  gap: 6px;
}

.label {
  font-size: 13px;
  color: #4b5563;
}

input[type='text'],
input[type='password'] {
  width: 100%;
  padding: 8px 10px;
  border-radius: 6px;
  border: 1px solid #d0d3db;
  outline: none;
  background: #ffffff;
  color: #111827;
  font-size: 14px;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

input[type='text']:focus,
input[type='password']:focus {
  border-color: var(--brand-secondary);
  box-shadow: 0 0 0 1px rgba(22, 119, 255, 0.45);
}

.auth-submit {
  width: 100%;
  margin-top: 4px;
}

.error {
  margin: 6px 0 0;
  color: #dc2626;
  font-size: 13px;
}

.switch-line {
  margin-top: 16px;
  font-size: 13px;
  color: #6b7280;
}

.link-btn {
  border: none;
  background: none;
  padding: 0 0 0 4px;
  font-size: 13px;
  color: var(--brand-secondary);
  cursor: pointer;
}

@media (max-width: 768px) {
  .auth-page {
    padding: 16px;
  }
  .auth-shell {
    grid-template-columns: 1fr;
  }
  .auth-left {
    padding: 24px 20px;
    border-right: none;
    border-bottom: 1px solid #eef0f5;
  }
  .auth-right {
    padding: 24px 20px;
  }
}
</style>