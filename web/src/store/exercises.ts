/**
 * Указатель по книге: номер, рабочий ярлык, страница.
 *
 * Текстов Картер здесь нет и быть не должно — приложение хранит только то,
 * что написал автор. Это и правовая чистота, и смысл: переписывать книгу
 * в приложение незачем, она есть у владельца.
 */
export interface Exercise {
  readonly number: number
  readonly part: string
  readonly shortTitle: string
  readonly bookPage: number | null
  readonly inputSchema: string
}

export const PART_ORDER = ['intro', 'one', 'two', 'three', 'four'] as const

let cache: Exercise[] | null = null

export async function loadExercises(): Promise<Exercise[]> {
  if (cache) return cache
  const res = await fetch(new URL('exercises.json', document.baseURI))
  const data = (await res.json()) as { exercises: Exercise[] }
  cache = data.exercises
  return cache
}

export function groupByPart(list: readonly Exercise[]): [string, Exercise[]][] {
  const groups = new Map<string, Exercise[]>()
  for (const e of list) {
    const g = groups.get(e.part) ?? []
    g.push(e)
    groups.set(e.part, g)
  }
  return PART_ORDER.filter((p) => groups.has(p)).map((p) => [
    p,
    groups.get(p)!.sort((a, b) => a.number - b.number),
  ])
}

/** Следующее нерешённое по порядку книги: путь по тетради линеен, и это к лучшему. */
export function nextExercise(
  list: readonly Exercise[],
  doneNumbers: ReadonlySet<number>,
): Exercise | null {
  const ordered = [...list].sort(
    (a, b) => PART_ORDER.indexOf(a.part as never) - PART_ORDER.indexOf(b.part as never) || a.number - b.number,
  )
  return ordered.find((e) => !doneNumbers.has(e.number)) ?? null
}
