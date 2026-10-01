import { describe, expect, it } from 'vitest'
import 'fake-indexeddb/auto'
import { maxLamport, openDb } from './db'
import { MutationSink } from './sink'
import { MaterialStore } from './material'
import { restoreRow } from './restore'
import { StageStore } from './stage'
import { PracticeStore } from './practice'

let t = 1_700_000_000_000
const clock = () => t++

async function fresh() {
  const db = await openDb(`undo-${Math.random()}`)
  const sink = new MutationSink(clock, 'dev')
  return { db, sink, repo: new MaterialStore(db, sink, clock), stage: new StageStore(db, sink, clock), practice: new PracticeStore(db, sink, clock) }
}

async function draft(repo: MaterialStore) {
  const b = await repo.addBit('шутка')
  await repo.setAttitude(b.id, 'HARD')
  await repo.setPremise(b.id, 'премиса')
  await repo.setPunch(b.id, 'панчлайн', 'TURN')
  return b
}

describe('отмена отметки зала', () => {
  it('первая отметка отменяема: она возвращает созданную запись', async () => {
    const { repo, stage } = await fresh()
    const b = await draft(repo)
    const gig = await stage.addGig(null, 'OPEN_MIC', 'Подвал')
    const change = await stage.mark(gig.id, b.id, 'LAUGH')
    expect(change.prev).toBeNull()
    expect(change.row.result).toBe('LAUGH')
  })

  it('отмена первой отметки возвращает шутке прежний статус', async () => {
    const { db, sink, repo, stage } = await fresh()
    const b = await draft(repo)
    const gig = await stage.addGig(null, 'OPEN_MIC', 'Подвал')
    const change = await stage.mark(gig.id, b.id, 'LAUGH')
    await repo.refreshStatus(b.id)
    expect((await repo.bit(b.id))!.status).toBe('TESTED')

    // Отмена создания — надгробие поверх новой отметки, затем пересчёт.
    await restoreRow(db, sink, 'performances', {
      ...change.row, meta: { ...change.row.meta, deletedAt: change.row.meta.updatedAt },
    })
    await repo.refreshStatus(b.id)
    expect((await repo.bit(b.id))!.status).toBe('DRAFT')
  })

  it('отмена исправленной отметки возвращает прежнюю реакцию', async () => {
    const { db, sink, repo, stage } = await fresh()
    const b = await draft(repo)
    const gig = await stage.addGig(null, 'OPEN_MIC', 'Подвал')
    await stage.mark(gig.id, b.id, 'SILENCE')
    const change = await stage.mark(gig.id, b.id, 'BIG_LAUGH')
    expect(change.prev!.result).toBe('SILENCE')
    await restoreRow(db, sink, 'performances', change.prev!)
    expect((await stage.performances())[0]!.result).toBe('SILENCE')
  })
})

describe('часы устройства', () => {
  it('чтение настроек не двигает логические часы', async () => {
    const { sink, practice } = await fresh()
    const before = sink.current()
    await practice.settings()
    await practice.settings()
    expect(sink.current()).toBe(before)
  })

  it('наибольшие часы находятся по базе, даже если счётчик потерян', async () => {
    const { db, repo } = await fresh()
    await repo.addTopic('а')
    await repo.addTopic('б')
    const fromDb = await maxLamport(db)
    expect(fromDb).toBe(2)

    // Счётчик потерян — новое устройство-«клон» начинает с нуля.
    const restarted = new MutationSink(clock, 'dev', 0)
    restarted.observe(fromDb)
    expect(restarted.stamp().lamport).toBeGreaterThan(2)
  })
})
