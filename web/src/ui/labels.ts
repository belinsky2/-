import type { Attitude, BitStatus, PunchTechnique } from '../domain/domain'

/**
 * Весь видимый текст живёт здесь.
 *
 * В Android-версии это же правило держалось проверкой сборки: строки в коде
 * расползаются, и потом ни перевести, ни переформулировать разом.
 */

export const ATTITUDE_LABEL: Record<Attitude, string> = {
  HARD: 'бесит',
  WEIRD: 'странно',
  SCARY: 'страшно',
  STUPID: 'тупо',
}

export const ATTITUDE_PROMPT: Record<Attitude, string> = {
  HARD: 'Самое тяжёлое в этой теме — это…',
  WEIRD: 'Самое странное в этой теме — это…',
  SCARY: 'Самое страшное в этой теме — это…',
  STUPID: 'Самое тупое в этой теме — это…',
}

export const TECHNIQUE_LABEL: Record<PunchTechnique, string> = {
  MIX: 'смешение',
  TURN: 'поворот',
  LIST_OF_THREE: 'список из трёх',
  SELF_MOCKING: 'самоирония',
  OTHER: 'другое',
}

export const STATUS_LABEL: Record<BitStatus, string> = {
  SEED: 'зерно',
  PREMISE: 'премиса',
  DRAFT: 'черновик',
  TESTED: 'обкатана',
  POLISHED: 'отшлифована',
  PARKED: 'отложена',
  RETIRED: 'списана',
}

export const T = {
  appName: 'Punchline',

  tabInbox: 'Входящие',
  tabTopics: 'Темы',
  tabMaterial: 'Материал',
  tabBackup: 'Архив',

  capturePlaceholder: 'Мысль, наблюдение, фраза',
  captureAdd: 'Записать',
  captureHint: 'Записывай как есть. Разбирать будешь потом.',
  inboxEmpty: 'Пусто. Первая строчка — самая дешёвая.',

  topicPlaceholder: 'О чём тебе есть что сказать',
  topicAdd: 'Добавить тему',
  topicsEmpty: 'Тем пока нет. Шутки растут из них.',
  topicDelete: 'Удалить',

  bitsEmpty: 'Материала пока нет.',
  noTopic: 'без темы',

  workshopTitle: 'Мастерская',
  fieldTitle: 'Название',
  fieldAttitude: 'Отношение',
  fieldPremise: 'Премиса',
  fieldSetup: 'Подводка',
  fieldPunch: 'Добивка',
  fieldTechnique: 'Техника',
  fieldActOut: 'Act-out',
  actOutSpaceWork: 'есть работа с пространством',
  fieldTags: 'Теги',
  tagPlaceholder: 'новый тег',
  tagAdd: 'плюс',

  save: 'Сохранить',
  saved: 'Сохранено',
  back: 'Назад',
  attitudeNone: 'убрать',

  undoPrefix: 'Отменить:',
  undoneToast: 'Отменено',

  backupTitle: 'Архив материала',
  backupExplain:
    'Один файл со всем материалом. Он сохраняется в Загрузки автоматически после каждой правки — но можно и вручную.',
  backupNow: 'Сохранить архив сейчас',
  backupRestore: 'Восстановить из архива',
  backupLast: 'Последний архив:',
  backupNever: 'ещё не сохранялся',
  backupStorageOk: 'Браузер обещает не удалять данные.',
  backupStorageWeak: 'Браузер не дал гарантию хранения — архив тем более важен.',
  backupCounts: 'В архиве:',
  backupBits: 'шуток',
  backupTopics: 'тем',
} as const

export const UNDO_LABEL = {
  bitCreated: 'добавление шутки',
  bitDeleted: 'удаление шутки',
  titleSet: 'название',
  attitudeSet: (a: Attitude) => `отношение «${ATTITUDE_LABEL[a]}»`,
  attitudeCleared: 'снятие отношения',
  premiseSet: 'премиса',
  setupSet: 'подводка',
  punchSet: 'добивка',
  actOutSet: 'act-out',
  tagAdded: (tag: string) => `тег «${tag}»`,
  tagRemoved: (tag: string) => `удаление тега «${tag}»`,
  statusSet: (s: BitStatus) => `статус «${STATUS_LABEL[s]}»`,
  topicAdded: 'добавление темы',
  topicDeleted: 'удаление темы',
} as const
