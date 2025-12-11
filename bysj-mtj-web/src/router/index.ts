import { createRouter, createWebHistory } from 'vue-router'

const HomeView = () => import('../views/HomeView.vue')
const LoginView = () => import('../views/LoginView.vue')
const RegisterView = () => import('../views/RegisterView.vue')
const DetectView = () => import('../views/DetectView.vue')
const HistoryView = () => import('../views/HistoryView.vue')
const KnowledgeView = () => import('../views/KnowledgeView.vue')
const TechGuideListView = () => import('../views/TechGuideListView.vue')
const TechGuideDetailView = () => import('../views/TechGuideDetailView.vue')
const AdminView = () => import('../views/AdminView.vue')
const ProfileView = () => import('../views/ProfileView.vue')
const ChatView = () => import('../views/ChatView.vue')
const NewsDetailView = () => import('../views/NewsDetailView.vue')
const CartView = () => import('../views/CartView.vue')
const ShopItemDetailView = () => import('../views/ShopItemDetailView.vue')
const ShopPayView = () => import('../views/ShopPayView.vue')
const OrdersView = () => import('../views/OrdersView.vue')
const ShopPayResultView = () => import('../views/ShopPayResultView.vue')

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/home' },
    { path: '/home', name: 'home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/news/:id', name: 'news-detail', component: NewsDetailView, meta: { requiresAuth: true } },
    { path: '/detect', name: 'detect', component: DetectView, meta: { requiresAuth: true } },
    { path: '/history', name: 'history', component: HistoryView, meta: { requiresAuth: true } },
    { path: '/knowledge', name: 'knowledge', component: KnowledgeView, meta: { requiresAuth: true } },
    { path: '/guide', name: 'guide', component: TechGuideListView, meta: { requiresAuth: true } },
    { path: '/guide/:id', name: 'guide-detail', component: TechGuideDetailView, meta: { requiresAuth: true } },
    { path: '/cart', name: 'cart', component: CartView, meta: { requiresAuth: true } },
    { path: '/shop/item/:id', name: 'shop-item', component: ShopItemDetailView, meta: { requiresAuth: true } },
    { path: '/shop/pay/:id', name: 'shop-pay', component: ShopPayView, meta: { requiresAuth: true } },
    { path: '/orders', name: 'orders', component: OrdersView, meta: { requiresAuth: true } },
    { path: '/shop/pay/result/:id', name: 'pay-result', component: ShopPayResultView, meta: { requiresAuth: true } },
    { path: '/admin', name: 'admin', component: AdminView, meta: { requiresAuth: true } },
    { path: '/chat', name: 'chat', component: ChatView, meta: { requiresAuth: true } },
    { path: '/login', name: 'login', component: LoginView },
    { path: '/register', name: 'register', component: RegisterView },
    { path: '/profile', name: 'Profile', component: ProfileView, meta: { requiresAuth: true } },
  ],
})

router.beforeEach((to, from, next) => {
  const requiresAuth = !!to.meta?.requiresAuth
  const userStr = sessionStorage.getItem('user')
  
  if (requiresAuth && !userStr) {
    next({ path: '/login' })
    return
  }
  
  // 检查管理员权限
  if (to.path === '/admin') {
    if (!userStr) {
      next({ path: '/login' })
      return
    }
    try {
      const user = JSON.parse(userStr)
      if (user.role !== 'admin') {
        // 非管理员用户不能访问管理后台
        next({ path: '/home' })
        return
      }
    } catch {
      next({ path: '/login' })
      return
    }
  }
  
  next()
})

export default router
