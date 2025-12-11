<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFetch, assetUrl } from '@/utils/api'

type StoredUser = { username: string; nickname?: string }

const route = useRoute()
const router = useRouter()

const user = ref<StoredUser | null>(null)
const item = ref<any | null>(null)
const quantity = ref(1)
const address = ref('')
const loading = ref(false)

const itemId = computed(() => Number(route.params.id))

function ensureLoggedIn(): boolean {
  if (!user.value) {
    ElMessage.error('请先登录后再购买')
    router.push('/login')
    return false
  }
  return true
}

async function loadUser() {
  const raw = sessionStorage.getItem('user')
  user.value = raw ? JSON.parse(raw) : null
  if (user.value) {
    try {
      const resp = await apiFetch(`/user/${encodeURIComponent(user.value.username)}`)
      if (resp.ok) {
        const data = await resp.json().catch(() => ({}))
        address.value = (data as any)?.address || ''
      }
    } catch {
      // 忽略用户资料加载错误
    }
  }
}

async function loadItem() {
  const id = itemId.value
  if (!id || Number.isNaN(id)) {
    ElMessage.error('无效的物品编号')
    return
  }
  loading.value = true
  try {
    const resp = await apiFetch(`/knowledge/fzwp/${id}`)
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '加载物品信息失败')
    }
    const data = await resp.json()
    item.value = data
  } catch (e: any) {
    ElMessage.error(e?.message || '加载物品信息失败')
  } finally {
    loading.value = false
  }
}

function normalizeQuantity() {
  if (!Number.isFinite(quantity.value) || quantity.value <= 0) {
    quantity.value = 1
  } else {
    quantity.value = Math.floor(quantity.value)
  }
}

async function buyNow() {
  if (!ensureLoggedIn()) return
  normalizeQuantity()
  if (!address.value.trim()) {
    ElMessage.warning('请先在个人中心填写收货地址')
    router.push('/profile')
    return
  }
  const id = itemId.value
  if (!id) return
  try {
    const resp = await apiFetch('/shop/orders/buy-now', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ itemId: id, quantity: quantity.value }),
    })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '下单失败')
    }
    const orderId = (data as any)?.orderId
    if (orderId) {
      ElMessage.success('下单成功，正在前往支付页')
      router.push({ name: 'shop-pay', params: { id: String(orderId) } })
    } else {
      ElMessage.success('下单成功')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '下单失败')
  }
}

async function addToCart() {
  if (!ensureLoggedIn()) return
  normalizeQuantity()
  const id = itemId.value
  if (!id) return
  try {
    const resp = await apiFetch('/shop/cart/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ itemId: id, quantity: quantity.value }),
    })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '加入购物车失败')
    }
    ElMessage.success('已加入购物车')
  } catch (e: any) {
    ElMessage.error(e?.message || '加入购物车失败')
  }
}

onMounted(async () => {
  await loadUser()
  await loadItem()
})
</script>

<template>
  <div class="detail-page">
    <div class="card" v-if="loading">
      正在加载物品信息...
    </div>

    <div v-else-if="!item" class="card">
      未找到该物品。
    </div>

    <div v-else class="card item-card">
      <header class="head">
        <button class="link" type="button" @click="router.back()">返回</button>
        <h2>{{ item.itemName }}</h2>
      </header>

      <div class="body">
        <div class="left">
          <div class="thumb">
            <img v-if="item.imageUrl" :src="assetUrl(item.imageUrl)" alt="item" />
            <div v-else class="placeholder">暂无图片</div>
          </div>
        </div>
        <div class="right">
          <div class="row">
            <span class="label">适用植物</span>
            <span class="value">{{ item.plantName || '未知' }}</span>
          </div>
          <div class="row">
            <span class="label">应对病害</span>
            <span class="value">{{ item.targetDisease || '未填写' }}</span>
          </div>
          <div class="row">
            <span class="label">价格</span>
            <span class="price">¥ {{ (item.price || 0).toFixed(2) }}</span>
          </div>
          <div class="row qty-row">
            <span class="label">数量</span>
            <input
              type="number"
              min="1"
              v-model.number="quantity"
              @blur="normalizeQuantity"
            />
          </div>
          <div class="row addr-row" v-if="user">
            <span class="label">收货地址</span>
            <span class="value">{{ address || '尚未设置，请在个人中心填写收货地址' }}</span>
          </div>
        </div>
      </div>

      <footer class="actions">
        <button class="btn primary" type="button" @click="buyNow">立即购买</button>
        <button class="btn outline" type="button" @click="addToCart">加入购物车</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100%;
  padding: 16px 0 24px;
}

.card {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.3);
  padding: 14px 16px 16px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
}

.item-card {
  display: grid;
  gap: 10px;
}

.head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.head h2 {
  margin: 0;
  font-size: 1.1rem;
}

.link {
  border: none;
  background: transparent;
  color: #2563eb;
  cursor: pointer;
  font-size: 0.9rem;
  padding: 0;
}

.body {
  display: grid;
  grid-template-columns: 180px minmax(0, 1fr);
  gap: 16px;
}

.thumb {
  width: 180px;
  height: 150px;
  border-radius: 10px;
  overflow: hidden;
  background: #e5e7eb;
}

.thumb img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.85rem;
  color: #6b7280;
}

.right {
  display: grid;
  gap: 6px;
}

.row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 0.9rem;
}

.label {
  width: 72px;
  color: #6b7280;
}

.value {
  color: #111827;
}

.price {
  color: #16a34a;
  font-weight: 600;
}

.qty-row input {
  width: 90px;
  padding: 4px 6px;
  border-radius: 6px;
  border: 1px solid #e5e7eb;
}

.addr-row .value {
  font-size: 0.85rem;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.btn {
  border: none;
  border-radius: 999px;
  padding: 7px 16px;
  font-size: 0.9rem;
  cursor: pointer;
  background: #e5e7eb;
  color: #111827;
}

.btn.primary {
  background: #16a34a;
  color: #ecfdf5;
}

.btn.outline {
  background: transparent;
  border: 1px solid #e5e7eb;
}

@media (max-width: 768px) {
  .body {
    grid-template-columns: 1fr;
  }
  .thumb {
    width: 100%;
  }
}
</style>
