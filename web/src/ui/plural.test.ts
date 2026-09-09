import { describe, expect, it } from 'vitest'
import { count } from './plural'

const bits = (n: number) => count(n, 'шутка', 'шутки', 'шуток')

describe('числовые формы', () => {
  it('единственное число', () => {
    expect(bits(1)).toBe('1 шутка')
    expect(bits(21)).toBe('21 шутка')
    expect(bits(101)).toBe('101 шутка')
  })

  it('от двух до четырёх', () => {
    expect(bits(2)).toBe('2 шутки')
    expect(bits(23)).toBe('23 шутки')
  })

  it('пять и больше', () => {
    expect(bits(0)).toBe('0 шуток')
    expect(bits(5)).toBe('5 шуток')
    expect(bits(100)).toBe('100 шуток')
  })

  it('подросшие числа от одиннадцати до четырнадцати — исключение', () => {
    expect(bits(11)).toBe('11 шуток')
    expect(bits(12)).toBe('12 шуток')
    expect(bits(14)).toBe('14 шуток')
    expect(bits(111)).toBe('111 шуток')
  })
})
