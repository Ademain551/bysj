import { reactive } from 'vue'

export interface LocationState {
  lng: number | null
  lat: number | null
  city: string
  adcode: string
  address: string
  updatedAt: number
}

export const currentLocation = reactive<LocationState>({
  lng: null,
  lat: null,
  city: '',
  adcode: '',
  address: '',
  updatedAt: 0,
})

export function setLocation(partial: Partial<LocationState>) {
  if (typeof partial.lng === 'number') currentLocation.lng = partial.lng
  if (typeof partial.lat === 'number') currentLocation.lat = partial.lat
  if (typeof partial.city === 'string') currentLocation.city = partial.city
  if (typeof partial.adcode === 'string') currentLocation.adcode = partial.adcode
  if (typeof partial.address === 'string') currentLocation.address = partial.address
  currentLocation.updatedAt = Date.now()
}
