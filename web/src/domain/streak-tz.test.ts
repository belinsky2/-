import { describe, expect, it } from 'vitest'
import { localDayOf, streakDays } from './metrics'

/** Сутки по Ханою (UTC+7), как у владельца. */
const hanoi = localDayOf(7 * 60)

describe('цепочка считается по местным суткам', () => {
  it('вчера в 21:00 и сегодня в 06:30 по Ханою — это два дня', () => {
    const yesterday = Date.UTC(2026, 8, 30, 14, 0)  // 30 сент 21:00 +07
    const today = Date.UTC(2026, 8, 30, 23, 30)     // 1 окт 06:30 +07
    expect(streakDays([yesterday, today], today, hanoi)).toBe(2)
  })

  it('06:30 и 08:00 одного утра по Ханою — один день', () => {
    const early = Date.UTC(2026, 8, 30, 23, 30)     // 1 окт 06:30 +07
    const later = Date.UTC(2026, 9, 1, 1, 0)        // 1 окт 08:00 +07
    expect(streakDays([early, later], later, hanoi)).toBe(1)
  })
})
