/**
 * Прогон всего приложения с картинками.
 *
 * Смысл не в картинках, а в том, что до этого единственным способом узнать,
 * работает ли экран, было попросить человека поставить APK и потыкать.
 */
import { createRequire } from 'node:module'
import { mkdirSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

const { chromium } = createRequire(import.meta.url)('playwright')

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:4173'
const OUT = resolve(process.env.SHOTS_DIR ?? 'shots')

rmSync(OUT, { recursive: true, force: true })
mkdirSync(OUT, { recursive: true })

const failures = []
function check(name, ok, detail = '') {
  if (ok) console.log(`  ✓ ${name}`)
  else { console.log(`  ✗ ${name} ${detail}`); failures.push(name) }
}

const browser = await chromium.launch({
  // Фальшивый микрофон: запись голоса — часть методики, и проверять её
  // нужно так же, как всё остальное, а не «на живом устройстве потом».
  args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'],
})
// Размер и плотность Galaxy S24 Ultra в CSS-пикселях.
const ctx = await browser.newContext({
  viewport: { width: 412, height: 915 },
  deviceScaleFactor: 2,
  locale: 'ru-RU',
  hasTouch: true,
  isMobile: true,
  permissions: ['microphone'],
})
const page = await ctx.newPage()
page.on('pageerror', (e) => { console.log(`  ✗ ошибка страницы: ${e.message}`); failures.push('pageerror') })

let step = 0
const shot = async (name) => page.screenshot({ path: `${OUT}/${String(++step).padStart(2, '0')}-${name}.png` })
const noOverflow = async () =>
  page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth)

await page.goto(BASE)
await page.waitForSelector('[data-screen=today]')
await shot('today-empty')

// ================= Сегодня =================
check('главный экран открывается первым', await page.isVisible('[data-screen=today]'))
await page.click('[data-testid=goal-30]')
await page.waitForFunction(() =>
  document.querySelector('[data-testid=ready-minutes]')?.textContent?.includes('30'))
check('цель по времени сохраняется', true)

// --- справка ---
await page.click('[data-testid=help-toggle]')
await page.waitForSelector('[data-testid=help]')
const helpToday = await page.textContent('[data-testid=help]')
check('справка «Сегодня» содержательна', helpToday.includes('цепочка'), `текст: ${helpToday.slice(0, 60)}`)
await shot('help')
await page.click('[data-testid=help-close]')
check('справка закрывается', (await page.locator('[data-testid=help]').count()) === 0)

// ================= Материал: захват =================
await page.click('[data-testid=tab-material]')
await page.waitForSelector('[data-screen=material]')
await page.fill('[data-testid=capture-input]', 'В лифте все смотрят вверх')
await page.click('[data-testid=capture-add]')
await page.waitForSelector('[data-testid=bit-item]')
check('шутка добавляется', (await page.locator('[data-testid=bit-item]').count()) === 1)

const undoText = await page.textContent('[data-testid=undobar]')
check('отмена подписана действием', undoText.includes('добавление шутки'), `текст: ${undoText}`)

await page.click('[data-testid=undo]')
await page.waitForSelector('[data-testid=inbox-empty]')
check('отмена создания убирает шутку', (await page.locator('[data-testid=bit-item]').count()) === 0)

await page.fill('[data-testid=capture-input]', 'В лифте все смотрят вверх')
await page.click('[data-testid=capture-add]')
await page.waitForSelector('[data-testid=bit-item]')

await page.fill('[data-testid=topic-input]', 'Городская жизнь')
await page.click('[data-testid=topic-add]')
await page.waitForSelector('[data-testid=topic-item]')
await shot('material')
check('тема добавляется', (await page.locator('[data-testid=topic-item]').count()) === 1)

// ================= Мастерская =================
await page.click('[data-testid=bit-item]')
await page.waitForSelector('[data-screen=workshop]')
await page.click('[data-testid=attitude-HARD]')
await page.waitForSelector('[data-testid=premise-prompt]')
check('нет горизонтальной прокрутки в мастерской', (await noOverflow()) <= 0)

