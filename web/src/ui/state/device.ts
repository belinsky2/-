import type { DeviceId } from '../../domain/identity'

/**
 * То, что живёт в localStorage, а не в базе: принадлежит устройству,
 * а не материалу, и в архив не уезжает.
 */
const DEVICE_KEY = 'punchline.deviceId'
const LAMPORT_KEY = 'punchline.lamport'

/** Идентификатор устройства. Создаётся при первом запуске и не меняется. */
export function deviceId(): DeviceId {
  let v = localStorage.getItem(DEVICE_KEY)
  if (!v) {
    v = crypto.randomUUID()
    localStorage.setItem(DEVICE_KEY, v)
  }
  return v
}

/** Сохранённые логические часы. Только подсказка: база сверяется при открытии. */
export function savedLamport(): number {
  return Number(localStorage.getItem(LAMPORT_KEY)) || 0
}

export function saveLamport(value: number): void {
  localStorage.setItem(LAMPORT_KEY, String(value))
}
