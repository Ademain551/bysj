<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFetch } from '@/utils/api'

const route = useRoute()
const router = useRouter()

const order = ref<any | null>(null)
const loading = ref(false)

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

onMounted(() => {
  loadOrder()
})
</script>

<template>
  <div class="result-page">
    <div class="card" v-if="loading">
      正在加载支付结果...
    </div>

    <div v-else-if="!order" class="card">
      未找到订单。
    </div>

    <div v-else class="card result-card">
      <div class="icon" :class="order.status">
        <span v-if="order.status === 'PAID' || order.status === 'COMPLETED'">✔</span>
        <span v-else-if="order.status === 'CREATED'">⌛</span>
        <span v-else>ℹ</span>
      </div>
      <h2 class="title">
        {{
          order.status === 'PAID'
            ? '支付成功'
            : order.status === 'COMPLETED'
              ? '订单已完成'
              : order.status === 'CREATED'
                ? '待支付'
                : '订单状态：' + order.status
        }}
      </h2>
      <p class="amount">金额：¥ {{ (order.totalAmount || 0).toFixed(2) }}</p>
      <p class="tip" v-if="order.status === 'PAID'">
        感谢您的支付，可以在“我的订单”中查看详情。
      </p>
      <p class="tip" v-else-if="order.status === 'CREATED'">
        订单尚未完成支付，您可以继续前往支付页完成支付。
      </p>
      <p class="tip" v-else-if="order.status === 'COMPLETED'">
        订单已完成，如有问题可以在“我的订单”中查看详情或联系管理员。
      </p>

      <div class="actions">
        <button class="btn primary" type="button" @click="router.push('/home')">返回首页</button>
        <button class="btn" type="button" @click="router.push('/orders')">我的订单</button>
        <button
          v-if="order.status === 'CREATED'"
          class="btn outline"
          type="button"
          @click="router.push({ name: 'shop-pay', params: { id: String(order.id) } })"
        >
          去支付
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.result-page {
  min-height: 100%;
  padding: 16px 0 24px;
  max-width: 600px;
  margin: 0 auto;
}

.card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 14px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  padding: 18px 16px 20px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
  text-align: center;
}

.icon {
  width: 56px;
  height: 56px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  margin: 0 auto 8px;
  background: #e5e7eb;
  color: #4b5563;
}

.icon.PAID {
  background: #ecfdf3;
  color: #16a34a;
}

.icon.CREATED {
  background: #eff6ff;
  color: #1d4ed8;
}

.title {
  margin: 0 0 4px;
  font-size: 1.15rem;
}

.amount {
  margin: 0 0 8px;
  font-size: 0.98rem;
  color: #111827;
}

.tip {
  margin: 0 0 12px;
  font-size: 0.86rem;
  color: #6b7280;
}

.actions {
  display: flex;
  justify-content: center;
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
</style>
