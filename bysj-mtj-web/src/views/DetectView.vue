<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watchEffect } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { apiFetch, API_ORIGIN } from '@/utils/api'

type StoredUser = {
  username: string
  avatarUrl?: string
  role?: string
  userType?: string
}

interface DiseaseBrief {
  id?: number
  name: string
  plant: string
  modelLabel: string
  description?: string
}

const router = useRouter()
const route = useRoute()
const user = ref<StoredUser | null>(null)
const loading = ref(false)
const result = reactive<{
  id?: number
  predictedClass?: string
  confidence?: number
  advice?: string
  createdAt?: string
  imageUrl?: string
  previewUrl?: string
  probabilities?: Array<{ class: string; prob: number }>
  disease?: DiseaseBrief | null
  recommendedItems?: Array<{ id: number; itemName: string; plantName?: string; targetDisease?: string; price?: number; imageUrl?: string; favorited?: boolean }>
  reportUrl?: string
  hasFeedback?: boolean
  latestFeedbackId?: number
  latestFeedbackStatus?: string
}>({})
const history = ref<Array<{ predictedClass: string; confidence: number; advice: string; createdAt: string; disease?: DiseaseBrief | null }>>([])
const reportLoading = ref(false)

// Feedback state
const experts = ref<Array<{ id?: number; username: string; nickname?: string; avatarUrl?: string; createdAt?: string }>>([])
const showFeedbackDialog = ref(false)
const feedbackForm = reactive<{ expertUsername: string; comment: string }>({
  expertUsername: '',
  comment: '',
})
const submittingFeedback = ref(false)

// 纠错反馈显示控制：农户/普通用户可见，专家与管理员隐藏
const isAdmin = computed(() => user.value?.role === 'admin')
const isExpert = computed(() => user.value?.userType === 'expert')
const showFeedbackUi = computed(() => !!user.value && !isAdmin.value && !isExpert.value)

// File upload
const fileInput = ref<HTMLInputElement | null>(null)
const fallbackPort = (import.meta.env.VITE_API_PORT as string | undefined)?.trim() || '8099'
const fallbackOrigin = typeof location !== 'undefined' ? `${location.protocol}//${location.hostname}${fallbackPort ? `:${fallbackPort}` : ''}` : `http://localhost:${fallbackPort}`
const IMG_BASE = API_ORIGIN || fallbackOrigin
function onFileChange(ev: Event) {
  const input = ev.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  // 本地预览
  try { result.previewUrl = URL.createObjectURL(file) } catch {}
  detectFile(file)
}

async function sendDetectRequest(path: string, file: File, includeUsername = false) {
  const fd = new FormData()
  fd.append('file', file)
  if (includeUsername && user.value) {
    fd.append('username', user.value.username)
  }
  return apiFetch(path, { method: 'POST', body: fd })
}

async function detectFile(file: File) {
  if (!user.value) { router.push('/login'); return }
  loading.value = true
  try {
    const resp = await sendDetectRequest('/detect', file, true)
    if (!resp.ok) {
      const detailText = await resp.text().catch(() => '')
      if (resp.status === 400 && /缺少用户名|用户不存在/.test(detailText)) {
        ElMessage.error(detailText || '请先登录后再识别')
        router.push('/login')
      } else {
        ElMessage.error(`识别服务不可用（HTTP ${resp.status}${detailText ? '：' + detailText : ''}）`)
      }
      return
    }
    const data = await resp.json()
    const disease: DiseaseBrief | undefined = data.disease
    result.disease = disease || null
    result.id = typeof data.id === 'number' ? data.id : undefined
    result.predictedClass = disease?.modelLabel || data.predictedClass
    result.confidence = data.confidence
    
    // 处理防治建议，针对背景无叶片的情况提供特定中文建议
    const predictedClass = result.predictedClass || ''
    if (predictedClass.includes('Background_without_leaves') || 
        predictedClass === 'Background_without_leaves' ||
        predictedClass.includes('Suspected_background_no_leaf') ||
        predictedClass === 'Suspected_background_no_leaf') {
      result.advice = '请拍摄含有叶片的照片以识别准确结果'
    } else {
      result.advice = data.advice
    }
    
    result.createdAt = data.createdAt
    result.imageUrl = data.imageUrl
    result.probabilities = Array.isArray(data.probabilities) ? data.probabilities : undefined
    result.recommendedItems = Array.isArray(data.recommendedItems) ? data.recommendedItems : undefined
    result.reportUrl = typeof data.reportUrl === 'string' ? data.reportUrl : undefined
    // 新识别结果默认还没有纠错反馈
    result.hasFeedback = false
    result.latestFeedbackId = undefined
    result.latestFeedbackStatus = undefined
    await loadHistory()
    ElMessage.success('识别完成')
  } catch (e: any) {
    ElMessage.error(e.message || '上传识别失败')
  } finally {
    loading.value = false
  }
}

