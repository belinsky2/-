import type { Attitude, BitStatus, GigType, LaughResult, PunchTechnique, SetListRole } from '../domain/domain'

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

export const ROLE_LABEL: Record<SetListRole, string> = {
  OPENER: 'открывашка',
  BODY: 'тело',
  CLOSER: 'закрывашка',
  CALLBACK: 'каллбэк',
}

export const GIG_LABEL: Record<GigType, string> = {
  REHEARSAL: 'прогон',
  OPEN_MIC: 'открытый микрофон',
  SHOWCASE: 'шоукейс',
  PAID: 'платный',
}

export const LAUGH_LABEL: Record<LaughResult, string> = {
  SILENCE: 'тишина',
  CHUCKLE: 'хмык',
  LAUGH: 'смех',
  BIG_LAUGH: 'хохот',
  APPLAUSE_BREAK: 'аплодисменты',
}

export const PART_LABEL: Record<string, string> = {
  intro: 'Начало',
  one: 'Часть 1 · Основы',
  two: 'Часть 2 · Материал',
  three: 'Часть 3 · Ремесло',
  four: 'Часть 4 · Сцена',
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
  fieldDuration: 'Хронометраж',
  fieldDurationHint: 'Сколько это занимает на сцене. Нужно, чтобы сет считался по времени.',
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
    'Один файл со всем материалом. Сохраняется в Загрузки сам — раз в сутки или через каждые двадцать правок. Кнопка ниже делает это прямо сейчас.',
  backupNow: 'Сохранить архив сейчас',
  backupRestore: 'Восстановить из архива',
  backupLast: 'Последний архив:',
  backupNever: 'ещё не сохранялся',
  backupStorageOk: 'Браузер обещает не удалять данные.',
  backupStorageWeak: 'Браузер не дал гарантию хранения — архив тем более важен.',
  backupCounts: 'В архиве:',
  backupChooseFile: 'Выбрать файл архива',
  backupNoFile: 'Файл не выбран',

  // --- сегодня ---
  tabToday: 'Сегодня',
  tabPractice: 'Практика',
  tabSets: 'Сеты',
  tabJournal: 'Дневник',

  todayChain: 'Цепочка',
  todayChainEmpty: 'Цепочка не начата. Сегодня — подходящий день.',
  todayReady: 'Готового материала',
  todayOf: 'из',
  todayMinutes: 'мин',
  todayGigs: 'Выступлений за 30 дней',
  todayBottleneck: 'Где затык',
  todayBottleneckHint: 'Больше всего материала застряло здесь.',
  todayGoal: 'Цель по времени акта',
  todayVision: 'Комедийная цель',
  todayVisionPlaceholder: 'Через год я…',
  todayActOut: 'Шуток с act-out',
  todayAttitude: 'Отношения',
  todaySettings: 'Настройки и архив',
  todayNothing: 'Пока пусто. Начни с записи мысли во «Входящих».',

  // --- практика ---
  practiceDone: 'пройдено',
  practiceNext: 'Следующее',
  practiceNote: 'Что вынес',
  practicePage: 'стр.',
  practiceNoText:
    'Здесь только номера и ярлыки — тексты упражнений в книге. Приложение хранит то, что напишешь ты.',

  // --- сеты ---
  setsTitle: 'Сет-листы',
  setPlaceholder: 'Название сета',
  setAdd: 'Собрать сет',
  setsEmpty: 'Сетов нет. Сет — это порядок, в котором ты выйдешь.',
  setTarget: 'Хронометраж',
  setPlanned: 'Набрано',
  setAddBit: 'Добавить шутку',
  setCandidates: 'Кандидаты',
  setCandidatesHint: 'Обкатанное и отшлифованное, лучшее сверху.',
  setNoCandidates: 'Кандидатов нет: сначала нужно вынести material на сцену.',
  setStage: 'Режим сцены',
  setDelete: 'Удалить сет',
  setEmpty: 'В сете пока пусто.',
  setIssues: 'Замечания движка',

  issueCallback: 'Каллбэк стоит раньше шутки, на которую ссылается.',
  issueSameTopic: 'Две подряд на одну тему — разбавь.',
  issueWeakCloser: 'Закрывающая слабее, чем есть в сете.',
  issueOverTime: 'Перебор по времени',
  issueUnderTime: 'Недобор по времени',

  // --- сцена ---
  stageStart: 'Начать',
  stagePause: 'Пауза',
  stageNext: 'Дальше',
  stagePrev: 'Назад',
  stageFinish: 'Закончить и разобрать',
  stageExit: 'Выйти',
  stageDone: 'Сет отработан',
  stageOf: 'из',

  // --- выступления ---
  gigsTitle: 'Выступления',
  gigVenue: 'Где выступал',
  gigAdd: 'Записать выступление',
  gigsEmpty: 'Выступлений пока нет.',
  gigDuration: 'Длительность, мин',
  reviewTitle: 'Разбор',
  reviewHint: 'Отметь реакцию зала по каждой шутке. Одним тапом.',
  reviewScore: 'Laugh score',
  reviewLpm: 'смеха в минуту',
  reviewFreeHint: 'Сет не привязан — отметь то, что прозвучало.',
  reviewNoBits: 'Отмечать нечего: материала пока нет.',

  // --- дневник ---
  journalTitle: 'Утренние страницы',
  journalHint: 'Пиши не останавливаясь. Это черновик мыслей, а не текст.',
  journalPlaceholder: 'Что в голове',
  journalStart: 'Засечь время',
  journalSave: 'Сохранить запись',
  journalHarvest: 'Выделить в шутку',
  journalHarvestHint: 'Выдели кусок текста и нажми — он станет зерном во «Входящих».',
  journalEmpty: 'Записей нет.',
  journalMinutes: 'мин письма',

  // --- голос ---
  recStart: 'Записать голосом',
  recStop: 'Остановить',
  recDenied: 'Микрофон не разрешён.',
  recUnsupported: 'Браузер не даёт записывать звук.',
  recPlay: 'Прослушать',
  recDelete: 'Удалить запись',
  recHint: 'Рант: говори две-три минуты не останавливаясь.',

  // --- поиск и версии ---
  searchPlaceholder: 'Искать по всему материалу',
  searchEmpty: 'Ничего не нашлось.',
  versionsTitle: 'Версии',
  versionsEmpty: 'История пуста.',
  versionRestore: 'Вернуть эту версию',

  exportTitle: 'Экспорт в Markdown',
  exportDo: 'Выгрузить текстом',
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
  durationOfBit: 'хронометраж шутки',
  tagAdded: (tag: string) => `тег «${tag}»`,
  tagRemoved: (tag: string) => `удаление тега «${tag}»`,
  statusSet: (s: BitStatus) => `статус «${STATUS_LABEL[s]}»`,
  topicAdded: 'добавление темы',
  topicDeleted: 'удаление темы',
  setCreated: 'создание сета',
  setChanged: 'изменение сета',
  setDeleted: 'удаление сета',
  roleSet: (r: SetListRole) => `роль «${ROLE_LABEL[r]}»`,
  orderChanged: 'перестановка в сете',
  targetSet: 'хронометраж сета',
  gigCreated: 'запись выступления',
  marked: (r: LaughResult) => `отметка «${LAUGH_LABEL[r]}»`,
  durationSet: 'длительность выступления',
  journalSaved: 'запись в дневник',
  journalDeleted: 'удаление записи',
  exerciseToggled: (n: number) => `отметка упражнения №${n}`,
  exerciseNote: (n: number) => `заметка к упражнению №${n}`,
  goalSet: 'цель по времени',
  visionSet: 'комедийная цель',
} as const
