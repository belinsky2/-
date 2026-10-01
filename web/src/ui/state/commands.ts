import type {
  Attitude, Bit, GigType, LaughResult, PunchTechnique, SetListRole,
} from '../../domain/domain'
import type { Id, SyncMeta } from '../../domain/identity'
import type { StoreName } from '../../store/db'
import { undoRowOf, type Change } from '../../store/change'
import { restoreRow } from '../../store/restore'
import { UNDO_LABEL } from '../labels'
import type { BackupState } from './backup'
import type { AppState } from './data'
import type { UndoState } from './undo'

type Row = { id: Id; meta: SyncMeta }

/**
 * Все изменения материала — здесь, и только здесь.
 *
 * Каждое изменение: записать → перечитать экраны → учесть для автоархива →
 * подписать отмену. Если отменить нечего, полоса отмены гаснет: прежде она
 * продолжала предлагать прошлое действие, и нажатие откатывало не то.
 */
export function useCommands(app: AppState, backup: BackupState, undo: UndoState) {
  const stores = app.stores

  async function commit<R extends Row>(
    label: string,
    store: StoreName,
    run: () => Promise<Change<R> | null>,
    afterUndo?: () => Promise<void>,
  ): Promise<R | null> {
    if (!stores) return null
    const change = await run()
    await app.reload()
    await backup.noteChange()
    if (!change) {
      undo.clear()
      return null
    }
    const back = undoRowOf(change)
    undo.push(label, async () => {
      await restoreRow(stores.db, stores.sink, store, back)
      if (afterUndo) await afterUndo()
      await app.reload()
    })
    return change.row
  }

  /** Правка существующей записи: метод хранилища возвращает её прежний вид. */
  const update = <R extends Row>(
    label: string, store: StoreName, run: () => Promise<R | null>, afterUndo?: () => Promise<void>,
  ) => commit<R>(label, store, async () => {
    const prev = await run()
    return prev ? { prev, row: prev } : null
  }, afterUndo).then(() => undefined)

  /** Создание: отмена кладёт надгробие поверх новой записи. */
  const create = <R extends Row>(label: string, store: StoreName, run: () => Promise<R>) =>
    commit<R>(label, store, async () => ({ prev: null, row: await run() }))

  if (!stores) return null
  const { material, stage, practice } = stores

  /** Правка шутки: перед ней — снимок версии, не чаще раза в пять минут. */
  function bit(b: Bit) {
    const edit = (label: string, fn: () => Promise<Bit | null>) => async () => {
      await material.snapshot(b)
      await update(label, 'bits', fn)
    }
    return {
      setTitle: (v: string) => edit(UNDO_LABEL.titleSet, () => material.setTitle(b.id, v))(),
      setAttitude: (a: Attitude | null) =>
        edit(a ? UNDO_LABEL.attitudeSet(a) : UNDO_LABEL.attitudeCleared,
          () => material.setAttitude(b.id, a))(),
      setPremise: (v: string) => edit(UNDO_LABEL.premiseSet, () => material.setPremise(b.id, v))(),
      setSetup: (v: string) => edit(UNDO_LABEL.setupSet, () => material.setSetup(b.id, v))(),
      setPunch: (v: string, t: PunchTechnique) =>
        edit(UNDO_LABEL.punchSet, () => material.setPunch(b.id, v, t))(),
      setActOut: (v: string, space: boolean) =>
        edit(UNDO_LABEL.actOutSet, () => material.setActOut(b.id, v, space))(),
      setDuration: (sec: number | null) =>
        edit(UNDO_LABEL.durationOfBit, () => material.setDuration(b.id, sec))(),
      setTopic: (topicId: Id | null) =>
        edit(UNDO_LABEL.topicSet, () => material.setTopic(b.id, topicId))(),
      setTags: (tags: string[]) => {
        const before = b.elements.tags
        const added = tags.find((x) => !before.includes(x))
        const removed = before.find((x) => !tags.includes(x))
        const label = added ? UNDO_LABEL.tagAdded(added)
          : removed ? UNDO_LABEL.tagRemoved(removed)
          : UNDO_LABEL.tagsChanged
        return edit(label, () => material.setTags(b.id, tags))()
      },
      addAudio: (blob: Blob, mime: string, sec: number) =>
        create(UNDO_LABEL.audioAdded, 'audio', () => material.addAudio(blob, mime, sec, b.id, null))
          .then(() => undefined),
      deleteAudio: (id: Id) =>
        update(UNDO_LABEL.audioDeleted, 'audio', () => material.deleteAudio(id)),
    }
  }

  function setList(id: Id) {
    const edit = (label: string, fn: () => Promise<Row | null>) => update(label, 'setLists', fn)
    return {
      addBit: (bitId: Id, dur: number | null) =>
        edit(UNDO_LABEL.setChanged, () => stage.addToSetList(id, bitId, dur)),
      remove: (itemId: Id) => edit(UNDO_LABEL.setChanged, () => stage.removeFromSetList(id, itemId)),
      setRole: (itemId: Id, role: SetListRole) =>
        edit(UNDO_LABEL.roleSet(role), () => stage.setItemRole(id, itemId, role)),
      move: (itemId: Id, delta: -1 | 1) =>
        edit(UNDO_LABEL.orderChanged, () => stage.moveItem(id, itemId, delta)),
      setTarget: (sec: number) => edit(UNDO_LABEL.targetSet, () => stage.setTargetDuration(id, sec)),
      deleteSet: () => edit(UNDO_LABEL.setDeleted, () => stage.deleteSetList(id)),
    }
  }

  function gig(id: Id) {
    return {
      /**
       * Отметка реакции зала. Статус шутки пересчитывается и после отметки,
       * и после её отмены: прежде отменённый смех оставлял шутку обкатанной.
       */
      mark: (bitId: Id, result: LaughResult) =>
        commit(UNDO_LABEL.marked(result), 'performances', async () => {
          const change = await stage.mark(id, bitId, result)
          await material.refreshStatus(bitId)
          return change
        }, () => material.refreshStatus(bitId)).then(() => undefined),
      setDuration: (sec: number) =>
        update(UNDO_LABEL.durationSet, 'gigs', () => stage.setGigDuration(id, sec)),
    }
  }

  return {
    material: {
      addBit: (title: string) =>
        create(UNDO_LABEL.bitCreated, 'bits', () => material.addBit(title)).then(() => undefined),
      addTopic: (title: string) =>
        create(UNDO_LABEL.topicAdded, 'topics', () => material.addTopic(title)).then(() => undefined),
      deleteTopic: (id: Id) => update(UNDO_LABEL.topicDeleted, 'topics', () => material.deleteTopic(id)),
      bit,
    },
    stage: {
      addSetList: (title: string, target: number) =>
        create(UNDO_LABEL.setCreated, 'setLists', () => stage.addSetList(title, target))
          .then(() => undefined),
      addGig: (setId: Id | null, type: GigType, venue: string) =>
        create(UNDO_LABEL.gigCreated, 'gigs', () => stage.addGig(setId, type, venue))
          .then(() => undefined),
      /**
       * Конец сцены — выступление с длительностью, одним действием. Прежде оно
       * писалось мимо общего пути: его нельзя было отменить, и оно не попадало
       * в счётчик автоархива.
       */
      finishStage: async (setId: Id, title: string, elapsedSec: number) => {
        const g = await create(UNDO_LABEL.gigCreated, 'gigs',
          () => stage.addGig(setId, 'OPEN_MIC', title, elapsedSec))
        return g?.id ?? null
      },
      setList,
      gig,
    },
    practice: {
      setGoal: (m: number) =>
        update(UNDO_LABEL.goalSet, 'settings', () => practice.saveSettings({ goalMinutes: m })),
      setVision: (v: string) =>
        update(UNDO_LABEL.visionSet, 'settings', () => practice.saveSettings({ comedyVision: v })),
      toggleExercise: (n: number, done: boolean) =>
        commit(UNDO_LABEL.exerciseToggled(n), 'exercises', () => practice.toggleExercise(n, done))
          .then(() => undefined),
      setExerciseNote: (n: number, note: string) =>
        commit(UNDO_LABEL.exerciseNote(n), 'exercises', () => practice.setExerciseNote(n, note))
          .then(() => undefined),
      saveJournal: (text: string, sec: number) =>
        create(UNDO_LABEL.journalSaved, 'journal', () => practice.addJournalEntry(text, sec))
          .then(() => undefined),
      deleteJournal: (id: Id) =>
        update(UNDO_LABEL.journalDeleted, 'journal', () => practice.deleteJournalEntry(id)),
    },
  }
}

export type Commands = NonNullable<ReturnType<typeof useCommands>>
export type BitCommands = ReturnType<Commands['material']['bit']>
