import { describe, expect, it } from 'vitest'
import { exportMarkdown, searchBits, type MarkdownLabels } from './markdown'
import { TestClock, bit, meta } from './fixtures'
import type { Bit, SetList, Topic } from './domain'

const t = new TestClock()

const L: MarkdownLabels = {
  title: 'Панчлайн', topics: 'Темы', material: 'Материал', act: 'Мой акт',
  setLists: 'Сет-листы', journal: 'Дневник', noTopic: 'без темы',
  status: (s) => s, attitude: (a) => a, technique: (x) => x, role: (r) => r,
}

const topic: Topic = { id: 'topic-1', title: 'Лифты', passionScore: 5, isCore: true, meta: meta(t.clock) }

const draft: Bit = {
  ...bit(t.clock, { id: 'b1', topicId: 'topic-1', attitude: 'HARD', premise: 'тишина' }),
  title: 'Лифт',
  elements: {
    premise: 'тишина', setup: null,
    punch: { text: 'этаж интересен', technique: 'TURN' },
    actOut: { text: 'смотрю вверх', hasSpaceWork: true, audioHash: null },
    tags: ['быт'], callbackTo: null,
  },
}

const polished: Bit = { ...bit(t.clock, { id: 'b2', status: 'POLISHED' }), title: 'Готовая' }

const set: SetList = {
  id: 's1', title: 'Пятиминутка', targetDurationSec: 300,
  items: [{ id: 'i1', bitId: 'b2', order: 0, role: 'OPENER', plannedDurationSec: 60 }],
  meta: meta(t.clock),
}

describe('экспорт', () => {
  const md = exportMarkdown(L, [topic], [draft, polished], [set], [
    { id: 'j1', dayMillis: 1_700_000_000_000, text: 'утро', durationSec: 600, meta: meta(t.clock) },
  ])

  it('содержит все секции тетради', () => {
    for (const h of ['# Панчлайн', '## Темы', '## Материал', '## Мой акт', '## Сет-листы', '## Дневник']) {
      expect(md).toContain(h)
    }
  })

  it('разносит незаконченное и готовое по разным секциям', () => {
    const act = md.slice(md.indexOf('## Мой акт'), md.indexOf('## Сет-листы'))
    expect(act).toContain('Готовая')
    expect(act).not.toContain('Лифт\n')
  })

  it('выносит разобранную шутку целиком', () => {
    expect(md).toContain('этаж интересен')
    expect(md).toContain('смотрю вверх')
    expect(md).toContain('есть работа с пространством')
    expect(md).toContain('быт')
  })

  it('не выносит удалённое', () => {
    const dead = { ...polished, meta: { ...polished.meta, deletedAt: 1 } }
    expect(exportMarkdown(L, [topic], [draft, dead], [], [])).not.toContain('Готовая')
  })

  it('шутка без темы попадает в свою группу, а не теряется', () => {
    const orphan = { ...bit(t.clock, { id: 'b3', topicId: null }), title: 'Сирота' }
    const out = exportMarkdown(L, [topic], [orphan], [], [])
    expect(out).toContain('## без темы')
    expect(out).toContain('Сирота')
  })
})

describe('поиск', () => {
  it('пустой запрос ничего не возвращает', () => {
    expect(searchBits([draft], '  ')).toHaveLength(0)
  })

  it('ищет по добивке, act-out и тегам, не только по названию', () => {
    expect(searchBits([draft], 'этаж')).toHaveLength(1)
    expect(searchBits([draft], 'смотрю')).toHaveLength(1)
    expect(searchBits([draft], 'быт')).toHaveLength(1)
  })

  it('регистр не важен', () => {
    expect(searchBits([draft], 'ЛИФТ')).toHaveLength(1)
  })

  it('чужое не находит', () => {
    expect(searchBits([draft], 'самолёт')).toHaveLength(0)
  })
})