async function loadResultById(id: string | number) {
  if (!user.value) { router.push('/login'); return }
  loading.value = true
  try {
    const resp = await apiFetch(`/detect/${encodeURIComponent(String(id))}`)
    if (!resp.ok) {
      const detailText = await resp.text().catch(() => '')
      ElMessage.error(detailText || '加载识别结果失败')
      return
    }
    const data = await resp.json()
    const disease: DiseaseBrief | undefined = data.disease
    result.disease = disease || null
    result.id = typeof data.id === 'number' ? data.id : undefined
    result.predictedClass = disease?.modelLabel || data.predictedClass
    result.confidence = data.confidence

    const predictedClass = result.predictedClass || ''
    if (predictedClass.includes('Background_without_leaves') || 
        predictedClass === 'Background_without_leaves' ||
        predictedClass.includes('Suspected_background_no_leaf') ||
        predictedClass === 'Suspected_background_no_leaf') {
      result.advice = '请拍摄含有叶片的照片以识别准确结果'
    } else {
      result.advice = data.advice
    }

    result.createdAt = data.createdAt
    result.imageUrl = data.imageUrl
    result.probabilities = Array.isArray(data.probabilities) ? data.probabilities : undefined
    result.recommendedItems = Array.isArray(data.recommendedItems) ? data.recommendedItems : undefined
    result.reportUrl = typeof data.reportUrl === 'string' ? data.reportUrl : undefined
    result.hasFeedback = !!data.hasFeedback
    result.latestFeedbackId = typeof data.latestFeedbackId === 'number' ? data.latestFeedbackId : undefined
    result.latestFeedbackStatus = typeof data.latestFeedbackStatus === 'string' ? data.latestFeedbackStatus : undefined
  } catch (e: any) {
    ElMessage.error(e.message || '加载识别结果失败')
  } finally {
    loading.value = false
  }
}

async function loadExperts() {
  try {
    const resp = await apiFetch('/user/experts')
    if (!resp.ok) return
    const data = await resp.json().catch(() => [])
    experts.value = Array.isArray(data) ? data : []
  } catch {
    // 忽略专家列表加载错误，仍允许提交反馈（后台会校验）
  }
}

