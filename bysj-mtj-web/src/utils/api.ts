const envBase = (import.meta.env.VITE_API_BASE as string | undefined)?.trim()
const fallbackPort = (import.meta.env.VITE_API_PORT as string | undefined)?.trim() || '8099'
const shouldUseProxy = (() => {
  const raw = import.meta.env.VITE_USE_DEV_PROXY
  if (raw !== undefined && raw !== null && String(raw).trim() !== '') {
    return String(raw).toLowerCase() === 'true'
  }
  return import.meta.env.DEV
})()

const fallbackOrigin = (() => {
  if (typeof window !== 'undefined') {
    const { protocol, hostname } = window.location
    const portPart = fallbackPort ? `:${fallbackPort}` : ''
    return `${protocol}//${hostname}${portPart}`
  }
  const portPart = fallbackPort ? `:${fallbackPort}` : ''
  return `http://localhost${portPart}`
})()

let resolvedBase: string
if (envBase && envBase.length > 0) {
  resolvedBase = envBase.replace(/\/$/, '')
} else if (import.meta.env.DEV && shouldUseProxy) {
  resolvedBase = '/api'
} else {
  resolvedBase = `${fallbackOrigin}/api`
}

export const API_BASE = resolvedBase

let resolvedOrigin: string
if (/^https?:\/\//.test(API_BASE)) {
  resolvedOrigin = API_BASE.replace(/\/api$/, '')
} else if (typeof window !== 'undefined') {
  resolvedOrigin = fallbackOrigin
} else {
  resolvedOrigin = ''
}

export const API_ORIGIN = resolvedOrigin

export function apiUrl(path: string): string {
  const normalized = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE}${normalized}`
}

export function apiFetch(path: string, init?: RequestInit & { params?: URLSearchParams }) {
  let url = apiUrl(path)
  let fetchInit: RequestInit | undefined = init

  if (init?.params) {
    const queryString = init.params.toString()
    if (queryString) {
      url += '?' + queryString
    }
    // 删除params以避免传递给fetch
    const { params, ...rest } = init
    fetchInit = rest
  }

  return fetch(url, fetchInit).then((res) => {
    if (res.status === 401 && typeof window !== 'undefined') {
      const path = window.location.pathname || ''
      try {
        sessionStorage.removeItem('user')
      } catch {}
      if (!path.startsWith('/login') && !path.startsWith('/register')) {
        window.location.href = '/login'
      }
    }
    return res
  })
}

export function assetUrl(url?: string | null): string {
  if (!url) return ''
  if (/^https?:\/\//i.test(url)) return url
  if (url.startsWith('/uploads')) {
    return `${API_ORIGIN}${url}`
  }
  return url
}
