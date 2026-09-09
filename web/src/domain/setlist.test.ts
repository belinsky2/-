import { describe, expect, it } from 'vitest'
import type { Bit, SetList, SetListItem } from './domain'
import type { Id } from './identity'
import { validateSetList } from './setlist'
import { TestClock, bit, meta } from './fixtures'

const t = new TestClock()

function item(o: Partial<SetListItem> & { id: string; bitId: string }): SetListItem {
  return { order: 0, role: 'BODY', plannedDurationSec: 60, ...o }
}

function setList(items: SetListItem[], targetDurationSec = 300): SetList {
  return { id: 'set-1', title: 'Сет', targetDurationSec, items, meta: meta(t.clock) }
}

function bitsMap(bits: Bit[]): Map<Id, Bit> {
  return new Map(bits.map((b) => [b.id, b]))
}

describe('правила сет-листа', () => {
  it('каллбэк раньше своей шутки — замечание', () => {
    const source = bit(t.clock, { id: 'b1' })
    const callback = { ...bit(t.clock, { id: 'b2' }), elements: { ...bit(t.clock).elements, callbackTo: 'b1' } }
    const s = setList([
      item({ id: 'i1', bitId: 'b2', order: 0 }),
      item({ id: 'i2', bitId: 'b1', order: 1 }),
    ])
    const issues = validateSetList(s, bitsMap([source, callback]))
    expect(issues.some((i) => i.kind === 'CallbackBeforeSource')).toBe(true)
  })

  it('каллбэк после своей шутки замечаний не вызывает', () => {
    const source = bit(t.clock, { id: 'b1' })
    const callback = { ...bit(t.clock, { id: 'b2' }), elements: { ...bit(t.clock).elements, callbackTo: 'b1' } }
    const s = setList([
      item({ id: 'i1', bitId: 'b1', order: 0 }),
      item({ id: 'i2', bitId: 'b2', order: 1 }),
    ])
    const issues = validateSetList(s, bitsMap([source, callback]))
    expect(issues.some((i) => i.kind === 'CallbackBeforeSource')).toBe(false)
  })

  it('две подряд на одну тему — подсказка разбавить', () => {
    const a = bit(t.clock, { id: 'b1', topicId: 'topic-1' })
    const b = bit(t.clock, { id: 'b2', topicId: 'topic-1' })
    const s = setList([
      item({ id: 'i1', bitId: 'b1', order: 0 }),
      item({ id: 'i2', bitId: 'b2', order: 1 }),
    ])
    expect(validateSetList(s, bitsMap([a, b])).some((i) => i.kind === 'SameTopicInARow')).toBe(true)
  })

  it('шутки без темы за однообразие не считаются', () => {
    const a = bit(t.clock, { id: 'b1', topicId: null })
    const b = bit(t.clock, { id: 'b2', topicId: null })
    const s = setList([
      item({ id: 'i1', bitId: 'b1', order: 0 }),
      item({ id: 'i2', bitId: 'b2', order: 1 }),
    ])
    expect(validateSetList(s, bitsMap([a, b])).some((i) => i.kind === 'SameTopicInARow')).toBe(false)
  })

  it('слабый закрывающий виден, когда в сете есть шутка сильнее', () => {
    const a = bit(t.clock, { id: 'b1', topicId: 'x' })
    const b = bit(t.clock, { id: 'b2', topicId: 'y' })
    const s = setList([
      item({ id: 'i1', bitId: 'b1', order: 0 }),
      item({ id: 'i2', bitId: 'b2', order: 1, role: 'CLOSER' }),
    ])
    const scores = new Map<Id, number>([['b1', 3.5], ['b2', 1.0]])
    expect(validateSetList(s, bitsMap([a, b]), scores).some((i) => i.kind === 'WeakCloser')).toBe(true)
  })

  it('без истории зала о закрывающем судить нечем', () => {
    const a = bit(t.clock, { id: 'b1', topicId: 'x' })
    const b = bit(t.clock, { id: 'b2', topicId: 'y' })
    const s = setList([
      item({ id: 'i1', bitId: 'b1', order: 0 }),
      item({ id: 'i2', bitId: 'b2', order: 1, role: 'CLOSER' }),
    ])
    expect(validateSetList(s, bitsMap([a, b])).some((i) => i.kind === 'WeakCloser')).toBe(false)
  })

  it('перебор и недобор по времени видны, а попадание — нет', () => {
    const a = bit(t.clock, { id: 'b1', topicId: 'x' })
    const kinds = (sec: number) =>
      validateSetList(
        setList([item({ id: 'i1', bitId: 'b1', plannedDurationSec: sec })], 300),
        bitsMap([a]),
      ).map((i) => i.kind)

    expect(kinds(400)).toContain('OverTime')
    expect(kinds(100)).toContain('UnderTime')
    // Ровно в цель и в пределах допуска — молчим.
    expect(kinds(300)).not.toContain('OverTime')
    expect(kinds(300)).not.toContain('UnderTime')
    expect(kinds(320)).not.toContain('OverTime')
  })

  it('пустой сет не ругается на время', () => {
    expect(validateSetList(setList([]), new Map())).toHaveLength(0)
  })

  it('длительность берётся у шутки, если у позиции своей нет', () => {
    const a = { ...bit(t.clock, { id: 'b1' }), durationSec: 400 }
    const s = setList([item({ id: 'i1', bitId: 'b1', plannedDurationSec: null })], 300)
    expect(validateSetList(s, bitsMap([a])).some((i) => i.kind === 'OverTime')).toBe(true)
  })
})
