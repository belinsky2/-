import { describe, expect, it } from 'vitest'
import { BIT_STATUSES, type Punch } from './domain'
import { canTransition, deservedStatus, hints } from './lifecycle'
import { TestClock, bit, performance } from './fixtures'

const punch: Punch = { text: 'панч', technique: 'TURN' }

describe('жизненный цикл шутки', () => {
  it('переходы вперёд идут в порядке тетради', () => {
    expect(canTransition('SEED', 'PREMISE')).toBe(true)
    expect(canTransition('PREMISE', 'DRAFT')).toBe(true)
    expect(canTransition('DRAFT', 'TESTED')).toBe(true)
    expect(canTransition('TESTED', 'POLISHED')).toBe(true)
  })

  it('ступени нельзя перепрыгнуть', () => {
    expect(canTransition('SEED', 'DRAFT')).toBe(false)
    expect(canTransition('PREMISE', 'POLISHED')).toBe(false)
  })

  it('отложить и вернуть можно всегда', () => {
    for (const s of BIT_STATUSES.filter((s) => s !== 'PARKED')) {
      expect(canTransition(s, 'PARKED'), `нельзя отложить из ${s}`).toBe(true)
    }
    expect(canTransition('PARKED', 'DRAFT')).toBe(true)
  })

  it('голая идея остаётся зерном', () => {
    const t = new TestClock()
    expect(deservedStatus(bit(t.clock, { status: 'SEED' }), [])).toBe('SEED')
  })

  it('премисе нужны и текст, и отношение', () => {
    const t = new TestClock()
    const withoutAttitude = bit(t.clock, { premise: 'самое сложное...' })
    expect(deservedStatus(withoutAttitude, [])).toBe('SEED')

    const complete = bit(t.clock, { premise: 'самое сложное...', attitude: 'HARD' })
    expect(deservedStatus(complete, [])).toBe('PREMISE')
  })

  it('премиса плюс панч дают черновик', () => {
    const t = new TestClock()
    expect(deservedStatus(bit(t.clock, { premise: 'п', attitude: 'WEIRD', punch }), [])).toBe('DRAFT')
  })

  it('одно выступление делает черновик обкатанным', () => {
    const t = new TestClock()
    const b = bit(t.clock, { premise: 'п', attitude: 'HARD', punch })
    expect(deservedStatus(b, [performance(t.clock, 'bit-1', 'CHUCKLE')])).toBe('TESTED')
  })

  it('шлифовка требует двух настоящих смехов, а не двух выходов', () => {
    const t = new TestClock()
    const b = bit(t.clock, { premise: 'п', attitude: 'HARD', punch })

    const weak = [
      performance(t.clock, 'bit-1', 'CHUCKLE', 'gig-1', 1),
      performance(t.clock, 'bit-1', 'SILENCE', 'gig-1', 2),
    ]
    expect(deservedStatus(b, weak)).toBe('TESTED')

    const strong = [
      performance(t.clock, 'bit-1', 'LAUGH', 'gig-1', 1),
      performance(t.clock, 'bit-1', 'BIG_LAUGH', 'gig-1', 2),
    ]
    expect(deservedStatus(b, strong)).toBe('POLISHED')
  })

  it('движок сам не вытаскивает шутку из архива', () => {
    const t = new TestClock()
    const parked = bit(t.clock, { status: 'PARKED', premise: 'п', attitude: 'HARD', punch })
    const strong = [
      performance(t.clock, 'bit-1', 'BIG_LAUGH', 'gig-1', 1),
      performance(t.clock, 'bit-1', 'BIG_LAUGH', 'gig-1', 2),
    ]
    expect(deservedStatus(parked, strong)).toBe('PARKED')
  })

  it('три тишины подряд предлагают переписать', () => {
    const t = new TestClock()
    const b = bit(t.clock, { premise: 'п', attitude: 'HARD', punch })
    const perf = [1, 2, 3].map((n) => performance(t.clock, 'bit-1', 'SILENCE', 'gig-1', n))
    expect(hints(b, perf, t.clock()).some((h) => h.kind === 'RewriteOrPark')).toBe(true)
  })

  it('старый смех не отменяет свежую серию провалов', () => {
    const t = new TestClock()
    const b = bit(t.clock, { premise: 'п', attitude: 'HARD', punch })
    const perf = [
      performance(t.clock, 'bit-1', 'BIG_LAUGH', 'gig-1', 1),
      performance(t.clock, 'bit-1', 'SILENCE', 'gig-1', 2),
      performance(t.clock, 'bit-1', 'SILENCE', 'gig-1', 3),
      performance(t.clock, 'bit-1', 'SILENCE', 'gig-1', 4),
    ]
    expect(hints(b, perf, t.clock()).some((h) => h.kind === 'RewriteOrPark')).toBe(true)
  })

  it('обкатанная шутка без act-out получает пометку', () => {
    const t = new TestClock()
    const b = bit(t.clock, { status: 'TESTED', premise: 'п', attitude: 'HARD', punch })
    expect(hints(b, [], t.clock()).some((h) => h.kind === 'MissingActOut')).toBe(true)

    const played = { ...b, elements: { ...b.elements, actOut: { text: 'играю тёщу', hasSpaceWork: false, audioHash: null } } }
    expect(hints(played, [], t.clock()).some((h) => h.kind === 'MissingActOut')).toBe(false)
  })

  it('черновик, пылящийся месяц, всплывает', () => {
    const t = new TestClock()
    const b = bit(t.clock, { status: 'DRAFT', premise: 'п', attitude: 'HARD', punch })
    expect(hints(b, [], t.clock()).some((h) => h.kind === 'StuckInDraft')).toBe(false)

    t.advanceDays(31)
    expect(hints(b, [], t.clock()).some((h) => h.kind === 'StuckInDraft')).toBe(true)
  })

  it('отшлифованный материал без дела квартал предлагается обратно', () => {
    const t = new TestClock()
    const b = bit(t.clock, { status: 'POLISHED', premise: 'п', attitude: 'HARD', punch })
    t.advanceDays(91)
    expect(hints(b, [], t.clock()).some((h) => h.kind === 'UnusedPolished')).toBe(true)
  })
})