await page.fill('[data-testid=premise-input]', 'Самое тяжёлое в лифте — это тишина')
await page.click('[data-testid=premise-save]')
await page.waitForSelector('[data-testid=premise-save][data-saved="1"]')
await page.fill('[data-testid=punch-input]', 'Мы все делаем вид, что этаж — это очень интересно')
await page.click('[data-testid=punch-save]')
await page.click('[data-testid=technique-LIST_OF_THREE]')
await page.fill('[data-testid=actout-input]', 'Задираю голову и считаю этажи')
await page.click('[data-testid=actout-save]')
// --- голос ---
await page.click('[data-testid=rec-start]')
await page.waitForSelector('[data-testid=rec-stop]')
await page.waitForTimeout(1200)
await page.click('[data-testid=rec-stop]')
await page.waitForSelector('[data-testid=clip]')
check('запись голоса сохраняется и появляется в списке',
  (await page.locator('[data-testid=clip]').count()) === 1)

await page.click('[data-testid=duration-90]')
await page.fill('[data-testid=tag-input]', 'быт')
await page.click('[data-testid=tag-add]')
await page.waitForSelector('[data-testid=tag-chip]')
await shot('workshop')

await page.reload()
await page.waitForSelector('[data-screen=today]')
await page.click('[data-testid=tab-material]')
await page.click('[data-testid=bit-item]')
await page.waitForSelector('[data-screen=workshop]')
check('добивка пережила перезагрузку',
  (await page.inputValue('[data-testid=punch-input]')).includes('этаж'))
check('act-out пережил перезагрузку',
  (await page.inputValue('[data-testid=actout-input]')).includes('этажи'))
check('запись голоса пережила перезагрузку',
  (await page.locator('[data-testid=clip]').count()) === 1)
check('техника пережила перезагрузку',
  (await page.getAttribute('[data-testid=technique-LIST_OF_THREE]', 'class')).includes('on'))

// Справка на каждом экране своя, а не одна на всё приложение.
await page.click('[data-testid=help-toggle]')
await page.waitForSelector('[data-testid=help]')
const helpWorkshop = await page.textContent('[data-testid=help]')
check('справка мастерской объясняет act-out', helpWorkshop.includes('act-out'))
check('справка меняется вместе с экраном', helpWorkshop !== helpToday)
await page.click('[data-testid=help-close]')

// ================= Поиск =================
await page.click('[data-testid=back]')
await page.fill('[data-testid=search]', 'этаж')
await page.waitForSelector('[data-testid=search-results]')
check('поиск находит по добивке, а не только по названию',
  (await page.locator('[data-testid=search-results] .item').count()) === 1)
await shot('search')
await page.fill('[data-testid=search]', '')

// ================= Практика =================
await page.click('[data-testid=tab-practice]')
await page.waitForSelector('[data-screen=practice]')
check('каталог упражнений загрузился',
  (await page.locator('[data-testid=exercise-item]').count()) === 48)
check('следующее упражнение подсказано', await page.isVisible('[data-testid=practice-next]'))
await page.click('[data-testid=exercise-1]')
await page.waitForFunction(() =>
  document.querySelector('[data-testid=practice-progress]')?.textContent?.trim().startsWith('1'))
check('упражнение отмечается пройденным', true)
await shot('practice')

// ================= Сеты =================
await page.click('[data-testid=tab-sets]')
await page.waitForSelector('[data-screen=sets]')
await page.fill('[data-testid=set-title]', 'Первая пятиминутка')
await page.click('[data-testid=target-300]')
await page.click('[data-testid=set-add]')
await page.waitForSelector('[data-testid=set-item]')
check('сет создаётся', (await page.locator('[data-testid=set-item]').count()) === 1)

await page.click('[data-testid=set-item]')
await page.waitForSelector('[data-screen=set-editor]')
await page.click('[data-testid=set-add-bit]')
// Шутка ещё не была на сцене — в кандидаты не попадает.
check('сырое в сет не предлагается',
  (await page.locator('[data-testid=candidate]').count()) === 0)
await shot('set-editor-empty')

// ================= Выступление и разбор =================
await page.click('[data-testid=back]')
await page.fill('[data-testid=gig-venue]', 'Подвал')
await page.click('[data-testid=gigtype-OPEN_MIC]')
await page.click('[data-testid=gig-add]')
await page.waitForSelector('[data-testid=gig-item]')
check('выступление записывается', (await page.locator('[data-testid=gig-item]').count()) === 1)

