import { useCallback, useEffect, useMemo, useState } from 'preact/hooks'
import type {
  Bit, BitPerformance, ExerciseRecord, Gig, JournalEntry, SetList, Settings, Topic,
} from '../../domain/domain'
import { systemClock, type Id } from '../../domain/identity'
import {
  actOutRatio, attitudeSpread, averageScoreByBit, funnel, polishedMinutes, streakDays, type Progress,
} from '../../domain/metrics'
import { requestPersistence } from '../../store/db'
import { openStores, type Stores } from '../../store'
import { loadExercises, type Exercise } from '../../store/exercises'
import { deviceId, saveLamport, savedLamport } from './device'

/** Всё, что показывают экраны. Перечитывается целиком после каждого изменения. */
export interface AppData {
  readonly bits: Bit[]
  readonly topics: Topic[]
  readonly setLists: SetList[]
  readonly gigs: Gig[]
  readonly performances: BitPerformance[]
  readonly journal: JournalEntry[]
  readonly exerciseRecords: ExerciseRecord[]
  readonly settings: Settings
}

export const DEFAULT_SETTINGS: Settings = {
  id: 'settings',
  goalMinutes: 5,
  comedyVision: '',
  meta: { updatedAt: 0, lamport: 0, deviceId: '', deletedAt: null },
}

const EMPTY: AppData = {
  bits: [], topics: [], setLists: [], gigs: [], performances: [],
  journal: [], exerciseRecords: [], settings: DEFAULT_SETTINGS,
}

export interface AppState {
  readonly stores: Stores | null
  readonly data: AppData
  readonly exercises: readonly Exercise[]
  readonly persistent: boolean
  readonly scores: ReadonlyMap<Id, number>
  readonly progress: Progress
  reload: () => Promise<void>
}

/**
 * Открыть хранилище и держать данные экранов в актуальном виде.
 *
 * Перечитывание целиком после каждого изменения — сознательная простота:
 * материала у одного автора — сотни записей, а не миллионы, и синхронно
 * обновлять десяток списков вручную дороже, чем перечитать их.
 */
export function useAppData(): AppState {
  const [stores, setStores] = useState<Stores | null>(null)
  const [data, setData] = useState<AppData>(EMPTY)
  const [exercises, setExercises] = useState<Exercise[]>([])
  const [persistent, setPersistent] = useState(false)

  useEffect(() => {
    void (async () => {
      setStores(await openStores(systemClock, deviceId(), savedLamport()))
      setPersistent(await requestPersistence())
      // Каталог упражнений — статический файл, а не часть базы: он от книги,
      // а не от автора, и обновляется вместе с приложением.
      setExercises(await loadExercises().catch(() => []))
    })()
  }, [])

  const reload = useCallback(async () => {
    if (!stores) return
    const { material, stage, practice, sink } = stores
    const [bits, topics, setLists, gigs, performances, journal, exerciseRecords, settings] =
      await Promise.all([
        material.bits(), material.topics(), stage.setLists(), stage.gigs(), stage.performances(),
        practice.journal(), practice.exercises(), practice.settings(),
      ])
    setData({ bits, topics, setLists, gigs, performances, journal, exerciseRecords, settings })
    saveLamport(sink.current())
  }, [stores])

  useEffect(() => {
    void reload()
  }, [reload])

  const scores = useMemo(() => averageScoreByBit(data.performances), [data.performances])

  const progress: Progress = useMemo(() => {
    const activity = [
      ...data.bits.map((b) => b.meta.updatedAt),
      ...data.journal.map((j) => j.dayMillis),
      ...data.gigs.map((g) => g.dateMillis),
    ]
    return {
      funnel: funnel(data.bits),
      polishedMinutes: polishedMinutes(data.bits),
      goalMinutes: data.settings.goalMinutes,
      actOutRatio: actOutRatio(data.bits),
      attitudeSpread: attitudeSpread(data.bits),
      gigsLast30Days: stores ? stores.stage.gigsLast30Days(data.gigs) : 0,
      streakDays: streakDays(activity, Date.now()),
    }
  }, [data, stores])

  return { stores, data, exercises, persistent, scores, progress, reload }
}
