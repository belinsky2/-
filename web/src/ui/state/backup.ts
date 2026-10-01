import { useCallback, useRef, useState } from 'preact/hooks'
import type { SetListRole } from '../../domain/domain'
import { exportMarkdown } from '../../domain/markdown'
import {
  backupDue, buildVault, downloadVault, importVault, parseVault, type ImportReport,
} from '../../store/backup'
import { ATTITUDE_LABEL, ROLE_LABEL, STATUS_LABEL, T, TECHNIQUE_LABEL } from '../labels'
import type { AppState } from './data'
import { deviceId } from './device'

const BACKUP_KEY = 'punchline.lastBackupAt'
const CHANGES_KEY = 'punchline.changesSinceBackup'

export interface BackupState {
  readonly lastBackupAt: number | null
  /** Учесть правку: через двадцать правок или сутки архив скачается сам. */
  noteChange: () => Promise<void>
  exportNow: () => Promise<void>
  importFile: (file: File) => Promise<ImportReport>
  exportMarkdownFile: () => void
}

/**
 * Архив и экспорт.
 *
 * Скачивание браузер разрешает только в ответ на действие человека, поэтому
 * автоархив проверяется в момент правки, а не по таймеру в фоне.
 */
export function useBackup(app: AppState): BackupState {
  const [lastBackupAt, setLastBackupAt] = useState<number | null>(
    Number(localStorage.getItem(BACKUP_KEY)) || null,
  )
  const changes = useRef(Number(localStorage.getItem(CHANGES_KEY)) || 0)

  const exportNow = useCallback(async () => {
    if (!app.stores) return
    const now = Date.now()
    downloadVault(await buildVault(app.stores.db, deviceId(), app.stores.sink.current(), now))
    localStorage.setItem(BACKUP_KEY, String(now))
    changes.current = 0
    localStorage.setItem(CHANGES_KEY, '0')
    setLastBackupAt(now)
  }, [app.stores])

  const noteChange = useCallback(async () => {
    changes.current += 1
    localStorage.setItem(CHANGES_KEY, String(changes.current))
    if (backupDue(changes.current, lastBackupAt, Date.now())) await exportNow()
  }, [lastBackupAt, exportNow])

  const importFile = useCallback(async (file: File): Promise<ImportReport> => {
    if (!app.stores) return { ok: false, reason: T.importNotReady }
    const parsed = parseVault(await file.text())
    if (!parsed.ok) return parsed
    const report = await importVault(app.stores.db, app.stores.sink, parsed.raw)
    await app.reload()
    return report
  }, [app])

  const exportMarkdownFile = useCallback(() => {
    const { data } = app
    const md = exportMarkdown(
      {
        title: T.appName, topics: T.tabTopics, material: T.tabMaterial, act: T.mdAct,
        setLists: T.setsTitle, journal: T.journalTitle, noTopic: T.noTopic,
        status: (s) => STATUS_LABEL[s], attitude: (a) => ATTITUDE_LABEL[a],
        technique: (t) => TECHNIQUE_LABEL[t], role: (r: SetListRole) => ROLE_LABEL[r],
      },
      data.topics, data.bits, data.setLists, data.journal,
    )
    const url = URL.createObjectURL(new Blob([md], { type: 'text/markdown' }))
    const a = document.createElement('a')
    a.href = url
    a.download = 'punchline.md'
    a.click()
    setTimeout(() => URL.revokeObjectURL(url), 10_000)
  }, [app])

  return { lastBackupAt, noteChange, exportNow, importFile, exportMarkdownFile }
}
