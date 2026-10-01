import { beforeEach, describe, expect, it } from 'vitest'
import 'fake-indexeddb/auto'
import { getAll, openDb } from './db'
import { MutationSink } from './sink'
import { MaterialStore } from './material'
import { StageStore } from './stage'
import { PracticeStore } from './practice'
import type { BitPerformance, BitVersion } from '../domain/domain'
import { averageScoreByBit } from '../domain/metrics'

let n = 0
let now = 1_700_000_000_000
const clock = () => now + n++

async function fresh() {
  const db = await openDb(`r2-${Math.random()}`)
  const sink = new MutationSink(clock, 'dev')
  return { db, sink, repo: new MaterialStore(db, sink, clock), stage: new StageStore(db, sink, clock), practice: new PracticeStore(db, sink, clock) }
}

describe('сет-листы', () => {
  beforeEach(() => { n = 0 })

  it('первая позиция становится открывающей, следующие — телом', async () => {
    const { repo, stage } = await fresh()
    const a = await repo.addBit('первая')
    const b = await repo.addBit('вторая')
    const s = await stage.addSetList('Пятиминутка', 300)

    await stage.addToSetList(s.id, a.id, 60)
    await stage.addToSetList(s.id, b.id, 60)

    const items = (await stage.setList(s.id))!.items
    expect(items.map((i) => i.role)).toEqual(['OPENER', 'BODY'])
  })

  it('перестановка меняет порядок и не оставляет дыр в нумерации', async () => {
    const { repo, stage } = await fresh()
    const a = await repo.addBit('a')
    const b = await repo.addBit('b')
    const c = await repo.addBit('c')
    const s = await stage.addSetList('Сет', 300)
    for (const x of [a, b, c]) await stage.addToSetList(s.id, x.id, 60)

    const itemOf = async (bitId: string) =>
      (await stage.setList(s.id))!.items.find((i) => i.bitId === bitId)!.id

    await stage.moveItem(s.id, await itemOf(c.id), -1)
    const after = [...(await stage.setList(s.id))!.items].sort((x, y) => x.order - y.order)
    expect(after.map((i) => i.bitId)).toEqual([a.id, c.id, b.id])
    expect(after.map((i) => i.order)).toEqual([0, 1, 2])
  })

  it('за край списка позиция не уезжает', async () => {
    const { repo, stage } = await fresh()
    const a = await repo.addBit('a')
    const s = await stage.addSetList('Сет', 300)
    await stage.addToSetList(s.id, a.id, 60)
    const item = (await stage.setList(s.id))!.items[0]!

    await stage.moveItem(s.id, item.id, -1)
    await stage.moveItem(s.id, item.id, 1)
    expect((await stage.setList(s.id))!.items).toHaveLength(1)
  })

  it('удаление позиции перенумеровывает остальные', async () => {
    const { repo, stage } = await fresh()
    const a = await repo.addBit('a')
    const b = await repo.addBit('b')
    const s = await stage.addSetList('Сет', 300)
    await stage.addToSetList(s.id, a.id, 60)
    await stage.addToSetList(s.id, b.id, 60)
    const first = (await stage.setList(s.id))!.items.find((i) => i.bitId === a.id)!

    await stage.removeFromSetList(s.id, first.id)
    const items = (await stage.setList(s.id))!.items
    expect(items).toHaveLength(1)
    expect(items[0]!.order).toBe(0)
  })
})

describe('тема шутки', () => {
  beforeEach(() => { n = 0 })

  it('шутку можно привязать к теме и отвязать обратно', async () => {
    const { repo } = await fresh()
    const topic = await repo.addTopic('Лифты')
    const b = await repo.addBit('В лифте молчат')
    expect((await repo.bit(b.id))!.topicId).toBeNull()

    await repo.setTopic(b.id, topic.id)
    expect((await repo.bit(b.id))!.topicId).toBe(topic.id)

    await repo.setTopic(b.id, null)
    expect((await repo.bit(b.id))!.topicId).toBeNull()
  })

  it('привязка к теме не сбивает статус шутки', async () => {
    const { repo, stage } = await fresh()
    const topic = await repo.addTopic('Лифты')
    const b = await repo.addBit('шутка')
    await repo.setAttitude(b.id, 'HARD')
    await repo.setPremise(b.id, 'премиса')
    await repo.setPunch(b.id, 'добивка', 'TURN')
    const gig = await stage.addGig(null, 'OPEN_MIC', 'Подвал')
    await stage.mark(gig.id, b.id, 'LAUGH')
    await repo.refreshStatus(b.id)

    await repo.setTopic(b.id, topic.id)
    expect((await repo.bit(b.id))!.status).toBe('TESTED')
  })
})

