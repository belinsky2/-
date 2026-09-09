import { describe, expect, it } from 'vitest'
import type { Bit } from './domain'
import {
  actOutRatio, attitudeSpread, averageScoreByBit, bottleneck, funnel, funnelCount,
  gigStats, goalRatio, polishedMinutes, streakDays, type Progress,
} from './metrics'
import { TestClock, bit, performance as perf } from './fixtures'

const t = new TestClock()
const DAY = 24 * 60 * 60 * 1000
const NOW = 1_700_000_000_000

const withStatus = (id: string, status: Bit['status'], durationSec: number | null = null): Bit => ({
  ...bit(t.clock, { id, status }),
  durationSec,
})

describe('метрики выступления', () => {
  it('пустое выступление даёт нули, а не деление на ноль', () => {
    expect(gigStats([], 600)).toEqual({ bitCount: 0, averageScore: 0, laughsPerMinute: 0 })
  })

  it('средний балл и смех в минуту считаются по записи', () => {
    const p = [
      perf(t.clock, 'b1', 'BIG_LAUGH'),  // 3
      perf(t.clock, 'b2', 'LAUGH'),      // 2
      perf(t.clock, 'b3', 'SILENCE'),    // 0
    ]
    const s = gigStats(p, 300)
    expect(s.bitCount).toBe(3)
    expect(s.averageScore).toBeCloseTo(5 / 3)
    // Засчитываются только те, что дотянули до смеха: 2 из 5 минут.
    expect(s.laughsPerMinute).toBeCloseTo(2 / 5)
  })

  it('без известной длительности смех в минуту не выдумывается', () => {
    expect(gigStats([perf(t.clock, 'b1', 'LAUGH')], null).laughsPerMinute).toBe(0)
    expect(gigStats([perf(t.clock, 'b1', 'LAUGH')], 0).laughsPerMinute).toBe(0)
  })

  it('средний результат шутки считается по всем её выходам', () => {
    const m = averageScoreByBit([
      perf(t.clock, 'b1', 'SILENCE', 'g1'),
      perf(t.clock, 'b1', 'BIG_LAUGH', 'g2'),
      perf(t.clock, 'b2', 'LAUGH', 'g1'),
    ])
    expect(m.get('b1')).toBeCloseTo(1.5)
    expect(m.get('b2')).toBeCloseTo(2)
  })
})

describe('состояние материала', () => {
  it('воронка не считает удалённое', () => {
    const live = withStatus('b1', 'DRAFT')
    const dead = { ...withStatus('b2', 'DRAFT'), meta: { ...live.meta, deletedAt: 1 } }
    expect(funnelCount(funnel([live, dead]), 'DRAFT')).toBe(1)
  })

  it('минуты готового материала считают только отшлифованное', () => {
    const bits = [withStatus('b1', 'POLISHED', 120), withStatus('b2', 'DRAFT', 600)]
    expect(polishedMinutes(bits)).toBeCloseTo(2)
  })

  it('доля act-out считается по вынесенному на сцену, а не по всему подряд', () => {
    const played = {
      ...withStatus('b1', 'TESTED'),
      elements: { ...withStatus('b1', 'TESTED').elements, actOut: { text: 'играю', hasSpaceWork: false, audioHash: null } },
    }
    const told = withStatus('b2', 'POLISHED')
    const draft = withStatus('b3', 'DRAFT')
    expect(actOutRatio([played, told, draft])).toBeCloseTo(0.5)
    expect(actOutRatio([draft])).toBe(0)
  })

  it('перекос по отношениям виден', () => {
    const hard = { ...withStatus('b1', 'DRAFT'), attitude: 'HARD' as const }
    const weird = { ...withStatus('b2', 'DRAFT'), attitude: 'WEIRD' as const }
    const spread = attitudeSpread([hard, { ...hard, id: 'b3' }, weird])
    expect(spread.HARD).toBe(2)
    expect(spread.WEIRD).toBe(1)
  })
})

describe('прогресс', () => {
  const base: Progress = {
    funnel: {}, polishedMinutes: 0, goalMinutes: 60, actOutRatio: 0,
    attitudeSpread: {}, gigsLast30Days: 0, streakDays: 0,
  }

  it('доля к цели не превышает единицу', () => {
    expect(goalRatio({ ...base, polishedMinutes: 90 })).toBe(1)
    expect(goalRatio({ ...base, polishedMinutes: 30 })).toBeCloseTo(0.5)
  })

  it('без заданной цели доля не считается', () => {
    expect(goalRatio({ ...base, goalMinutes: 0, polishedMinutes: 10 })).toBe(0)
  })

  it('затык — самая населённая незавершённая ступень', () => {
    expect(bottleneck({ ...base, funnel: { SEED: 2, DRAFT: 7, POLISHED: 99 } })).toBe('DRAFT')
    expect(bottleneck({ ...base, funnel: { POLISHED: 5 } })).toBeNull()
    expect(bottleneck(base)).toBeNull()
  })
})

describe('цепочка Сайнфелда', () => {
  it('без работы цепочки нет', () => {
    expect(streakDays([], NOW)).toBe(0)
  })

  it('считает дни подряд, включая сегодня', () => {
    expect(streakDays([NOW, NOW - DAY, NOW - 2 * DAY], NOW)).toBe(3)
  })

  it('несколько записей за день считаются одним днём', () => {
    expect(streakDays([NOW, NOW - 1000, NOW - 2000], NOW)).toBe(1)
  })

  it('сегодняшний пропуск ещё не рвёт цепочку — день не кончился', () => {
    expect(streakDays([NOW - DAY, NOW - 2 * DAY], NOW)).toBe(2)
  })

  it('пропущенный вчерашний день цепочку рвёт', () => {
    expect(streakDays([NOW - 2 * DAY, NOW - 3 * DAY], NOW)).toBe(0)
  })

  it('дыра в середине обрывает счёт на ней', () => {
    expect(streakDays([NOW, NOW - DAY, NOW - 3 * DAY], NOW)).toBe(2)
  })
})
