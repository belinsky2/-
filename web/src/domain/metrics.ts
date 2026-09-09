import type { Attitude, Bit, BitPerformance, BitStatus } from './domain'
import { laughScore } from './domain'
import type { Id } from './identity'
import { isDeleted } from './identity'

/** Сводка по выступлению. */
export interface GigStats {
  readonly bitCount: number
  readonly averageScore: number
  readonly laughsPerMinute: number
}

/** Сколько материала на каком этапе. Показывает, где именно затык. */
export type Funnel = Readonly<Partial<Record<BitStatus, number>>>

export function funnelCount(f: Funnel, s: BitStatus): number {
  return f[s] ?? 0
}

/**
 * Laugh score выступления. Упражнение 35 у Картер: считать смех по записи,
 * а не по ощущению — ощущение врёт в обе стороны.
 */
export function gigStats(
  performances: readonly BitPerformance[],
  actualDurationSec: number | null,
): GigStats {
  if (performances.length === 0) return { bitCount: 0, averageScore: 0, laughsPerMinute: 0 }
  const total = performances.reduce((s, p) => s + laughScore(p.result), 0)
  const laughs = performances.filter((p) => laughScore(p.result) >= laughScore('LAUGH')).length
  const minutes = actualDurationSec && actualDurationSec > 0 ? actualDurationSec / 60 : null
  return {
    bitCount: performances.length,
    averageScore: total / performances.length,
    laughsPerMinute: minutes === null ? 0 : laughs / minutes,
  }
}

/** Средний результат шутки по всем её выходам. */
export function averageScoreByBit(performances: readonly BitPerformance[]): Map<Id, number> {
  const sums = new Map<Id, { total: number; n: number }>()
  for (const p of performances) {
    const cur = sums.get(p.bitId) ?? { total: 0, n: 0 }
    sums.set(p.bitId, { total: cur.total + laughScore(p.result), n: cur.n + 1 })
  }
  return new Map([...sums].map(([id, { total, n }]) => [id, total / n]))
}

const alive = (bits: readonly Bit[]) => bits.filter((b) => !isDeleted(b.meta))

export function funnel(bits: readonly Bit[]): Funnel {
  const out: Partial<Record<BitStatus, number>> = {}
  for (const b of alive(bits)) out[b.status] = (out[b.status] ?? 0) + 1
  return out
}

/** Сколько минут готового материала набрано. */
export function polishedMinutes(bits: readonly Bit[]): number {
  return alive(bits)
    .filter((b) => b.status === 'POLISHED')
    .reduce((s, b) => s + (b.durationSec ?? 0), 0) / 60
}

/**
 * Доля шуток с act-out среди тех, что уже выносились на сцену.
 * Картер настаивает: играть, а не рассказывать, — и перекос стоит видеть.
 */
export function actOutRatio(bits: readonly Bit[]): number {
  const onStage = alive(bits).filter((b) => b.status === 'TESTED' || b.status === 'POLISHED')
  if (onStage.length === 0) return 0
  return onStage.filter((b) => b.elements.actOut !== null).length / onStage.length
}

/** Распределение по отношениям: показывает, что автор застрял на одном. */
export function attitudeSpread(bits: readonly Bit[]): Partial<Record<Attitude, number>> {
  const out: Partial<Record<Attitude, number>> = {}
  for (const b of alive(bits)) {
    if (b.attitude) out[b.attitude] = (out[b.attitude] ?? 0) + 1
  }
  return out
}

/**
 * Сводка прогресса. Считается из данных, а не из ощущений: в этом весь смысл
 * связки «выступление → отметки → пересборка акта».
 */
export interface Progress {
  readonly funnel: Funnel
  readonly polishedMinutes: number
  readonly goalMinutes: number
  readonly actOutRatio: number
  readonly attitudeSpread: Partial<Record<Attitude, number>>
  readonly gigsLast30Days: number
  readonly streakDays: number
}

export function goalRatio(p: Progress): number {
  if (p.goalMinutes <= 0) return 0
  return Math.min(1, p.polishedMinutes / p.goalMinutes)
}

/**
 * Где именно затык. Показывать все проценты воронки бессмысленно —
 * полезен один вывод: на каком шаге материал застревает.
 */
export function bottleneck(p: Progress): BitStatus | null {
  const stages: BitStatus[] = ['SEED', 'PREMISE', 'DRAFT', 'TESTED']
  let best: BitStatus | null = null
  for (const s of stages) {
    const n = funnelCount(p.funnel, s)
    if (n > 0 && (best === null || n > funnelCount(p.funnel, best))) best = s
  }
  return best
}

const MILLIS_PER_DAY = 24 * 60 * 60 * 1000

/**
 * Цепочка Сайнфелда: сколько дней подряд была хоть какая-то работа.
 * Сегодняшний пропуск цепочку ещё не рвёт — день не кончился.
 */
export function streakDays(activeDayMillis: readonly number[], nowMillis: number): number {
  if (activeDayMillis.length === 0) return 0
  const dayOf = (ms: number) => Math.floor(ms / MILLIS_PER_DAY)
  const days = new Set(activeDayMillis.map(dayOf))
  const today = dayOf(nowMillis)

  let cursor = days.has(today) ? today : today - 1
  if (!days.has(cursor)) return 0

  let n = 0
  while (days.has(cursor)) {
    n++
    cursor--
  }
  return n
}
