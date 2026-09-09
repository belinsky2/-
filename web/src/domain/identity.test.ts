import { describe, expect, it } from 'vitest'
import { generateId, isDeleted, wins } from './identity'
import { TestClock, TEST_DEVICE, seededRandom } from './fixtures'

describe('identity', () => {
  it('идентификатор имеет форму UUID версии 7', () => {
    const id = generateId(new TestClock().clock, seededRandom(1))
    expect(id).toHaveLength(36)
    expect([id[8], id[13], id[18], id[23]]).toEqual(['-', '-', '-', '-'])
    expect(id[14]).toBe('7')
    expect('89ab').toContain(id[19])
  })

  it('созданные позже сортируются после созданных раньше', () => {
    const t = new TestClock()
    const first = generateId(t.clock, seededRandom(1))
    t.advanceMillis(5)
    const second = generateId(t.clock, seededRandom(2))
    expect(first < second).toBe(true)
  })

  it('время старше 2^32 мс не теряет старшие разряды', () => {
    // Наивная реализация на побитовых сдвигах обрезала бы метку до 32 бит,
    // и все идентификаторы одного дня стали бы неупорядоченными.
    const early = generateId(() => 1_700_000_000_000, seededRandom(1))
    const late = generateId(() => 1_900_000_000_000, seededRandom(1))
    expect(early < late).toBe(true)
  })

  it('два устройства в один и тот же миг не сталкиваются', () => {
    const t = new TestClock()
    expect(generateId(t.clock, seededRandom(1))).not.toBe(generateId(t.clock, seededRandom(2)))
  })

  it('логические часы решают спор раньше настенного времени', () => {
    const loser = { updatedAt: 5_000, lamport: 1, deviceId: 'phone', deletedAt: null }
    // Часы на втором устройстве отстают, но логически изменение более позднее.
    const winner = { updatedAt: 1_000, lamport: 2, deviceId: 'mac', deletedAt: null }
    expect(wins(winner, loser)).toBe(true)
    expect(wins(loser, winner)).toBe(false)
  })

  it('при полностью одинаковых часах побеждает устройство детерминированно', () => {
    const a = { updatedAt: 1_000, lamport: 1, deviceId: 'aaa', deletedAt: null }
    const b = { updatedAt: 1_000, lamport: 1, deviceId: 'bbb', deletedAt: null }
    expect(wins(b, a)).toBe(true)
    expect(wins(a, b)).toBe(false)
  })

  it('надгробие видно через метаданные', () => {
    const alive = { updatedAt: 1, lamport: 1, deviceId: TEST_DEVICE, deletedAt: null }
    expect(isDeleted(alive)).toBe(false)
    expect(isDeleted({ ...alive, deletedAt: 2 })).toBe(true)
  })
})
