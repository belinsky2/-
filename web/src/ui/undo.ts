import { useCallback, useEffect, useState } from 'preact/hooks'

/**
 * Отмена последнего действия.
 *
 * Кнопка постоянная и подписана тем, что именно отменяет: безымянная кнопка
 * в углу заставляет угадывать, что произойдёт по нажатию, и ей перестают
 * доверять ровно в тот момент, когда она нужна.
 *
 * Глубина — одно действие. Стек отмен требует, чтобы человек помнил свою
 * историю правок; для работы над шуткой в метро это не тот инструмент.
 */
export interface UndoableAction {
  readonly label: string
  readonly undo: () => Promise<void>
}

export interface UndoState {
  readonly pending: UndoableAction | null
  readonly toast: string | null
  push: (label: string, undo: () => Promise<void>) => void
  run: () => Promise<void>
  clearToast: () => void
}

export function useUndo(): UndoState {
  const [pending, setPending] = useState<UndoableAction | null>(null)
  const [toast, setToast] = useState<string | null>(null)

  const push = useCallback((label: string, undo: () => Promise<void>) => {
    setPending({ label, undo })
  }, [])

  const run = useCallback(async () => {
    if (!pending) return
    // Снимаем сразу: пока идёт запись, второе нажатие отменило бы дважды.
    setPending(null)
    await pending.undo()
    setToast(pending.label)
  }, [pending])

  // Гасим по таймеру. Раньше это висело на onAnimationEnd, а анимации у
  // подсказки нет — событие не приходило, и она оставалась на экране навсегда.
  useEffect(() => {
    if (toast === null) return
    const id = setTimeout(() => setToast(null), 2600)
    return () => clearTimeout(id)
  }, [toast])

  const clearToast = useCallback(() => setToast(null), [])

  return { pending, toast, push, run, clearToast }
}