await page.click('[data-testid=gig-item]')
await page.waitForSelector('[data-screen=review]')
await page.fill('[data-testid=gig-duration]', '5')
await page.click('[data-testid=review-row] [data-testid=mark-BIG_LAUGH]')
await page.waitForFunction(() =>
  document.querySelector('[data-testid=review-score]')?.textContent?.includes('3.00'))
check('отметка зала пересчитывает laugh score', true)
await shot('review')

// Отмеченная шутка обязана стать кандидатом в сет.
await page.click('[data-testid=back]')
await page.click('[data-testid=set-item]')
await page.waitForSelector('[data-screen=set-editor]')
await page.click('[data-testid=set-add-bit]')
await page.waitForSelector('[data-testid=candidate]')
check('обкатанная шутка попадает в кандидаты', true)
await page.click('[data-testid=candidate]')
await page.waitForSelector('[data-testid=set-row]')
check('шутка встаёт в сет', (await page.locator('[data-testid=set-row]').count()) === 1)
check('недобор по времени виден', await page.isVisible('[data-testid=set-issues]'))
const planned = (await page.textContent('[data-testid=set-planned]')).trim()
check('хронометраж шутки попадает в сумму сета', planned.startsWith('1:30'), `сумма: ${planned}`)
await shot('set-editor')
check('нет горизонтальной прокрутки в сете', (await noOverflow()) <= 0)

// ================= Сцена =================
await page.click('[data-testid=go-stage]')
await page.waitForSelector('[data-screen=stage]')
check('на сцене видна подсказка, а не полный текст',
  (await page.textContent('[data-testid=stage-title]')).includes('лифт'))
await page.click('[data-testid=stage-run]')
await shot('stage')
await page.click('[data-testid=stage-finish]')
await page.waitForSelector('[data-screen=review]')
check('после сцены сразу открывается разбор', true)

// ================= Дневник =================
await page.click('[data-testid=back]')
await page.click('[data-testid=tab-journal]')
await page.waitForSelector('[data-screen=journal]')
await page.fill('[data-testid=journal-input]', 'Утром думал про лифты и про то, как в них молчат')
await page.click('[data-testid=journal-save]')
await page.waitForSelector('[data-testid=journal-entry]')
check('запись дневника сохраняется',
  (await page.locator('[data-testid=journal-entry]').count()) === 1)
check('цепочка началась', await page.isVisible('[data-testid=journal-streak]'))
await shot('journal')

// ================= Сегодня после работы =================
await page.click('[data-testid=tab-today]')
await page.waitForSelector('[data-screen=today]')
check('цепочка показана на главном', await page.isVisible('[data-testid=streak]'))
// Склонения — та ошибка, которую тесты не видят, а человек видит сразу.
const streakText = (await page.textContent('[data-testid=streak]')).replace(/\s+/g, ' ').trim()
check('число склоняется по-русски', streakText === '1 день подряд', `текст: ${streakText}`)
check('выступления за 30 дней посчитаны',
  (await page.textContent('[data-testid=gigs-30]')).trim() === '2')
check('затык воронки определён', await page.isVisible('[data-testid=bottleneck]'))
await shot('today')

// ================= Архив и экспорт =================
await page.click('[data-testid=open-settings]')
await page.waitForSelector('[data-screen=backup]')
check('в настройках есть общая карта приложения', await page.isVisible('[data-testid=guide]'))
const guide = await page.textContent('[data-testid=guide]')
for (const tabName of ['Сегодня', 'Практика', 'Материал', 'Сеты', 'Дневник']) {
  check(`карта упоминает раздел «${tabName}»`, guide.includes(tabName))
}
await shot('backup')
const vault = await Promise.all([
  page.waitForEvent('download'),
  page.click('[data-testid=backup-export]'),
]).then(([d]) => d)
check('архив — один файл .json', vault.suggestedFilename().endsWith('.json'))

const md = await Promise.all([
  page.waitForEvent('download'),
  page.click('[data-testid=export-md]'),
]).then(([d]) => d)
check('экспорт в Markdown отдаёт .md', md.suggestedFilename().endsWith('.md'))

await browser.close()

console.log(failures.length === 0
  ? `\nВсе проверки прошли. Экраны: ${OUT}`
  : `\nПРОВАЛЕНО: ${failures.join(', ')}`)
process.exit(failures.length === 0 ? 0 : 1)