async function submitFeedback() {
  if (!user.value) {
    ElMessage.error('请先登录后再反馈')
    router.push('/login')
    return
  }
  if (!result.id) {
    ElMessage.warning('当前识别结果无效，无法反馈')
    return
  }
  if (!feedbackForm.expertUsername) {
    ElMessage.warning('请选择要指派的专家')
    return
  }
  if (submittingFeedback.value) return
  submittingFeedback.value = true
  try {
    const resp = await apiFetch(`/detect/${encodeURIComponent(String(result.id))}/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        username: user.value.username,
        expertUsername: feedbackForm.expertUsername,
        comment: feedbackForm.comment || '',
      }),
    })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '提交反馈失败')
    }
    result.hasFeedback = true
    result.latestFeedbackStatus = typeof (data as any)?.status === 'string' ? (data as any).status : 'PENDING_EXPERT'
    result.latestFeedbackId = typeof (data as any)?.id === 'number' ? (data as any).id : result.latestFeedbackId
    ElMessage.success('反馈已提交，等待专家处理')
    showFeedbackDialog.value = false
  } catch (e: any) {
    ElMessage.error(e?.message || '提交反馈失败')
  } finally {
    submittingFeedback.value = false
  }
}

function ensureReportUrl(): string | null {
	const url = result.reportUrl
	if (!url) {
		ElMessage.warning('当前识别结果尚未生成报告')
		return null
	}
	return url.startsWith('http') ? url : (API_ORIGIN ? API_ORIGIN + url : url)
}

function viewReport() {
	const full = ensureReportUrl()
	if (!full) return
	window.open(full, '_blank', 'noopener')
}

function downloadReport() {
	const full = ensureReportUrl()
	if (!full) return
	// 通过 fetch 获取 PDF Blob，再用 blob URL 触发下载，避免跨域下载属性被忽略导致页面跳转
	fetch(full)
		.then(async (resp) => {
			if (!resp.ok) {
				const text = await resp.text().catch(() => '')
				ElMessage.error(text || '下载识别报告失败')
				return
			}
			return resp.blob()
		})
		.then((blob) => {
			if (!blob) return
			const url = URL.createObjectURL(blob)
			const a = document.createElement('a')
			// 使用文件名作为下载名（如果能从 URL 中解析到）
			const parts = full.split('/')
			const fname = parts[parts.length - 1] || 'detect-report.pdf'
			a.href = url
			a.download = fname
			document.body.appendChild(a)
			a.click()
			document.body.removeChild(a)
			URL.revokeObjectURL(url)
		})
		.catch((e: any) => {
			ElMessage.error(e?.message || '下载识别报告失败')
		})
}

async function generateReport() {
	if (!user.value) {
		ElMessage.error('请先登录后再生成报告')
		return
	}
	const id = result.id
	if (!id) {
		ElMessage.warning('当前识别结果无效，无法生成报告')
		return
	}
	if (reportLoading.value) return
	reportLoading.value = true
	try {
		const resp = await apiFetch(`/detect/${encodeURIComponent(String(id))}/report`, { method: 'POST' })
		if (!resp.ok) {
			const text = await resp.text().catch(() => '')
			ElMessage.error(text || '生成识别报告失败')
			return
		}
		const data = await resp.json()
		if (typeof data.reportUrl === 'string') {
			result.reportUrl = data.reportUrl
			ElMessage.success('识别报告生成成功')
		} else {
			ElMessage.error('生成识别报告失败：返回数据不正确')
		}
	} catch (e: any) {
		ElMessage.error(e?.message || '生成识别报告失败')
	} finally {
		reportLoading.value = false
	}
}

async function buyNow(item: { id: number; itemName: string }) {
  if (!user.value) {
    ElMessage.error('请先登录后再购买')
    router.push('/login')
    return
  }
  router.push({ name: 'shop-item', params: { id: String(item.id) } })
}

async function addToCart(item: { id: number; itemName: string }) {
  if (!user.value) {
    ElMessage.error('请先登录后再加入购物车')
    router.push('/login')
    return
  }
  try {
    const resp = await apiFetch('/shop/cart/add', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ itemId: item.id, quantity: 1 }),
    })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '加入购物车失败')
    }
    ElMessage.success(`已将「${item.itemName}」加入购物车`)
  } catch (e: any) {
    ElMessage.error(e?.message || '加入购物车失败')
  }
}

async function toggleFavorite(item: { id: number; itemName: string }) {
  if (!user.value) {
    ElMessage.error('请先登录后再收藏')
    router.push('/login')
    return
  }
  try {
    const resp = await apiFetch('/shop/favorites/toggle', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ itemId: item.id }),
    })
    const data = await resp.json().catch(() => ({}))
    if (!resp.ok) {
      throw new Error((data as any)?.error || '收藏操作失败')
    }
    const favorited = !!(data as any)?.favorited
    if (Array.isArray(result.recommendedItems)) {
      const target = result.recommendedItems.find((it) => it.id === item.id)
      if (target) {
        target.favorited = favorited
      }
    }
    ElMessage.success(favorited ? `已收藏「${item.itemName}」` : `已取消收藏「${item.itemName}」`)
  } catch (e: any) {
    ElMessage.error(e?.message || '收藏操作失败')
  }
}

// Camera capture
const showCamera = ref(false)
const videoRef = ref<HTMLVideoElement | null>(null)
let mediaStream: MediaStream | null = null

const devices = ref<MediaDeviceInfo[]>([])
const selectedDeviceId = ref<string | null>(null)

async function openCamera() {
  try {
    showCamera.value = true
    await startCamera(selectedDeviceId.value || undefined)
    await listVideoDevices()
  } catch (e: any) {
    showCamera.value = false
    ElMessage.error(e?.message || '无法打开摄像头')
  }
}

async function startCamera(deviceId?: string) {
  try {
    if (mediaStream) {
      mediaStream.getTracks().forEach(t => t.stop())
      mediaStream = null
    }
    const constraints: MediaStreamConstraints = deviceId
      ? { video: { deviceId: { exact: deviceId }, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false }
      : { video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false }
    mediaStream = await navigator.mediaDevices.getUserMedia(constraints)
    if (videoRef.value) {
      videoRef.value.srcObject = mediaStream
      await videoRef.value.play()
    }
  } catch (err) {
    if (!deviceId) {
      mediaStream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false })
      if (videoRef.value) {
        videoRef.value.srcObject = mediaStream
        await videoRef.value.play()
      }
    } else {
      throw err
    }
  }
}

async function listVideoDevices() {
  try {
    const all = await navigator.mediaDevices.enumerateDevices()
    const cams = all.filter(d => d.kind === 'videoinput')
    devices.value = cams
    if (!selectedDeviceId.value && cams.length > 0) {
      selectedDeviceId.value = cams[0].deviceId
    }
  } catch {}
}

async function onSelectDeviceChange() {
  if (!showCamera.value) return
  await startCamera(selectedDeviceId.value || undefined)
}

async function switchCamera() {
  if (devices.value.length <= 1) return
  const idx = devices.value.findIndex(d => d.deviceId === selectedDeviceId.value)
  const next = devices.value[(idx + 1) % devices.value.length]
  selectedDeviceId.value = next.deviceId
  await startCamera(selectedDeviceId.value)
}

async function captureAndDetect() {
  if (!videoRef.value) return
  const video = videoRef.value
  const canvas = document.createElement('canvas')
  canvas.width = video.videoWidth || 1280
  canvas.height = video.videoHeight || 720
  const ctx = canvas.getContext('2d')!
  await new Promise<void>(resolve => requestAnimationFrame(() => {
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height)
    resolve()
  }))
  canvas.toBlob(async (blob) => {
    if (!blob) { ElMessage.error('拍照失败'); return }
    const file = new File([blob], 'capture.jpg', { type: 'image/jpeg' })
    await detectFile(file)
  }, 'image/jpeg', 0.92)
}

function closeCamera() {
  showCamera.value = false
  if (mediaStream) {
    mediaStream.getTracks().forEach(t => t.stop())
    mediaStream = null
  }
}

// History
async function loadHistory() {
  if (!user.value) return
  try {
    const resp = await apiFetch(`/detect/history/${encodeURIComponent(user.value.username)}?limit=5`)
    if (resp.status === 404) {
      console.warn('[detect] history endpoint returned 404, skip history loading')
      history.value = []
      return
    }
    if (!resp.ok) throw new Error('获取历史失败')
    const list = await resp.json()
    history.value = Array.isArray(list)
      ? list.map((item: any) => ({
          predictedClass: item?.predictedClass,
          confidence: item?.confidence ?? 0,
          advice: item?.advice ?? '',
          createdAt: item?.createdAt,
          disease: item?.disease || null,
        }))
      : []
  } catch (e: any) {
    ElMessage.error(e.message || '加载历史失败')
  }
}

function goAllHistory() {
  router.push('/history')
}

onMounted(async () => {
  const raw = sessionStorage.getItem('user')
  user.value = raw ? JSON.parse(raw) : null
  if (!user.value) { router.push('/login'); return }
  await loadHistory()
  await loadExperts()

  const idParam = route.query.id as string | string[] | undefined
  const id = Array.isArray(idParam) ? idParam[0] : idParam
  if (id) {
    await loadResultById(id)
  }
})

onUnmounted(() => closeCamera())

let originalBodyPaddingRight = ''
let scrollLockY = 0
watchEffect(() => {
  const lock = showCamera.value
  try {
    if (lock) {
      scrollLockY = window.scrollY
      const sbw = window.innerWidth - document.documentElement.clientWidth
      originalBodyPaddingRight = document.body.style.paddingRight
      document.body.style.paddingRight = sbw > 0 ? `${sbw}px` : ''
      document.body.style.position = 'fixed'
      document.body.style.top = `-${scrollLockY}px`
      document.body.style.left = '0'
      document.body.style.right = '0'
      document.body.style.overflow = 'hidden'
      document.body.style.width = '100%'
      document.body.style.touchAction = 'none'
    } else {
      const top = document.body.style.top
      document.body.style.position = ''
      document.body.style.top = ''
      document.body.style.left = ''
      document.body.style.right = ''
      document.body.style.overflow = ''
      document.body.style.width = ''
      document.body.style.touchAction = ''
      document.body.style.paddingRight = originalBodyPaddingRight
      const y = top ? Math.abs(parseInt(top, 10)) : 0
      if (y) window.scrollTo(0, y)
    }
  } catch {}
})

// Localization helpers
const plantMap: Record<string, string> = {
  'Apple': '苹果', 'Blueberry': '蓝莓', 'Cherry': '樱桃', 'Corn': '玉米', 'Corn_(maize)': '玉米', 'Grape': '葡萄', 'Orange': '橙子', 'Peach': '桃', 'Pepper,_bell': '柿子椒', 'Pepper': '辣椒', 'Potato': '马铃薯', 'Raspberry': '树莓', 'Soybean': '大豆', 'Squash': '南瓜', 'Strawberry': '草莓', 'Tomato': '番茄', 'Background': '背景'
}
const diseaseMap: Record<string, string> = {
  'healthy': '健康',
  'Leaf_scorch': '叶片灼伤',
  'Leaf_blight': '叶枯病',
  'Leaf_blight_(Isariopsis_Leaf_Spot)': '叶枯病（伊萨里俄普斯叶斑）',
  'Leaf_spot': '叶斑病',
  'Early_blight': '早疫病',
  'Late_blight': '晚疫病',
  'Powdery_mildew': '白粉病',
  'Rust': '锈病',
  'Mosaic_virus': '花叶病毒',
  'Bacterial_spot': '细菌性斑点病',
  'Target_Spot': '靶斑病',
  'Septoria_leaf_spot': '尾孢叶斑病',
  'Spider_mites Two-spotted_spider_mite': '二斑叶螨',
  'Black_rot': '黑腐病',
  'Esca_(Black_Measles)': '黑麻疹（葡萄蔓割病）',
  'Leaf_mold': '叶霉病',
  'Cedar_apple_rust': '雪松苹果锈病',
  'Apple_scab': '苹果黑星病',
  'Brown_rot': '褐腐病',
  'Gray_leaf_spot': '灰斑病',
  'Common_rust_': '普通锈病',
  'Northern_Leaf_Blight': '北方叶枯病',
  'Haunglongbing_(Citrus_greening)': '黄龙病（柑橘绿化病）',
  'Phytophthora_infestans': '晚疫病（致病疫霉）',
  'Fusarium_wilt': '枯萎病',
  'Verticillium_wilt': '黄萎病',
  'Tomato_Yellow_Leaf_Curl_Virus': '番茄黄化曲叶病毒',
  'Tomato_mosaic_virus': '番茄花叶病毒',
  'Background_without_leaves': '背景无叶片'
}

function parseClassName(name?: string) {
  if (!name) return { plant: '-', disease: '-' }
  
  // 直接检查是否为背景无叶片的情况
  if (name.includes('Background_without_leaves') || 
      name === 'Background_without_leaves' ||
      name.includes('Suspected_background_no_leaf') ||
      name === 'Suspected_background_no_leaf') {
    return { plant: '背景', disease: '无' }
  }
  
  const parts = name.split('___')
  const plantEn = parts[0] ?? ''
  const diseaseEn = parts[1] ?? 'healthy'
  // 规范化：处理 PlantVillage 中的括号/空格/下划线差异
  const plantCandidates = [plantEn, plantEn.replace(/ /g, '_')]
  const plantZh = plantCandidates.map(k => plantMap[k]).find(Boolean) || plantEn
  const diseaseKeyUnderscore = diseaseEn.replace(/ /g, '_')
  const diseaseCandidates = [diseaseEn, diseaseKeyUnderscore]
  let diseaseZh = diseaseCandidates.map(k => diseaseMap[k]).find(Boolean) || diseaseEn.replace(/_/g, ' ')
  
  // 特例：玉米灰斑病（Cercospora/Gray leaf spot）
  if (/Cercospora\s*leaf\s*spot/i.test(diseaseEn) || /Gray\s*leaf\s*spot/i.test(diseaseEn)) {
    diseaseZh = '灰斑病'
  }
  
  // 背景无叶片的特殊处理
  if (plantEn === 'Background' && (diseaseEn === 'without_leaves' || diseaseEn.includes('without_leaves'))) {
    return { plant: '背景', disease: '无' }
  }
  
  return { plant: plantZh, disease: diseaseZh }
}

const localized = computed(() => {
  if (result.disease) {
    return { plant: result.disease.plant, disease: result.disease.name }
  }
  return parseClassName(result.predictedClass)
})

const feedbackStatusText = computed(() => {
  if (!result.hasFeedback) return '尚未提交纠错反馈'
  const s = result.latestFeedbackStatus
  if (!s) return '尚未提交纠错反馈'
  if (s === 'PENDING_EXPERT') return '已提交纠错反馈，等待专家处理'
  if (s === 'CONFIRMED_CORRECT') return '专家已审核：识别结果无误'
  if (s === 'CONFIRMED_WRONG') return '专家已确认识别错误，等待平台处理'
  if (s === 'DATASET_ADDED') return '样本已加入数据集，平台正在或已完成调优'
  return s
})

const feedbackStatusTagType = computed(() => {
  if (!result.hasFeedback) return 'info'
  const s = result.latestFeedbackStatus
  if (!s) return 'info'
  if (s === 'PENDING_EXPERT') return 'warning'
  if (s === 'CONFIRMED_CORRECT') return 'success'
  if (s === 'CONFIRMED_WRONG') return 'danger'
  if (s === 'DATASET_ADDED') return 'success'
  return 'info'
})

function displayForHistory(item: { predictedClass: string; disease?: DiseaseBrief | null }) {
  if (item?.disease) {
    return { plant: item.disease.plant, disease: item.disease.name }
  }
  return parseClassName(item?.predictedClass)
}

function onImgError(e: Event) {
  const img = e.target as HTMLImageElement
  if (result.previewUrl && img.src !== result.previewUrl) {
    img.src = result.previewUrl
  } else if (img.src !== '/images/placeholder.png') {
    // 使用默认占位图
    img.src = '/images/placeholder.png'
  }
}
</script>

<template>
  <div class="detect-page">
    <header class="page-header">
      <div class="page-title">植物叶片识别</div>
      <p class="page-sub">支持本地上传与实时拍照，两侧展示识别结果与近期历史记录。</p>
    </header>

    <section class="detect-layout">
      <div class="detect-left">
        <section class="inputs">
          <div class="upload card">
            <div class="card-title">上传图片识别</div>
            <input ref="fileInput" type="file" accept="image/*" capture="environment" @change="onFileChange" />
            <div class="hint">选择本地图片，系统将自动进行识别。</div>
          </div>
          <div class="camera card">
            <div class="card-title">摄像头拍照识别</div>
            <div v-if="!showCamera" class="camera-actions">
              <button class="btn" @click="openCamera">打开摄像头</button>
            </div>
            <div v-else class="camera-view">
              <video ref="videoRef" autoplay playsinline class="video"></video>
              <div class="camera-toolbar">
                <label v-if="devices.length > 0" class="devsel">
                  摄像头：
                  <select v-model="selectedDeviceId" @change="onSelectDeviceChange">
                    <option v-for="(d, i) in devices" :key="d.deviceId" :value="d.deviceId">{{ d.label || ('摄像头 ' + (i + 1)) }}</option>
                  </select>
                </label>
                <button v-if="devices.length > 1" class="btn outline" @click="switchCamera">切换摄像头</button>
                <span class="hint" style="margin-left:auto">若无法打开，请检查浏览器权限，或使用左侧拍照上传</span>
              </div>
              <div class="camera-actions">
                <button class="btn" @click="captureAndDetect">拍照识别</button>
                <button class="btn outline" @click="closeCamera">关闭摄像头</button>
              </div>
            </div>
          </div>
        </section>

        <section class="history card">
          <div class="card-title">最近识别历史</div>
          <div v-if="history.length === 0" class="empty">暂无识别记录</div>
          <ul v-else class="hist-list">
            <li v-for="(h, i) in history" :key="i" class="hist-item">
              <div class="left">
                <div class="cls">{{ displayForHistory(h).plant }} · {{ displayForHistory(h).disease }}</div>
                <div class="adv">{{ h.advice }}</div>
              </div>
              <div class="right">
                <div class="conf">{{ (h.confidence * 100).toFixed(1) }}%</div>
                <div class="time">{{ new Date(h.createdAt).toLocaleString() }}</div>
              </div>
            </li>
          </ul>
          <div class="actions">
            <button class="btn secondary" @click="goAllHistory">查看全部历史</button>
          </div>
        </section>
      </div>

      <div class="detect-right">
        <section class="result card" v-if="result.predictedClass">
          <div class="card-title">识别结果</div>
          <div class="img-wrap">
            <img 
              :src="result.imageUrl ? (IMG_BASE + result.imageUrl) : result.previewUrl || '/images/placeholder.png'" 
              alt="识别图片" 
              @error="onImgError" 
            />
          </div>
          <div class="rows">
            <div class="row"><span class="label">植物</span><span class="value">{{ localized.plant }}</span></div>
            <div class="row"><span class="label">病害</span><span class="value">{{ localized.disease }}</span></div>
            <div class="row"><span class="label">置信度</span><span class="value">{{ (result.confidence! * 100).toFixed(1) }}%</span></div>
            <div class="row advice"><span class="label">防治建议</span><span class="value">{{ result.advice }}</span></div>
          </div>
          <div class="time">识别时间：{{ result.createdAt ? new Date(result.createdAt).toLocaleString() : '-' }}</div>

          <div v-if="showFeedbackUi" class="feedback-block">
            <div class="feedback-header">
              <span class="label">纠错反馈</span>
              <el-tag
                v-if="result.hasFeedback"
                :type="feedbackStatusTagType"
                size="small"
              >
                {{ feedbackStatusText }}
              </el-tag>
            </div>

            <div v-if="!result.hasFeedback" class="feedback-info">
              <p>如果您认为本次识别有误，可以点击下方链接提交纠错反馈，由专家进行审核。</p>
              <div class="actions" style="margin-top: 4px;">
                <button class="feedback-link-btn" type="button" @click="showFeedbackDialog = true">
                  识别错了，我要反馈
                </button>
              </div>
            </div>

            <div v-else class="feedback-info">
              <p>您已提交过纠错反馈，可在“我的反馈”中查看处理进度。</p>
            </div>
          </div>

          <div v-if="result.recommendedItems && result.recommendedItems.length" class="recommend">
            <div class="recommend-title">推荐用药 / 物品</div>
            <div class="recommend-grid">
              <div
                v-for="item in result.recommendedItems"
                :key="item.id"
                class="product-card"
              >
                <div class="product-img-wrap">
                  <img
                    v-if="item.imageUrl"
                    :src="API_ORIGIN + item.imageUrl"
                    class="product-img"
                    alt="推荐物品图片"
                  />
                  <div v-else class="product-img placeholder">暂无图片</div>
                </div>
                <div class="product-body">
                  <div class="product-name" :title="item.itemName">{{ item.itemName }}</div>
                  <div class="product-tags">
                    <span v-if="item.plantName" class="tag">{{ item.plantName }}</span>
                    <span v-if="item.targetDisease" class="tag subtle">应对：{{ item.targetDisease }}</span>
                  </div>
                  <div class="product-price" v-if="item.price != null">
                    <span class="currency">¥</span>{{ item.price }}
                  </div>
                </div>
                <div class="product-actions">
                  <button type="button" class="btn primary sm" @click="buyNow(item)">立即购买</button>
                  <button type="button" class="btn secondary sm" @click="addToCart(item)">加入购物车</button>
                  <button type="button" class="btn ghost sm" @click="toggleFavorite(item)">{{ item.favorited ? '已收藏' : '收藏' }}</button>
                </div>
              </div>
            </div>
          </div>
          <div v-else class="recommend-empty">
            当前病害暂未配置推荐物品，可联系管理员在“防治物品管理”中补充。
          </div>

          <div class="top5" v-if="result.probabilities?.length">
            <div class="top5-title">Top-5 置信分布</div>
            <ul class="top5-list">
              <li v-for="(p, i) in result.probabilities" :key="i" class="top5-item">
                <div class="label">{{ parseClassName(p.class).plant }} - {{ parseClassName(p.class).disease }}</div>
                <div class="bar-wrap"><div class="bar" :style="{ width: Math.max(4, p.prob * 100) + '%'}"></div></div>
                <div class="val">{{ (p.prob * 100).toFixed(1) }}%</div>
              </li>
            </ul>
          </div>

          <div class="actions">
            <button
              v-if="!result.reportUrl"
              class="btn primary"
              type="button"
              :disabled="reportLoading"
              @click="generateReport"
            >
              {{ reportLoading ? '正在生成识别报告…' : '生成识别报告（PDF）' }}
            </button>
          </div>
        </section>
      </div>
    </section>

    <el-dialog
      v-if="showFeedbackUi"
      v-model="showFeedbackDialog"
      title="提交识别纠错反馈"
      width="520px"
    >
      <div class="feedback-dialog-body">
        <div class="field">
          <span class="field-label">指派专家</span>
          <el-select
            v-model="feedbackForm.expertUsername"
            placeholder="请选择要指派的专家"
            style="width: 260px"
            filterable
          >
            <el-option
              v-for="e in experts"
              :key="e.username"
              :label="e.nickname || e.username"
              :value="e.username"
            />
          </el-select>
        </div>
        <div class="field">
          <span class="field-label">说明</span>
          <el-input
            v-model="feedbackForm.comment"
            type="textarea"
            :rows="3"
            placeholder="简单说明哪里识别错了（可选）"
          />
        </div>
      </div>
      <template #footer>
        <div class="dialog-actions">
          <button class="btn" type="button" :disabled="submittingFeedback" @click="submitFeedback">
            {{ submittingFeedback ? '正在提交…' : '提交反馈' }}
          </button>
          <button class="btn outline" type="button" @click="showFeedbackDialog = false">取消</button>
        </div>
      </template>
    </el-dialog>

    <div v-if="loading" class="loading">正在识别，请稍候...</div>
  </div>
</template>

<style scoped>
.detect-page { min-height: 100%; padding: 16px 0 24px; overscroll-behavior: contain; background: linear-gradient(180deg, #f0fdf4 0%, #f8fafc 100%); }

.page-header {
  width: min(1100px, 100%);
  margin: 0 auto 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.page-title {
  font-size: 20px;
  font-weight: 700;
}
.page-sub {
  font-size: 13px;
  color: #6b7280;
}

.detect-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(0, 1.1fr);
  gap: 16px;
  width: min(1100px, 100%);
  margin: 0 auto;
  align-items: flex-start;
}

.detect-left { display: grid; gap: 16px; }
.detect-right { display: grid; gap: 16px; }

.inputs { display: grid; grid-template-columns: 1fr; gap: 16px; width: auto; margin: 0; }
.card { background: rgba(255,255,255,0.72); backdrop-filter: blur(12px); border: 1px solid rgba(255,255,255,0.8); border-radius: 12px; padding: 16px; box-shadow: 0 8px 20px rgba(0,0,0,0.08); }
.card-title { font-weight: 600; margin-bottom: 8px; }
.hint { font-size: 12px; color: #6b7280; margin-top: 8px; }

.camera-view { display: grid; gap: 8px; }
.video { width: 100%; height: 280px; background: #111827; border-radius: 10px; object-fit: cover; will-change: transform; transform: translateZ(0); backface-visibility: hidden; }
.camera.card { min-height: 380px; }
.camera-actions { display: flex; gap: 8px; }
.camera-toolbar { display: flex; align-items: center; gap: 8px; }
.camera-toolbar .devsel select { padding: 4px 8px; border: 1px solid #e5e7eb; border-radius: 6px; }

.result { width: 100%; margin: 0; }
.rows { display: grid; gap: 8px; }
.img-wrap { width: 100%; display: flex; justify-content: center; margin-bottom: 10px; }
.img-wrap img { max-width: 100%; max-height: 260px; border-radius: 10px; box-shadow: 0 8px 20px rgba(0,0,0,0.08); object-fit: cover; }
.row { display: grid; grid-template-columns: 120px 1fr; align-items: start; }
.row.advice .value { white-space: pre-wrap; }
.time { font-size: 12px; color: #6b7280; margin-top: 8px; }

.feedback-block { margin-top: 10px; padding-top: 8px; border-top: 1px dashed #e5e7eb; }
.feedback-header { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 6px; }
.feedback-form { display: grid; gap: 6px; margin-top: 4px; }
.feedback-form .field { display: grid; grid-template-columns: 80px 1fr; align-items: center; gap: 8px; }
.feedback-form .field-label { font-size: 12px; color: #6b7280; }
.feedback-info { font-size: 12px; color: #6b7280; margin-top: 4px; }

.history { width: 100%; margin: 0; }
.empty { color: #6b7280; }
.hist-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 10px; }
.hist-item { display: grid; grid-template-columns: 2fr 1fr; gap: 8px; padding: 10px; border: 1px dashed #e5e7eb; border-radius: 8px; }
.hist-item .cls { font-weight: 600; }
.hist-item .adv { font-size: 12px; color: #374151; margin-top: 4px; }
.hist-item .right { text-align: right; }
.actions { margin-top: 10px; display: flex; justify-content: flex-end; }

/* using global .btn styles */

.loading { position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%); background: rgba(0,0,0,0.75); color: #fff; padding: 8px 12px; border-radius: 999px; }

@media (max-width: 900px) {
  .detect-layout {
    grid-template-columns: 1fr;
  }
}

/* Top-5 list */
.top5 { margin-top: 12px; }
.top5-title { font-weight: 600; margin-bottom: 8px; }
.top5-list { list-style: none; padding: 0; margin: 0; display: grid; gap: 8px; }
.top5-item { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 8px; }
.top5-item .label { font-size: 13px; color: #0f172a; }
.bar-wrap { grid-column: 1 / span 1; height: 8px; background: rgba(15,23,42,0.08); border-radius: 999px; overflow: hidden; }
.bar { height: 100%; background: linear-gradient(90deg, #3b82f6, #22c55e); box-shadow: 0 0 12px rgba(59,130,246,0.35); }
.val { font-size: 12px; color: #111827; }

/* 推荐商品卡片 */
.recommend { margin-top: 14px; }
.recommend-title { font-weight: 600; margin-bottom: 8px; }
.recommend-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 10px;
}
.product-card {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  box-shadow: 0 6px 16px rgba(15,23,42,0.06);
  overflow: hidden;
}
.product-img-wrap {
  width: 100%;
  aspect-ratio: 4 / 3;
  background: #f9fafb;
  display: flex;
  align-items: center;
  justify-content: center;
}
.product-img-wrap .product-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.product-img-wrap .placeholder {
  font-size: 12px;
  color: #9ca3af;
}
.product-body {
  padding: 8px 10px 4px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.product-name {
  font-size: 13px;
  font-weight: 600;
  color: #111827;
  line-height: 1.4;
  max-height: 2.8em;
  overflow: hidden;
}
.product-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.product-tags .tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 999px;
  background: #ecfdf3;
  color: #15803d;
}
.product-tags .tag.subtle {
  background: #eff6ff;
  color: #1d4ed8;
}
.product-price {
  margin-top: 2px;
  font-size: 14px;
  font-weight: 700;
  color: #e11d48;
}
.product-price .currency { font-size: 12px; margin-right: 1px; }
.product-actions {
  display: flex;
  padding: 6px 8px 8px;
  gap: 6px;
  justify-content: space-between;
}
.product-actions .btn.sm {
  padding: 4px 8px;
  font-size: 12px;
  line-height: 1.2;
}
.product-actions .btn.ghost {
  background: transparent;
  border-color: transparent;
  color: #4b5563;
}
</style>