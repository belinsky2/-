import { describe, expect, it } from 'vitest'
import type { Bit, BitStatus } from '../domain/domain'
import { TestClock, bit } from '../domain/fixtures'

/**
 * Отбор кандидатов в сет. Правило живёт в экране, поэтому проверяется здесь
 * же по той же формуле: кандидат — шутка, у которой есть добивка.
 */
const RANK: Record<string, number> = { POLISHED: 3, TESTED: 2, DRAFT: 1 }

function candidates(bits: Bit[], used: Set<string>, scores: Map<string, number>): Bit[] {
  return bits
    .filter((b) => !used.has(b.id) && RANK[b.status] !== undefined)
    .sort((a, b) =>
      (RANK[b.status]! - RANK[a.status]!) || ((scores.get(b.id) ?? 0) - (scores.get(a.id) ?? 0)),
    )
}

const t = new TestClock()
const make = (id: string, status: BitStatus): Bit => bit(t.clock, { id, status })

describe('кандидаты в сет', () => {
  it('черновик попадает в кандидаты: иначе первый сет собрать нечем', () => {
    // Замкнутый круг: обкатанной шутка становится после выступления,
    // а выступление собирают по сету.
    const out = candidates([make('b1', 'DRAFT')], new Set(), new Map())
    expect(out.map((b) => b.id)).toEqual(['b1'])
  })

  it('зерно и премиса не попадают: рассказывать там нечего', () => {
    const out = candidates([make('b1', 'SEED'), make('b2', 'PREMISE')], new Set(), new Map())
    expect(out).toHaveLength(0)
  })

  it('отложенное и списанное не предлагается', () => {
    const out = candidates([make('b1', 'PARKED'), make('b2', 'RETIRED')], new Set(), new Map())
    expect(out).toHaveLength(0)
  })

  it('проверенное залом стоит выше непроверенного', () => {
    const out = candidates(
      [make('draft', 'DRAFT'), make('polished', 'POLISHED'), make('tested', 'TESTED')],
      new Set(),
      new Map(),
    )
    expect(out.map((b) => b.id)).toEqual(['polished', 'tested', 'draft'])
  })

  it('внутри одного статуса сверху то, что лучше заходило', () => {
    const out = candidates(
      [make('слабая', 'TESTED'), make('сильная', 'TESTED')],
      new Set(),
      new Map([['слабая', 1.0], ['сильная', 3.5]]),
    )
    expect(out.map((b) => b.id)).toEqual(['сильная', 'слабая'])
  })

  it('уже стоящее в сете второй раз не предлагается', () => {
    const out = candidates([make('b1', 'DRAFT')], new Set(['b1']), new Map())
    expect(out).toHaveLength(0)
  })
})
