declare global {
  interface Window {
    AMap: any
    _AMapSecurityConfig?: { securityJsCode: string }
  }
}

const WEATHER_KEY = '8eab52d085719f6042d91ac3f9bae7c9'
const WEATHER_SECURITY = '8a814e5684333c593755d3115ad7909c'

const GEO_KEY = '66317f0689da080e4773dec11b3cdaff'
const GEO_SECURITY = 'cb247fd14327d62ae5dda83202a0446e'

let loadingPromise: Promise<any> | null = null

export type AMapKeyProfile = 'geo' | 'weather'

function getKeyPair(prefer: AMapKeyProfile) {
  if (prefer === 'weather') return { key: WEATHER_KEY, sec: WEATHER_SECURITY }
  return { key: GEO_KEY, sec: GEO_SECURITY }
}

export function loadAMap(
  prefer: AMapKeyProfile = 'geo',
  plugins: string[] = []
): Promise<any> {
  if (typeof window !== 'undefined' && (window as any).AMap) {
    return Promise.resolve((window as any).AMap)
  }
  if (loadingPromise) return loadingPromise

  const { key, sec } = getKeyPair(prefer)
  ;(window as any)._AMapSecurityConfig = { securityJsCode: sec }

  const query: string[] = [`v=2.0`, `key=${encodeURIComponent(key)}`]
  if (plugins.length) {
    query.push(`plugin=${plugins.map(encodeURIComponent).join(',')}`)
  }
  const src = `https://webapi.amap.com/maps?${query.join('&')}`

  loadingPromise = new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.src = src
    script.async = true
    script.onload = () => resolve((window as any).AMap)
    script.onerror = () => reject(new Error('AMap JSAPI load failed'))
    document.head.appendChild(script)
  })

  return loadingPromise
}
