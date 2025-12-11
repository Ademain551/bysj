<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFetch, apiUrl } from '@/utils/api'

const route = useRoute()
const router = useRouter()

const order = ref<any | null>(null)
const loading = ref(false)
const paying = ref(false)

const orderId = route.params.id as string | undefined

async function loadOrder() {
  if (!orderId) {
    ElMessage.error('无效的订单编号')
    return
  }
  loading.value = true
  try {
    const resp = await apiFetch(`/shop/orders/${encodeURIComponent(orderId)}`)
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '加载订单失败')
    }
    const data = await resp.json()
    order.value = data
  } catch (e: any) {
    ElMessage.error(e?.message || '加载订单失败')
  } finally {
    loading.value = false
  }
}

function payNow() {
  if (!orderId) return
  if (!order.value || order.value.status !== 'CREATED') {
    ElMessage.warning('当前订单状态不可支付')
    return
  }
  if (paying.value) return
  paying.value = true
  try {
    const url = apiUrl(`/shop/pay/alipay?orderId=${encodeURIComponent(orderId)}`)
    // 跳转到后端支付宝支付页
    window.location.href = url
  } catch (e: any) {
    paying.value = false
    ElMessage.error(e?.message || '跳转支付宝失败')
  }
}

onMounted(() => {
  loadOrder()
})
</script>

<template>
  <div class="pay-page">
    <div class="card" v-if="loading">
      正在加载订单信息...
    </div>

    <div v-else-if="!order" class="card">
      未找到订单。
    </div>

    <div v-else class="card pay-card">
      <header class="head">
        <button class="link" type="button" @click="router.back()">返回</button>
        <h2>订单支付</h2>
      </header>

      <section class="info">
        <div class="row"><span class="label">订单号</span><span class="value">{{ order.id }}</span></div>
        <div class="row"><span class="label">状态</span><span class="value">{{ order.status }}</span></div>
        <div class="row"><span class="label">金额</span><span class="price">¥ {{ (order.totalAmount || 0).toFixed(2) }}</span></div>
        <div class="row"><span class="label">收货地址</span><span class="value">{{ order.shippingAddress || '未填写' }}</span></div>
      </section>

      <section class="items" v-if="Array.isArray(order.items) && order.items.length">
        <h3>订单明细</h3>
        <ul>
          <li v-for="it in order.items" :key="it.itemId" class="item">
            <div class="name">{{ it.itemName }}</div>
            <div class="meta">数量 × {{ it.quantity }} · 单价 ¥ {{ (it.unitPrice || 0).toFixed(2) }}</div>
            <div class="line">小计 ¥ {{ (it.lineTotal || 0).toFixed(2) }}</div>
          </li>
        </ul>
      </section>

      <section class="qr-section">
        <div class="qr-placeholder">
          <div class="title">扫码支付（示意）</div>
          <div class="desc">此处为模拟支付二维码界面，可根据需要替换为真实二维码。</div>
        </div>
      </section>

      <footer class="actions">
        <button class="btn primary" type="button" :disabled="paying" @click="payNow">
          {{ paying ? '跳转中...' : '前往支付宝支付' }}
        </button>
        <button class="btn outline" type="button" @click="router.push('/home')">返回首页</button>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.pay-page {
  min-height: 100%;
  padding: 16px 0 24px;
  max-width: 720px;
  margin: 0 auto;
}

.card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  padding: 14px 16px 18px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

.pay-card {
  display: grid;
  gap: 12px;
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

.info {
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

.items h3 {
  margin: 0 0 6px;
  font-size: 0.95rem;
}

.items ul {
  list-style: none;
  padding: 0;
  margin: 0;
  display: grid;
  gap: 6px;
}

.item {
  padding: 6px 8px;
  border-radius: 8px;
  background: #f9fafb;
}

.item .name {
  font-size: 0.9rem;
  font-weight: 500;
}

.item .meta {
  font-size: 0.8rem;
  color: #6b7280;
}

.item .line {
  font-size: 0.8rem;
  color: #16a34a;
}

.qr-section {
  display: flex;
  justify-content: center;
}

.qr-placeholder {
  width: 220px;
  height: 220px;
  border-radius: 24px;
  background: radial-gradient(circle at 20% 20%, #dbeafe, #f9fafb);
  border: 1px dashed #93c5fd;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 12px;
}

.qr-placeholder .title {
  font-size: 0.95rem;
  font-weight: 600;
  margin-bottom: 4px;
}

.qr-placeholder .desc {
  font-size: 0.8rem;
  color: #6b7280;
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
  .pay-page {
    padding: 12px 0 20px;
  }
}
</style>
