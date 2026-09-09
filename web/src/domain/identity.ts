/**
 * Часы как зависимость, а не как глобальное состояние: тесты жизненного цикла
 * шутки должны уметь «прожить» девяносто дней за миллисекунду.
 */
export type Clock = () => number

export const systemClock: Clock = () => Date.now()

/** Источник случайности — тоже зависимость, иначе идентификаторы не проверить. */
export type RandomBytes = (n: number) => Uint8Array

export const systemRandom: RandomBytes = (n) => {
  const b = new Uint8Array(n)
  crypto.getRandomValues(b)
  return b
}

const HEX = '0123456789abcdef'

function toUuidString(bytes: Uint8Array): string {
  let s = ''
  for (let i = 0; i < bytes.length; i++) {
    if (i === 4 || i === 6 || i === 8 || i === 10) s += '-'
    const v = bytes[i]!
    s += HEX[v >>> 4]! + HEX[v & 0x0f]!
  }
  return s
}

/**
 * Идентификатор записи. UUIDv7: первые 48 бит — метка времени, поэтому значения
 * упорядочены по возрастанию и не разрушают локальность индекса, в отличие от v4.
 * Уникальность обеспечивается устройством, а не базой, — иначе два устройства
 * создадут запись с одним номером и при слиянии одна затрёт другую.
 */
export type Id = string

export function generateId(clock: Clock, random: RandomBytes = systemRandom): Id {
  const ms = clock()
  const bytes = new Uint8Array(16)
  // 48 бит времени, big-endian. Через деление, а не сдвиги: в JS побитовые
  // операции работают с 32 битами и старшие разряды миллисекунд потерялись бы.
  let rest = Math.floor(ms)
  for (let i = 5; i >= 0; i--) {
    bytes[i] = rest % 256
    rest = Math.floor(rest / 256)
  }
  bytes.set(random(10), 6)
  // версия 7 в старших четырёх битах седьмого байта
  bytes[6] = (bytes[6]! & 0x0f) | 0x70
  // вариант RFC 4122 в старших двух битах девятого байта
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  return toUuidString(bytes)
}

/** Идентификатор устройства. Создаётся один раз при первом запуске и не меняется. */
export type DeviceId = string

/**
 * Метаданные, без которых слияние двух устройств невозможно.
 *
 * lamport — логические часы: настенное время на телефоне и на Mac разъезжается,
 * и при одинаковом updatedAt нужен детерминированный победитель.
 * deletedAt — надгробие: физическое удаление не переживает слияния, потому что
 * на втором устройстве запись просто выглядела бы новой.
 */
export interface SyncMeta {
  readonly updatedAt: number
  readonly lamport: number
  readonly deviceId: DeviceId
  readonly deletedAt: number | null
}

export function isDeleted(meta: SyncMeta): boolean {
  return meta.deletedAt !== null
}

/**
 * Кто побеждает при расхождении. Сравнение по логическим часам, затем по
 * настенному времени, затем по идентификатору устройства — последнее нужно лишь
 * чтобы результат не зависел от порядка обхода.
 */
export function wins(a: SyncMeta, b: SyncMeta): boolean {
  if (a.lamport !== b.lamport) return a.lamport > b.lamport
  if (a.updatedAt !== b.updatedAt) return a.updatedAt > b.updatedAt
  return a.deviceId > b.deviceId
}
