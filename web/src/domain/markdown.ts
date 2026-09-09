import type { Bit, JournalEntry, SetList, Topic } from './domain'
import type { Id } from './identity'
import { isDeleted } from './identity'

/**
 * Экспорт по секциям тетради.
 *
 * Markdown, а не свой формат: выгрузка должна открываться где угодно и
 * читаться человеком через десять лет, когда приложения уже не будет.
 * Подписи приходят из UI — в данных не место видимому тексту.
 */
export interface MarkdownLabels {
  readonly title: string
  readonly topics: string
  readonly material: string
  readonly act: string
  readonly setLists: string
  readonly journal: string
  readonly noTopic: string
  readonly status: (s: Bit['status']) => string
  readonly attitude: (a: NonNullable<Bit['attitude']>) => string
  readonly technique: (t: NonNullable<Bit['elements']['punch']>['technique']) => string
  readonly role: (r: SetList['items'][number]['role']) => string
}

function bitBlock(b: Bit, L: MarkdownLabels): string {
  const out: string[] = [`### ${b.title}`, '', `*${L.status(b.status)}*`]
  if (b.attitude) out.push(`- Отношение: ${L.attitude(b.attitude)}`)
  if (b.elements.premise) out.push(`- Премиса: ${b.elements.premise}`)
  if (b.elements.setup) out.push(`- Подводка: ${b.elements.setup}`)
  if (b.elements.punch) {
    out.push(`- Добивка (${L.technique(b.elements.punch.technique)}): ${b.elements.punch.text}`)
  }
  if (b.elements.actOut) {
    const space = b.elements.actOut.hasSpaceWork ? ', есть работа с пространством' : ''
    out.push(`- Act-out${space}: ${b.elements.actOut.text}`)
  }
  if (b.elements.tags.length > 0) out.push(`- Теги: ${b.elements.tags.join(', ')}`)
  out.push('')
  return out.join('\n')
}

export function exportMarkdown(
  L: MarkdownLabels,
  topics: readonly Topic[],
  bits: readonly Bit[],
  setLists: readonly SetList[],
  journal: readonly JournalEntry[],
): string {
  const live = <T extends { meta: { deletedAt: number | null } }>(rows: readonly T[]) =>
    rows.filter((r) => !isDeleted(r.meta as never))

  const bitsAlive = live(bits)
  const byId = new Map<Id, Bit>(bitsAlive.map((b) => [b.id, b]))
  const out: string[] = [`# ${L.title}`, '']

  out.push(`## ${L.topics}`, '')
  for (const t of live(topics)) out.push(`- ${t.title}`)
  out.push('')

  out.push(`## ${L.material}`, '')
  const grouped = new Map<string, Bit[]>()
  for (const b of bitsAlive.filter((b) => b.status !== 'POLISHED')) {
    const key = live(topics).find((t) => t.id === b.topicId)?.title ?? L.noTopic
    grouped.set(key, [...(grouped.get(key) ?? []), b])
  }
  for (const [topic, list] of grouped) {
    out.push(`## ${topic}`, '')
    for (const b of list) out.push(bitBlock(b, L))
  }

  out.push(`## ${L.act}`, '')
  for (const b of bitsAlive.filter((b) => b.status === 'POLISHED')) out.push(bitBlock(b, L))

  out.push(`## ${L.setLists}`, '')
  for (const s of live(setLists)) {
    out.push(`### ${s.title}`, '')
    for (const i of [...s.items].sort((a, b) => a.order - b.order)) {
      out.push(`${i.order + 1}. ${byId.get(i.bitId)?.title ?? '—'} — ${L.role(i.role)}`)
    }
    out.push('')
  }

  out.push(`## ${L.journal}`, '')
  for (const e of live(journal)) {
    out.push(`### ${new Date(e.dayMillis).toLocaleDateString('ru-RU')}`, '', e.text, '')
  }

  return out.join('\n')
}

/** Простой поиск по всему тексту: одна строка запроса, без синтаксиса. */
export function searchBits(bits: readonly Bit[], query: string): Bit[] {
  const q = query.trim().toLowerCase()
  if (!q) return []
  return bits.filter((b) => {
    const hay = [
      b.title, b.elements.premise, b.elements.setup,
      b.elements.punch?.text, b.elements.actOut?.text, ...b.elements.tags,
    ].filter(Boolean).join(' ').toLowerCase()
    return hay.includes(q)
  })
}
