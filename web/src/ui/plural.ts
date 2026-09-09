/**
 * Русские числовые формы: 1 шутка, 2 шутки, 5 шуток.
 *
 * Без этого интерфейс пишет «1 шуток» — мелочь, по которой сразу видно,
 * что текст собран машиной и никто в него не смотрел.
 */
export function plural(n: number, one: string, few: string, many: string): string {
  const abs = Math.abs(n) % 100
  const last = abs % 10
  if (abs > 10 && abs < 20) return many
  if (last > 1 && last < 5) return few
  if (last === 1) return one
  return many
}

export function count(n: number, one: string, few: string, many: string): string {
  return `${n} ${plural(n, one, few, many)}`
}
