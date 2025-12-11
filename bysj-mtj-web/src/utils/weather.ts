import { reactive } from 'vue'

export interface WeatherState {
  city: string
  text: string
  temperature: string
  windDirection: string
  windPower: string
  humidity: string
  reportTime: string
  updatedAt: number
}

export const currentWeather = reactive<WeatherState>({
  city: '',
  text: '',
  temperature: '',
  windDirection: '',
  windPower: '',
  humidity: '',
  reportTime: '',
  updatedAt: 0,
})

export function setWeather(partial: Partial<WeatherState>) {
  if (typeof partial.city === 'string') currentWeather.city = partial.city
  if (typeof partial.text === 'string') currentWeather.text = partial.text
  if (typeof partial.temperature === 'string') currentWeather.temperature = partial.temperature
  if (typeof partial.windDirection === 'string') currentWeather.windDirection = partial.windDirection
  if (typeof partial.windPower === 'string') currentWeather.windPower = partial.windPower
  if (typeof partial.humidity === 'string') currentWeather.humidity = partial.humidity
  if (typeof partial.reportTime === 'string') currentWeather.reportTime = partial.reportTime
  currentWeather.updatedAt = Date.now()
}
