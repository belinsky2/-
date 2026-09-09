/**
 * Прогон сценария, который сломался на телефоне, с картинками.
 *
 * Смысл не в картинках, а в том, что до этого единственным способом узнать,
 * работает ли экран, было попросить человека поставить APK и потыкать.
 */
import { createRequire } from 'node:module'

// Playwright установлен глобально в этом окружении и остаётся CommonJS-модулем.
const { chromium } = createRequire(import.meta.url)('/opt/node22/lib/node_modules/playwright')
import { mkdirSync, rmSync } from 'node:fs'
import { resolve } from 'node:path'

const BASE = process.env.BASE_URL ?? 'http://127.0.0.1:4173'
const OUT = resolve(process.env.SHOTS_DIR ?? 'shots')

rmSync(OUT, { recursive: true, force: true })
mkdirSync(OUT, { recursive: true })

const failures = []
function check(name, ok, detail = '') {
  if (ok) console.log(`  ✓ ${name}`)
  else { console.log(`  ✗ ${name} ${detail}`); failures.push(name) }
}

const browser = await chromium.launch()
// Размер и плотность Galaxy S24 Ultra в CSS-пикселях.
const ctx = await browser.newContext({
  viewport: { width: 412, height: 915 },
  deviceScaleFactor: 2,
  locale: 'ru-RU',
  hasTouch: true,
  isMobile: true,
})
const page = await ctx.newPage()
page.on('pageerror', (e) => { console.log(`  ✗ ошибка страницы: ${e.message}`); failures.push('pageerror') })

let step = 0
const shot = async (name) => page.screenshot({ path: `${OUT}/${String(++step).padStart(2, '0')}-${name}.png` })

await page.goto(BASE)
await page.waitForSelector('[data-screen=inbox]')
await shot('inbox-empty')
check('входящие открываются пустыми', await page.isVisible('[data-testid=inbox-empty]'))

// --- дефект №3: не получилось добавить ни одной шутки -----------------
await page.fill('[data-testid=capture-input]', 'В лифте все смотрят вверх')
await page.click('[data-testid=capture-add]')
await page.waitForSelector('[data-testid=bit-item]')
await shot('inbox-one-bit')
check('шутка добавляется', (await page.locator('[data-testid=bit-item]').count()) === 1)
check('поле очищается после записи', (await page.inputValue('[data-testid=capture-input]')) === '')

// --- запрошенная фича: отмена подписана действием ---------------------
check('полоса отмены появилась', await page.isVisible('[data-testid=undobar]'))
const undoText = await page.textContent('[data-testid=undobar]')
check('отмена названа', undoText.includes('добавление шутки'), `текст: ${undoText}`)

await page.click('[data-testid=bit-item]')
await page.waitForSelector('[data-screen=workshop]')
await shot('workshop-fresh')

// --- дефект №1: верстка. Ряд фишек не должен уезжать за экран --------
await page.click('[data-testid=attitude-HARD]')
await page.waitForSelector('[data-testid=premise-prompt]')
await shot('workshop-attitude')
const overflow = await page.evaluate(() =>
  document.documentElement.scrollWidth - document.documentElement.clientWidth)
check('нет горизонтальной прокрутки', overflow <= 0, `лишних пикселей: ${overflow}`)

// --- дефект №2: добивка не сохраняется -------------------------------
await page.fill('[data-testid=premise-input]', 'Самое тяжёлое в лифте — это тишина')
await page.click('[data-testid=premise-save]')
await page.waitForSelector('[data-testid=premise-save][data-saved="1"]')

await page.fill('[data-testid=punch-input]', 'Мы все делаем вид, что этаж — это очень интересно')
await page.click('[data-testid=punch-save]')
await page.click('[data-testid=technique-LIST_OF_THREE]')
await page.fill('[data-testid=tag-input]', 'быт')
await page.click('[data-testid=tag-add]')
await page.waitForSelector('[data-testid=tag-chip]')
await shot('workshop-filled')
check('после записи остаётся отметка «Сохранено», а не мёртвая кнопка',
  (await page.getAttribute('[data-testid=punch-save]', 'data-saved')) === '1')

// Настоящая проверка: пережила ли добивка перезагрузку.
await page.reload()
await page.waitForSelector('[data-screen=inbox]')
await page.click('[data-testid=bit-item]')
await page.waitForSelector('[data-screen=workshop]')
await shot('workshop-after-reload')
check('добивка пережила перезагрузку',
  (await page.inputValue('[data-testid=punch-input]')).includes('этаж'))
check('премиса пережила перезагрузку',
  (await page.inputValue('[data-testid=premise-input]')).includes('тишина'))
check('тег пережил перезагрузку', (await page.locator('[data-testid=tag-chip]').count()) === 1)
check('техника пережила перезагрузку',
  (await page.getAttribute('[data-testid=technique-LIST_OF_THREE]', 'class')).includes('on'))

// --- отмена реально откатывает --------------------------------------
await page.fill('[data-testid=tag-input]', 'лишний')
await page.click('[data-testid=tag-add]')
await page.waitForFunction(() => document.querySelectorAll('[data-testid=tag-chip]').length === 2)
const undoLabel = await page.textContent('[data-testid=undobar]')
check('отмена названа тегом', undoLabel.includes('лишний'), `текст: ${undoLabel}`)
await shot('workshop-undo-offered')
await page.click('[data-testid=undo]')
await page.waitForFunction(() => document.querySelectorAll('[data-testid=tag-chip]').length === 1)
await shot('workshop-undone')
check('отмена вернула прежнее состояние',
  (await page.locator('[data-testid=tag-chip]').count()) === 1)
check('подсказка об отмене показана', await page.isVisible('[data-testid=toast]'))
await page.waitForSelector('[data-testid=toast]', { state: 'detached', timeout: 6000 })
check('подсказка гаснет сама', (await page.locator('[data-testid=toast]').count()) === 0)

// --- остальные вкладки ------------------------------------------------
await page.click('[data-testid=back]')
await page.click('[data-testid=tab-topics]')
await page.fill('[data-testid=topic-input]', 'Городская жизнь')
await page.click('[data-testid=topic-add]')
await page.waitForSelector('[data-testid=topic-item]')
await shot('topics')
check('тема добавляется', (await page.locator('[data-testid=topic-item]').count()) === 1)

await page.click('[data-testid=tab-backup]')
await page.waitForSelector('[data-screen=backup]')
await shot('backup')

// --- дефект №4: архив должен быть одним понятным файлом --------------
const dl = await Promise.all([
  page.waitForEvent('download'),
  page.click('[data-testid=backup-export]'),
]).then(([d]) => d)
const name = dl.suggestedFilename()
check('архив — один файл .json', name.endsWith('.json'), `имя: ${name}`)

await browser.close()

console.log(failures.length === 0
  ? `\nВсе проверки прошли. Экраны: ${OUT}`
  : `\nПРОВАЛЕНО: ${failures.join(', ')}`)
process.exit(failures.length === 0 ? 0 : 1)
