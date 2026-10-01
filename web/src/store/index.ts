import type { Clock, DeviceId } from '../domain/identity'
import { maxLamport, openDb } from './db'
import { MutationSink } from './sink'
import { MaterialStore } from './material'
import { StageStore } from './stage'
import { PracticeStore } from './practice'

/** Всё хранилище приложения: одна база, одни часы, три раздела по смыслу. */
export interface Stores {
  readonly db: IDBDatabase
  readonly sink: MutationSink
  readonly material: MaterialStore
  readonly stage: StageStore
  readonly practice: PracticeStore
}

/**
 * Открыть хранилище.
 *
 * Логические часы подтягиваются к наибольшим в базе: сохранённый отдельно
 * счётчик может потеряться, и тогда новые правки проигрывали бы старым.
 */
export async function openStores(
  clock: Clock,
  deviceId: DeviceId,
  savedLamport: number,
  dbName?: string,
): Promise<Stores> {
  const db = await openDb(dbName)
  const sink = new MutationSink(clock, deviceId, savedLamport)
  sink.observe(await maxLamport(db))
  return {
    db,
    sink,
    material: new MaterialStore(db, sink, clock),
    stage: new StageStore(db, sink, clock),
    practice: new PracticeStore(db, sink, clock),
  }
}
