<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFetch } from '@/utils/api'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const orders = ref<any[]>([])
const statusFilter = ref<string>('')

function statusText(status: string): string {
  if (status === 'CREATED') return '待付款'
  if (status === 'PAID') return '待收货'
  if (status === 'COMPLETED') return '已完成'
  return status || '-'
}

async function loadOrders() {
  loading.value = true
  try {
    const resp = await apiFetch('/shop/orders')
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '加载订单失败')
    }
    const data = await resp.json()
    orders.value = Array.isArray(data) ? data : []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载订单失败')
  } finally {
    loading.value = false
  }
}

function goPay(id: number, status: string) {
  if (status === 'CREATED') {
    router.push({ name: 'shop-pay', params: { id: String(id) } })
  } else {
    router.push({ name: 'pay-result', params: { id: String(id) } })
  }
}

async function confirmReceive(id: number) {
  try {
    const resp = await apiFetch(`/shop/orders/${encodeURIComponent(String(id))}/complete`, { method: 'POST' })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '确认收货失败')
    }
    const updatedStatus = (data as any)?.status
    const target = orders.value.find(o => o.id === id)
    if (target && updatedStatus) {
      target.status = updatedStatus
    }
    ElMessage.success('已确认收货')
  } catch (e: any) {
    ElMessage.error(e?.message || '确认收货失败')
  }
}

const filteredOrders = computed(() => {
  if (!statusFilter.value) return orders.value
  return orders.value.filter((o) => o.status === statusFilter.value)
})

function applyStatusFromRoute() {
  const raw = route.query.status
  const s = typeof raw === 'string' ? raw : ''
  if (s === 'CREATED' || s === 'PAID' || s === 'COMPLETED') {
    statusFilter.value = s
  } else {
    statusFilter.value = ''
  }
}

function setStatusFilter(s: string) {
  statusFilter.value = s
  const query = s ? { status: s } : {}
  router.replace({ path: '/orders', query })
}

onMounted(() => {
  applyStatusFromRoute()
  loadOrders()
})

watch(
  () => route.query.status,
  () => {
    applyStatusFromRoute()
  }
)
</script>

<template>
  <div class="orders-page">
    <div class="card header-card">
      <div class="title">我的订单</div>
      <div class="sub">共 {{ filteredOrders.length }} 笔订单<span v-if="statusFilter">（当前：{{ statusText(statusFilter) }}）</span></div>
      <div class="tabs">
        <button
          class="tab-btn"
          :class="{ active: !statusFilter }"
          type="button"
          @click="setStatusFilter('')"
        >
          全部
        </button>
        <button
          class="tab-btn"
          :class="{ active: statusFilter === 'CREATED' }"
          type="button"
          @click="setStatusFilter('CREATED')"
        >
          待付款
        </button>
        <button
          class="tab-btn"
          :class="{ active: statusFilter === 'PAID' }"
          type="button"
          @click="setStatusFilter('PAID')"
        >
          待收货
        </button>
        <button
          class="tab-btn"
          :class="{ active: statusFilter === 'COMPLETED' }"
          type="button"
          @click="setStatusFilter('COMPLETED')"
        >
          已完成
        </button>
      </div>
    </div>

    <div class="card" v-if="loading">
      正在加载订单...
    </div>

    <div v-else-if="!filteredOrders.length" class="card empty-card">
      <p>暂时还没有订单，可以先在识别结果页购买推荐物品。</p>
    </div>

    <div v-else class="card list-card">
      <div
        v-for="o in filteredOrders"
        :key="o.id"
        class="order-row"
      >
        <div class="main">
          <div class="line1">
            <span class="oid">订单号：{{ o.id }}</span>
            <span class="status" :class="o.status">{{ statusText(o.status) }}</span>
          </div>
          <div class="line2">
            <span>下单时间：{{ o.createdAt ? new Date(o.createdAt).toLocaleString() : '-' }}</span>
          </div>
          <div class="line3">
            <span class="addr">收货地址：{{ o.shippingAddress || '未填写' }}</span>
          </div>
          <div class="items" v-if="Array.isArray(o.items) && o.items.length">
            <span v-for="it in o.items" :key="it.itemId" class="item-chip">
              {{ it.itemName }} × {{ it.quantity }}
            </span>
          </div>
        </div>
        <div class="side">
          <div class="amount">¥ {{ (o.totalAmount || 0).toFixed(2) }}</div>
          <button class="btn primary" type="button" @click="goPay(o.id, o.status)">
            {{ o.status === 'CREATED' ? '去支付' : '查看' }}
          </button>
          <button
            v-if="o.status === 'PAID'"
            class="btn outline"
            type="button"
            @click="confirmReceive(o.id)"
          >
            确认收货
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.orders-page {
  min-height: 100%;
  padding: 16px 0 24px;
  max-width: 900px;
  margin: 0 auto;
}

.card {
  background: rgba(255, 255, 255, 0.9);
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  padding: 12px 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.06);
}

.header-card {
  margin-bottom: 12px;
}

.header-card .title {
  font-size: 1.1rem;
  font-weight: 600;
  margin-bottom: 4px;
}

.header-card .sub {
  font-size: 0.85rem;
  color: #6b7280;
}

.empty-card {
  margin-top: 12px;
  text-align: center;
  font-size: 0.95rem;
  color: #6b7280;
}

.list-card {
  margin-top: 12px;
  display: grid;
  gap: 8px;
}

.order-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  padding: 8px 6px;
  border-radius: 10px;
  background: #f9fafb;
}

.main {
  display: grid;
  gap: 4px;
}

.line1 {
  display: flex;
  align-items: center;
  gap: 8px;
}

.oid {
  font-size: 0.9rem;
  font-weight: 500;
  color: #111827;
}

.status {
  font-size: 0.78rem;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e5e7eb;
  color: #4b5563;
}

.status.CREATED {
  background: #eff6ff;
  color: #1d4ed8;
}

.status.PAID {
  background: #ecfdf3;
  color: #16a34a;
}

.status.COMPLETED {
  background: #f3f4f6;
  color: #4b5563;
}

.line2,
.line3 {
  font-size: 0.82rem;
  color: #6b7280;
}

.addr {
  display: -webkit-box;
  line-clamp: 2;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.items {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.item-chip {
  font-size: 0.78rem;
  padding: 2px 6px;
  border-radius: 999px;
  background: #e5e7eb;
  color: #374151;
}

.side {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  justify-content: space-between;
  gap: 6px;
}

.amount {
  font-size: 0.95rem;
  color: #16a34a;
  font-weight: 600;
}

.btn {
  border: none;
  border-radius: 999px;
  padding: 6px 14px;
  font-size: 0.85rem;
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
  .order-row {
    grid-template-columns: 1fr;
  }
  .side {
    flex-direction: row;
    align-items: center;
  }
}
</style>