describe('отметки зала', () => {
  beforeEach(() => { n = 0 })

  it('повторная отметка исправляет прежнюю, а не добавляет вторую', async () => {
    const { db, repo, stage } = await fresh()
    const bit = await repo.addBit('шутка')
    const gig = await stage.addGig(null, 'OPEN_MIC', 'Подвал')

    await stage.mark(gig.id, bit.id, 'SILENCE')
    await stage.mark(gig.id, bit.id, 'BIG_LAUGH')

    const rows = await getAll<BitPerformance>(db, 'performances')
    expect(rows).toHaveLength(1)
    expect(rows[0]!.result).toBe('BIG_LAUGH')
    expect(averageScoreByBit(await stage.performances()).get(bit.id)).toBe(3)
  })

  it('одна шутка в разных выступлениях — разные отметки', async () => {
    const { repo, stage } = await fresh()
    const bit = await repo.addBit('шутка')
    const g1 = await stage.addGig(null, 'OPEN_MIC', 'Первый')
    const g2 = await stage.addGig(null, 'OPEN_MIC', 'Второй')

    await stage.mark(g1.id, bit.id, 'SILENCE')
    await stage.mark(g2.id, bit.id, 'BIG_LAUGH')

    expect(await stage.performances()).toHaveLength(2)
    expect(averageScoreByBit(await stage.performances()).get(bit.id)).toBe(1.5)
  })

  it('выступления за 30 дней считаются по дате, а не по всему списку', async () => {
    const { stage } = await fresh()
    const gigs = [
      { dateMillis: now - 5 * 24 * 3600 * 1000 },
      { dateMillis: now - 40 * 24 * 3600 * 1000 },
    ] as never[]
    expect(stage.gigsLast30Days(gigs)).toBe(1)
  })
})

describe('связка «сцена → отметки → статус»', () => {
  beforeEach(() => { n = 0 })

  it('отметка зала выводит шутку из черновика в обкатанные', async () => {
    const { repo, stage } = await fresh()
    const b = await repo.addBit('шутка')
    await repo.setAttitude(b.id, 'HARD')
    await repo.setPremise(b.id, 'премиса')
    await repo.setPunch(b.id, 'добивка', 'TURN')
    expect((await repo.bit(b.id))!.status).toBe('DRAFT')

    const gig = await stage.addGig(null, 'OPEN_MIC', 'Подвал')
    await stage.mark(gig.id, b.id, 'CHUCKLE')
    await repo.refreshStatus(b.id)

    expect((await repo.bit(b.id))!.status).toBe('TESTED')
  })

  it('два настоящих смеха доводят шутку до отшлифованной', async () => {
    const { repo, stage } = await fresh()
    const b = await repo.addBit('шутка')
    await repo.setAttitude(b.id, 'HARD')
    await repo.setPremise(b.id, 'премиса')
    await repo.setPunch(b.id, 'добивка', 'TURN')

    const g1 = await stage.addGig(null, 'OPEN_MIC', 'Первый')
    const g2 = await stage.addGig(null, 'OPEN_MIC', 'Второй')
    await stage.mark(g1.id, b.id, 'LAUGH')
    await stage.mark(g2.id, b.id, 'BIG_LAUGH')
    await repo.refreshStatus(b.id)

    expect((await repo.bit(b.id))!.status).toBe('POLISHED')
  })

  it('правка обкатанной шутки не роняет её обратно в черновик', async () => {
    const { repo, stage } = await fresh()
    const b = await repo.addBit('шутка')
    await repo.setAttitude(b.id, 'HARD')
    await repo.setPremise(b.id, 'премиса')
    await repo.setPunch(b.id, 'добивка', 'TURN')

    const gig = await stage.addGig(null, 'OPEN_MIC', 'Подвал')
    await stage.mark(gig.id, b.id, 'LAUGH')
    await repo.refreshStatus(b.id)
    expect((await repo.bit(b.id))!.status).toBe('TESTED')

    // Поправить опечатку в добивке — не то же самое, что откатить материал.
    await repo.setPunch(b.id, 'добивка без опечатки', 'TURN')
    expect((await repo.bit(b.id))!.status).toBe('TESTED')
  })

  it('отложенную шутку отметки из архива не вытаскивают', async () => {
    const { repo, stage } = await fresh()
    const b = await repo.addBit('шутка')
    await repo.setAttitude(b.id, 'HARD')
    await repo.setPremise(b.id, 'премиса')
    await repo.setPunch(b.id, 'добивка', 'TURN')
    await repo.setStatus(b.id, 'PARKED')

    const gig = await stage.addGig(null, 'OPEN_MIC', 'Подвал')
    await stage.mark(gig.id, b.id, 'BIG_LAUGH')
    await repo.refreshStatus(b.id)

    expect((await repo.bit(b.id))!.status).toBe('PARKED')
  })
})

describe('версии шутки', () => {
  beforeEach(() => { n = 0 })

  it('снимок не чаще раза в пять минут', async () => {
    const { db, repo } = await fresh()
    const bit = await repo.addBit('шутка')

    await repo.snapshot(bit)
    await repo.snapshot(bit)
    expect(await getAll<BitVersion>(db, 'versions')).toHaveLength(1)

    now += 6 * 60 * 1000
    await repo.snapshot(bit)
    expect(await getAll<BitVersion>(db, 'versions')).toHaveLength(2)
    now = 1_700_000_000_000
  })
})

describe('упражнения и настройки', () => {
  beforeEach(() => { n = 0 })

  it('отметка упражнения не плодит записи', async () => {
    const { practice } = await fresh()
    await practice.toggleExercise(7, true)
    await practice.toggleExercise(7, false)
    const all = await practice.exercises()
    expect(all).toHaveLength(1)
    expect(all[0]!.done).toBe(false)
  })

  it('заметка к упражнению переживает переключение отметки', async () => {
    const { practice } = await fresh()
    await practice.setExerciseNote(7, 'что понял')
    await practice.toggleExercise(7, true)
    const rec = (await practice.exercises())[0]!
    expect(rec.note).toBe('что понял')
    expect(rec.done).toBe(true)
  })

  it('настройки отдаются с разумными значениями до первого сохранения', async () => {
    const { practice } = await fresh()
    expect((await practice.settings()).goalMinutes).toBe(5)
    await practice.saveSettings({ goalMinutes: 30 })
    expect((await practice.settings()).goalMinutes).toBe(30)
  })
})
