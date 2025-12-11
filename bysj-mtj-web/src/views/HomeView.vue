<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import WeatherWidget from '@/components/WeatherWidget.vue'
import GeolocationWidget from '@/components/GeolocationWidget.vue'
import DiseaseWarning from '@/components/DiseaseWarning.vue'
import { apiFetch } from '@/utils/api'

type StoredUser = {
  username: string
  avatarUrl?: string
  role?: string
  userType?: string
  nickname?: string
}

type ExternalNewsItem = {
  title: string
  url: string
  date?: string
}

const router = useRouter()
const user = ref<StoredUser | null>(null)

onMounted(() => {
  const raw = sessionStorage.getItem('user')
  user.value = raw ? JSON.parse(raw) : null
})

function logout() {
  sessionStorage.removeItem('user')
  try {
    apiFetch('/logout', { method: 'POST' })
  } catch {}
  router.push('/login')
}

function go(path: string) {
  router.push(path)
}

// 简易轮播
// 使用 public 静态资源：/banner/@1.jpg /banner/@2.jpg /banner/@3.jpg
const images = [
  '/banner/@1.jpg',
  '/banner/@2.jpg',
  '/banner/@3.jpg',
]
const current = ref(0)
let timer: number | null = null
onMounted(() => { timer = window.setInterval(() => { current.value = (current.value + 1) % images.length }, 3000) })
onUnmounted(() => { if (timer) window.clearInterval(timer) })
function prev() { current.value = (current.value - 1 + images.length) % images.length }
function next() { current.value = (current.value + 1) % images.length }

// 新闻滚动（来自农业农村部官网全国信息联播）
const news = ref<ExternalNewsItem[]>([])
const maxNewsToShow = 4
const newsToShow = computed(() => {
  if (!news.value || news.value.length === 0) return []
  return news.value.slice(0, maxNewsToShow)
})
async function loadNews() {
  try {
    const resp = await apiFetch('/external-news')
    if (!resp.ok) return
    const data = await resp.json()
    news.value = Array.isArray(data) ? data : []
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadNews()
})

function openNews(item: ExternalNewsItem) {
  if (!item || !item.url) return
  window.open(item.url, '_blank')
}

const isAdmin = computed(() => user.value?.role === 'admin')
</script>

<template>
  <div class="home">
    <section class="home-layout">
      <div class="main-column">
        <div class="hero card carousel">
          <img :src="images[current]" alt="banner" />
          <button class="nav prev" @click="prev">‹</button>
          <button class="nav next" @click="next">›</button>
          <div class="dots">
            <span v-for="(img, i) in images" :key="i" :class="{ active: i === current }" @click="current = i"></span>
          </div>
        </div>
        <section class="news-section">
          <div class="news card">
            <h3 class="section-title">农业农村新闻</h3>
            <ul class="news-list">
              <li
                v-for="(item, index) in newsToShow"
                :key="item.url || item.title || index"
                class="news-item"
              >
                <button class="news-link" type="button" @click="openNews(item)">
                  <span class="news-title">{{ item.title }}</span>
                </button>
              </li>
              <li v-if="!newsToShow.length" class="news-empty">
                暂无农业新闻
              </li>
            </ul>
          </div>
        </section>
      </div>
      <div class="side-column">
        <div class="top-widgets">
          <div class="card mini-card">
            <WeatherWidget />
          </div>
          <div class="card mini-card">
            <GeolocationWidget />
          </div>
        </div>
        <div class="card">
          <DiseaseWarning />
        </div>
      </div>
    </section>
  </div>
  
</template>

<style scoped>
.home { min-height: 100%; padding: 12px 0 20px; background: linear-gradient(180deg, #f0fdf4 0%, #f8fafc 100%); }
.home-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.9fr) minmax(260px, 0.9fr);
  gap: 12px;
  width: min(1120px, 100%);
  margin: 0 auto 12px;
}
.main-column { display: flex; flex-direction: column; gap: 8px; }
.hero.carousel {
  position: relative;
  height: 280px;
  overflow: hidden;
  background: var(--apple-gradient-main);
  box-shadow: 0 6px 32px 0 rgba(41,151,255,0.09);
  border-radius: 16px;
}
.carousel img { width: 100%; height: 100%; object-fit: cover; filter: saturate(1.15) brightness(0.96); }
.carousel .nav { position: absolute; top: 50%; transform: translateY(-50%); background: #fffafbcc; border: 1.5px solid #eaeef9; width: 44px; height: 44px; border-radius: 50%; cursor: pointer; font-size: 1.63rem; font-weight: 500; color: var(--apple-blue,#2997ff); box-shadow: 0 1.5px 12px #5dc0ff18; transition: box-shadow 0.23s, background 0.19s, color 0.13s; }
.carousel .nav:hover { background: #dff0fd; color: #2997ff; box-shadow: 0 3.5px 17px #2997ff24; }
.carousel .prev { left: 14px; }
.carousel .next { right: 14px; }
.carousel .dots { position: absolute; bottom: 17px; left: 0; right: 0; display: flex; justify-content: center; gap: 9px; z-index: 2; }
.carousel .dots span { width: 12px; height: 12px; border-radius: 50%; background: #f2fbff; box-shadow: 0 1px 7px #2997ff25, 0 0.5px 3px #25c46b11; cursor: pointer; transition: background 0.18s; border: 1.5px solid #c5eba7bb; }
.carousel .dots .active { background: linear-gradient(95deg,#25c46b 40%,#2997ff 100%); border-color: #4dfec6; }

.side-column { display: grid; gap: 8px; align-content: flex-start; }
.top-widgets { display: flex; gap: 8px; justify-content: flex-end; }
.mini-card { flex: 1 1 0; padding: 8px 10px; }

.news-section {
  width: 100%;
}

.news { padding: 16px 18px; background: var(--apple-glass,rgba(255,255,255,0.84)); border-radius: 16px; box-shadow: var(--apple-shadow-card); }
.news h3 { margin: 0 0 12px 0; color: var(--apple-blue,#2997ff); }
.ticker { height: 84px; overflow: hidden; display: flex; align-items: center; }
.ticker a { color: #17191c; text-decoration: none; display: block; font-size: 1.05rem; }
.ticker strong { display: block; font-size: 1.15em; color: #25c46b; }
.ticker p { margin: 6px 0 0 0; color: #387fc4; font-size: 0.98em; font-weight: 500; letter-spacing: 0.02em; }
 .news-list { margin: 0; padding: 0; list-style: none; display: flex; flex-direction: column; gap: 4px; max-height: 130px; overflow-y: auto; }
 .news-item { display: block; }
 .news-link { width: 100%; text-align: left; border: none; background: transparent; padding: 4px 0; cursor: pointer; }
 .news-link:hover .news-title { color: #25c46b; }
 .news-title { font-size: 0.98rem; color: #17191c; line-height: 1.5; white-space: normal; }
 .news-empty { font-size: 0.95rem; color: #6b7280; padding-top: 4px; }
@media (max-width: 1200px) { .hero.carousel { height: 260px; } }
@media (max-width: 900px) {
  .home-layout {
    grid-template-columns: 1fr;
  }
  .hero.carousel { height: 210px; }
}
@media (max-width: 600px) { .hero.carousel { height: 160px; } }
</style>
