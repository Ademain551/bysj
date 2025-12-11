<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFetch, API_ORIGIN } from '@/utils/api'

interface CartItem {
  id: number
  itemId: number
  itemName: string
  plantName?: string
  price?: number
  imageUrl?: string
  targetDisease?: string
  quantity: number
  checked: boolean
}

const router = useRouter()
const loading = ref(false)
const items = ref<CartItem[]>([])
const selectAll = ref(false)

const selectedIds = computed(() => items.value.filter(i => i.checked).map(i => i.id))
const selectedTotal = computed(() => {
  return items.value
    .filter(i => i.checked)
    .reduce((sum, it) => sum + (Number(it.price || 0) * it.quantity), 0)
})

async function loadCart() {
  loading.value = true
  try {
    const resp = await apiFetch('/shop/cart')
    if (!resp.ok) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '加载购物车失败')
    }
    const data = await resp.json()
    const list = Array.isArray(data) ? data : []
    items.value = list.map((it: any) => ({
      id: it.id,
      itemId: it.itemId,
      itemName: it.itemName,
      plantName: it.plantName,
      price: it.price,
      imageUrl: it.imageUrl,
      targetDisease: it.targetDisease,
      quantity: it.quantity || 1,
      checked: false,
    }))
    selectAll.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '加载购物车失败')
  } finally {
    loading.value = false
  }
}

function toggleSelectAll() {
  items.value.forEach(it => { it.checked = selectAll.value })
}

function onItemCheckChange() {
  const allChecked = items.value.length > 0 && items.value.every(i => i.checked)
  selectAll.value = allChecked
}

async function removeItem(id: number) {
  try {
    const resp = await apiFetch(`/shop/cart/${id}`, { method: 'DELETE' })
    if (!resp.ok && resp.status !== 204) {
      const text = await resp.text().catch(() => '')
      throw new Error(text || '移出购物车失败')
    }
    items.value = items.value.filter(it => it.id !== id)
    ElMessage.success('已移出购物车')
  } catch (e: any) {
    ElMessage.error(e?.message || '移出购物车失败')
  }
}

async function checkout(cartItemIds: number[]) {
  if (!cartItemIds.length) {
    ElMessage.warning('请先选择要结算的商品')
    return
  }
  try {
    const resp = await apiFetch('/shop/cart/checkout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ cartItemIds }),
    })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '结算失败')
    }
    const orderId = (data as any)?.orderId
    if (!orderId) {
      ElMessage.success('结算成功')
    } else {
      ElMessage.success('结算成功，正在前往支付页')
      router.push({ name: 'shop-pay', params: { id: String(orderId) } })
    }
  } catch (e: any) {
    const msg = e?.message || '结算失败'
    ElMessage.error(msg)
    if (msg.includes('收货地址')) {
      router.push('/profile')
    }
  }
}

function checkoutSelected() {
  checkout(selectedIds.value)
}

function checkoutSingle(id: number) {
  checkout([id])
}

function goItemDetail(itemId: number) {
  router.push({ name: 'shop-item', params: { id: String(itemId) } })
}

onMounted(() => {
  loadCart()
})
</script>

<template>
  <div class="cart-page">
    <div class="card header-card">
      <div class="title">购物车</div>
      <div class="sub">已选 {{ selectedIds.length }} 件 · 合计 <span class="price">¥ {{ selectedTotal.toFixed(2) }}</span></div>
    </div>

    <div class="card" v-if="loading">
      正在加载购物车...
    </div>

    <div v-else-if="!items.length" class="card empty-card">
      <p>购物车还是空的，去识别结果页添加推荐物品吧。</p>
    </div>

    <div v-else class="card list-card">
      <div class="list-header">
        <label class="select-all">
          <input type="checkbox" v-model="selectAll" @change="toggleSelectAll" />
          <span>全选</span>
        </label>
        <div class="summary">
          已选 {{ selectedIds.length }} 件 · 合计 <span class="price">¥ {{ selectedTotal.toFixed(2) }}</span>
        </div>
        <button class="btn primary" type="button" @click="checkoutSelected">结算所选</button>
      </div>

      <div class="item-list">
        <div v-for="it in items" :key="it.id" class="item-row">
          <input
            type="checkbox"
            v-model="it.checked"
            class="row-checkbox"
            @change="onItemCheckChange"
          />
          <div class="thumb" @click="goItemDetail(it.itemId)">
            <img
              v-if="it.imageUrl"
              :src="API_ORIGIN + it.imageUrl"
              alt="item"
            />
            <div v-else class="placeholder">暂无图片</div>
          </div>
          <div class="info" @click="goItemDetail(it.itemId)">
            <div class="name" :title="it.itemName">{{ it.itemName }}</div>
            <div class="tags">
              <span v-if="it.plantName" class="tag">{{ it.plantName }}</span>
              <span v-if="it.targetDisease" class="tag subtle">应对：{{ it.targetDisease }}</span>
            </div>
          </div>
          <div class="price-col">
            <div class="price">¥ {{ (it.price || 0).toFixed(2) }}</div>
            <div class="qty">× {{ it.quantity }}</div>
            <div class="line">小计 ¥ {{ ((it.price || 0) * it.quantity).toFixed(2) }}</div>
          </div>
          <div class="actions-col">
            <button class="btn" type="button" @click="checkoutSingle(it.id)">结算该项</button>
            <button class="btn outline" type="button" @click="removeItem(it.id)">移出</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.cart-page {
  min-height: 100%;
  padding: 16px 0 24px;
  max-width: 900px;
  margin: 0 auto;
}

.card {
  background: rgba(255, 255, 255, 0.8);
  border-radius: 12px;
  border: 1px solid rgba(148, 163, 184, 0.35);
  padding: 12px 14px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.05);
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
  gap: 10px;
}

.list-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.select-all {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.9rem;
  color: #374151;
}

.summary {
  margin-left: auto;
  font-size: 0.9rem;
  color: #4b5563;
}

.price {
  color: #16a34a;
  font-weight: 600;
}

.item-list {
  display: grid;
  gap: 8px;
}

.item-row {
  display: grid;
  grid-template-columns: auto 80px minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
  padding: 8px 6px;
  border-radius: 10px;
  background: #f9fafb;
}

.row-checkbox {
  margin-left: 4px;
}

.thumb {
  width: 80px;
  height: 70px;
  border-radius: 8px;
  overflow: hidden;
  background: #e5e7eb;
  cursor: pointer;
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
  font-size: 0.78rem;
  color: #6b7280;
}

.info {
  display: grid;
  gap: 4px;
  cursor: pointer;
}

.name {
  font-size: 0.95rem;
  font-weight: 500;
  color: #111827;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.tag {
  font-size: 0.75rem;
  padding: 2px 8px;
  border-radius: 999px;
  background: #e0f2fe;
  color: #0369a1;
}

.tag.subtle {
  background: #f3f4f6;
  color: #4b5563;
}

.price-col {
  text-align: right;
  font-size: 0.8rem;
  color: #4b5563;
}

.price-col .price {
  font-size: 0.9rem;
}

.actions-col {
  display: grid;
  gap: 4px;
}

.btn {
  border: none;
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 0.83rem;
  cursor: pointer;
  background: #e5e7eb;
  color: #111827;
  white-space: nowrap;
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
  .item-row {
    grid-template-columns: auto 72px minmax(0, 1.4fr);
    grid-template-rows: auto auto;
  }
  .price-col,
  .actions-col {
    grid-column: 3 / 4;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
  }
}
</style>
