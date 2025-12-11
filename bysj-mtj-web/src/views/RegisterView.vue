<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiFetch } from '@/utils/api'

const router = useRouter()
const username = ref('')
const nickname = ref('')
const phone = ref('')
const password = ref('')
const confirmPassword = ref('')
const userType = ref<'expert' | 'farmer'>('expert')
const loading = ref(false)
const error = ref('')
const success = ref('')
const avatarFile = ref<File | null>(null)

function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  avatarFile.value = (input.files && input.files[0]) || null
}

function goLogin() {
  router.push('/login')
}

async function handleRegister() {
  error.value = ''
  success.value = ''
  if (!username.value || !nickname.value || !phone.value || !password.value || !confirmPassword.value) {
    error.value = '请填写完整的注册信息'
    return
  }
  if (!/^\d{8}$/.test(username.value)) {
    error.value = '账号需为8位数字'
    return
  }
  const trimmedPhone = phone.value.trim()
  if (!/^\d{11}$/.test(trimmedPhone)) {
    error.value = '手机号需为11位数字'
    return
  }
  if (password.value.length < 6) {
    error.value = '密码长度至少 6 位'
    return
  }
  if (password.value !== confirmPassword.value) {
    error.value = '两次输入的密码不一致'
    return
  }
  loading.value = true
  try {
    let res: Response
    if (avatarFile.value) {
      const fd = new FormData()
      fd.append('username', username.value)
      fd.append('nickname', nickname.value)
      fd.append('phone', trimmedPhone)
      fd.append('password', password.value)
      fd.append('userType', userType.value)
      fd.append('avatar', avatarFile.value)
      res = await apiFetch('/register', { method: 'POST', body: fd })
    } else {
      res = await apiFetch('/register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: username.value,
          nickname: nickname.value,
          phone: trimmedPhone,
          password: password.value,
          userType: userType.value,
        }),
      })
    }
    const data = await res.json()

    if (res.ok && data?.success) {
      success.value = '注册成功，正在跳转登录页...'
      setTimeout(() => router.push('/login'), 1200)
    } else {
      error.value = data?.message || '注册失败，请稍后重试'
    }
  } catch (e) {
    error.value = '注册失败，请检查网络或稍后重试'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-shell">
      <section class="auth-left">
        <h1 class="auth-title">农作物叶片病虫害识别系统</h1>
        <p class="auth-sub">快速完成账号注册，享受完整的识别、知识库与消息服务。</p>
        <ul class="auth-highlights">
          <li>· 支持专家与种植户两种身份，权限清晰</li>
          <li>· 与识别记录、知识库、聊天服务打通</li>
          <li>· 后台管理支撑，便于后续运营维护</li>
        </ul>
      </section>

      <section class="auth-right">
        <div class="auth-card">
          <h2 class="card-title">注册账号</h2>
          <form class="form" @submit.prevent="handleRegister">
            <label class="field">
              <span class="label">账号（8位数字）</span>
              <input v-model.trim="username" type="text" placeholder="请输入8位数字账号" required />
            </label>

            <label class="field">
              <span class="label">昵称</span>
              <input v-model.trim="nickname" type="text" placeholder="请输入昵称" required />
            </label>

            <label class="field">
              <span class="label">密码</span>
              <input v-model="password" type="password" placeholder="请输入密码（至少6位）" required />
            </label>

            <label class="field">
              <span class="label">确认密码</span>
              <input v-model="confirmPassword" type="password" placeholder="请再次输入密码" required />
            </label>

            <label class="field">
              <span class="label">手机号（11位）</span>
              <input v-model.trim="phone" type="tel" placeholder="请输入11位手机号" />
            </label>

            <label class="field">
              <span class="label">头像</span>
              <input type="file" accept="image/*" @change="onAvatarChange" />
            </label>

            <div class="field">
              <span class="label">身份</span>
              <div class="radios">
                <label>
                  <input v-model="userType" type="radio" value="expert" /> 农林专家
                </label>
                <label>
                  <input v-model="userType" type="radio" value="farmer" /> 种植户
                </label>
              </div>
            </div>

            <button class="btn auth-submit" type="submit" :disabled="loading">
              {{ loading ? '正在注册...' : '注册' }}
            </button>

            <p v-if="error" class="error">{{ error }}</p>
            <p v-if="success" class="success">{{ success }}</p>
          </form>

          <p class="switch-line">
            已有账号？
            <button type="button" class="link-btn" @click="goLogin">返回登录</button>
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
  max-width: 380px;
}

.card-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: #111827;
}

.form { display: grid; gap: 14px; }
.field { display: grid; gap: 6px; }
.label { font-size: 13px; color: #4b5563; }

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

.error { margin: 6px 0 0; color: #dc2626; font-size: 13px; }
.success { margin: 6px 0 0; color: #16a34a; font-size: 13px; }

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

.radios { display: flex; gap: 1.5rem; align-items: center; }
.radios label { display: inline-flex; align-items: center; gap: 0.4rem; color: #1f2937; font-size: 0.95rem; }
.radios input[type='radio'] { accent-color: #22c55e; }

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