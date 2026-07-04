// Headless screenshot + console-error sweep for the v4 dev server (:5177).
// Usage: node shot.mjs <route> <outfile> [fullPage]
//        node shot.mjs --audit          (sweep all routes, report console errors)
import { chromium } from 'playwright-core'
import { homedir } from 'os'
import { join } from 'path'

const exe = join(homedir(), '.cache/ms-playwright/chromium-1223/chrome-linux64/chrome')
const BASE = 'http://localhost:5177'

const ROUTES = [
  '/', '/dex', '/moves', '/items', '/cards', '/furniture', '/achievements',
  '/quirks', '/types', '/world', '/story', '/quests', '/trainers', '/bosses',
  '/camps', '/squadrons', '/tutorials', '/mechanics', '/logic-graphs', '/about',
]

const browser = await chromium.launch({ executablePath: exe })
const page = await browser.newPage({ viewport: { width: 1440, height: 900 } })

if (process.argv[2] === '--audit') {
  const errs = []
  page.on('console', m => { if (m.type() === 'error') errs.push(`${page.url()} :: ${m.text().slice(0, 200)}`) })
  page.on('pageerror', e => errs.push(`${page.url()} :: PAGEERROR ${String(e).slice(0, 200)}`))
  for (const r of ROUTES) {
    await page.goto(BASE + r, { waitUntil: 'networkidle', timeout: 30000 }).catch(e => errs.push(`${r} :: NAV ${e}`))
    await page.waitForTimeout(400)
  }
  console.log(errs.length ? errs.join('\n') : 'OK: 0 console errors across ' + ROUTES.length + ' routes')
} else {
  const [route, out, full] = process.argv.slice(2)
  await page.goto(BASE + route, { waitUntil: 'networkidle', timeout: 30000 })
  await page.waitForTimeout(600)
  await page.screenshot({ path: out, fullPage: full === 'full' })
  console.log('saved', out)
}
await browser.close()
