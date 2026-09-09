import type { Clock, RandomBytes, SyncMeta } from './identity'
import { generateId } from './identity'
import type { ActOut, Attitude, Bit, BitPerformance, BitStatus, LaughResult, Punch } from './domain'
import { EMPTY_ELEMENTS } from './domain'

/** Управляемые часы: тест обязан уметь прожить девяносто дней мгновенно. */
export class TestClock {
  constructor(private now = 1_700_000_000_000) {}
  readonly clock: Clock = () => this.now
  advanceDays(days: number) { this.now += days * 24 * 60 * 60 * 1000 }
  advanceMillis(ms: number) { this.now += ms }
}

/** Предсказуемый источник байтов вместо crypto: тест не должен зависеть от удачи. */
export function seededRandom(seed: number): RandomBytes {
  let s = seed >>> 0
  return (n) => {
    const out = new Uint8Array(n)
    for (let i = 0; i < n; i++) {
      s = (s * 1664525 + 1013904223) >>> 0
      out[i] = (s >>> 24) & 0xff
    }
    return out
  }
}

export const TEST_DEVICE = 'test-device'

export function meta(clock: Clock, lamport = 1, deletedAt: number | null = null): SyncMeta {
  return { updatedAt: clock(), lamport, deviceId: TEST_DEVICE, deletedAt }
}

export function bit(
  clock: Clock,
  o: {
    id?: string
    topicId?: string | null
    status?: BitStatus
    attitude?: Attitude | null
    premise?: string | null
    punch?: Punch | null
    actOut?: ActOut | null
    durationSec?: number | null
  } = {},
): Bit {
  const id = o.id ?? 'bit-1'
  return {
    id,
    topicId: o.topicId === undefined ? 'topic-1' : o.topicId,
    title: id,
    status: o.status ?? 'SEED',
    attitude: o.attitude ?? null,
    elements: {
      ...EMPTY_ELEMENTS,
      premise: o.premise ?? null,
      punch: o.punch ?? null,
      actOut: o.actOut ?? null,
    },
    durationSec: o.durationSec ?? null,
    meta: meta(clock),
  }
}

export function performance(
  clock: Clock,
  bitId: string,
  result: LaughResult,
  gigId = 'gig-1',
  lamport = 1,
): BitPerformance {
  return {
    id: generateId(clock, seededRandom(bitId.length + lamport)),
    gigId,
    bitId,
    result,
    note: null,
    meta: meta(clock, lamport),
  }
}
