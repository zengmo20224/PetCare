import { cpSync, existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const projectRoot = resolve(__dirname, '..')
const outputDir = process.env.UNI_OUTPUT_DIR
  ? resolve(process.env.UNI_OUTPUT_DIR)
  : resolve(projectRoot, 'dist', 'build', 'mp-weixin')
const sourceStaticDir = resolve(projectRoot, 'src', 'static')
const targetStaticDir = resolve(outputDir, 'static')
const appJsonPath = resolve(outputDir, 'app.json')
const projectConfigPath = resolve(outputDir, 'project.config.json')
const projectPrivateConfigPath = resolve(outputDir, 'project.private.config.json')
const scannableOutputExtensions = new Set(['.js', '.json', '.wxml', '.wxs', '.wxss'])
const forbiddenMpReferences = [
  /nutui-uniapp/i,
  /node-modules[\\/]+nutui-uniapp/i,
  /<nut-/i,
  /var\(--pc/i,
  /env\(/i,
  /backdrop-filter/i,
  /aspect-ratio/i,
  // `&gt;` HTML entity is only meaningful in UI declaration files (.wxml/.wxss/.json).
  // In bundled .js it appears legitimately as string literals inside third-party deps
  // (e.g. markdown-it's HTML-entity escape table), so we do NOT scan .js for this rule.
  { pattern: /&gt;/i, skipExtensions: new Set(['.js']) },
]

if (!existsSync(outputDir)) {
  throw new Error(`mp-weixin output directory does not exist: ${outputDir}`)
}

if (!existsSync(sourceStaticDir)) {
  throw new Error(`source static directory does not exist: ${sourceStaticDir}`)
}

mkdirSync(outputDir, { recursive: true })
cpSync(sourceStaticDir, targetStaticDir, { recursive: true, force: true })

if (!existsSync(appJsonPath)) {
  throw new Error(`app.json does not exist in mp-weixin output: ${appJsonPath}`)
}

const appJson = JSON.parse(readFileSync(appJsonPath, 'utf8'))
const missingIcons = []

for (const item of appJson.tabBar?.list ?? []) {
  for (const field of ['iconPath', 'selectedIconPath']) {
    const iconPath = item[field]
    if (iconPath && !existsSync(resolve(outputDir, iconPath))) {
      missingIcons.push(iconPath)
    }
  }
}

if (missingIcons.length > 0) {
  throw new Error(`mp-weixin tabBar icon files are missing: ${missingIcons.join(', ')}`)
}

const preferredLibVersion =
  findInstalledWechatBaseLibVersion()
  ?? readBaseLibraryVersion(projectConfigPath)
  ?? readBaseLibraryVersion(projectPrivateConfigPath)
if (preferredLibVersion) {
  pinBaseLibraryVersion(projectConfigPath, preferredLibVersion)
  pinBaseLibraryVersion(projectPrivateConfigPath, preferredLibVersion)
  console.log(`mp-weixin base library pinned to ${preferredLibVersion}`)
}

assertNoForbiddenMpReferences(outputDir)

console.log(`mp-weixin static assets ready: ${targetStaticDir}`)

function assertNoForbiddenMpReferences(scanRoot) {
  const violations = []

  for (const filePath of listScannableFiles(scanRoot)) {
    const content = readFileSync(filePath, 'utf8')
    const extension = filePath.slice(filePath.lastIndexOf('.'))
    for (const entry of forbiddenMpReferences) {
      // Entries are either a RegExp (applies to all file types) or
      // { pattern, skipExtensions } for rules that must skip certain file types.
      const pattern = entry instanceof RegExp ? entry : entry.pattern
      const skipExtensions = entry instanceof RegExp ? null : entry.skipExtensions
      if (skipExtensions && skipExtensions.has(extension)) continue
      if (pattern.test(content)) {
        violations.push(`${relative(scanRoot, filePath)} (${pattern.source})`)
        break
      }
    }
  }

  if (violations.length > 0) {
    throw new Error(`mp-weixin output contains unsupported UI references: ${violations.join(', ')}`)
  }
}

function listScannableFiles(scanRoot) {
  const files = []
  const entries = readdirSync(scanRoot, { withFileTypes: true })

  for (const entry of entries) {
    const entryPath = resolve(scanRoot, entry.name)
    if (entry.isDirectory()) {
      files.push(...listScannableFiles(entryPath))
      continue
    }

    const extension = entry.name.slice(entry.name.lastIndexOf('.'))
    if (scannableOutputExtensions.has(extension)) {
      files.push(entryPath)
    }
  }

  return files
}

function findInstalledWechatBaseLibVersion() {
  const localAppData = process.env.LOCALAPPDATA
  if (!localAppData) return null

  const userDataDir = resolve(localAppData, '微信开发者工具', 'User Data')
  if (!existsSync(userDataDir)) return null

  const candidates = []
  for (const profile of readdirSync(userDataDir, { withFileTypes: true })) {
    if (!profile.isDirectory()) continue
    const vendorDir = resolve(userDataDir, profile.name, 'WeappVendor')
    if (!existsSync(vendorDir)) continue
    for (const file of readdirSync(vendorDir)) {
      const match = /^(\d+\.\d+\.\d+)\.wxvpkg$/.exec(file)
      if (match) {
        candidates.push(match[1])
      }
    }
  }

  return candidates.sort(compareSemver).at(-1) ?? null
}

function compareSemver(left, right) {
  const leftParts = left.split('.').map(Number)
  const rightParts = right.split('.').map(Number)
  for (let index = 0; index < 3; index += 1) {
    const diff = leftParts[index] - rightParts[index]
    if (diff !== 0) return diff
  }
  return 0
}

function pinBaseLibraryVersion(configPath, libVersion) {
  if (!existsSync(configPath)) return

  const config = JSON.parse(readFileSync(configPath, 'utf8'))
  config.libVersion = libVersion
  writeFileSync(configPath, `${JSON.stringify(config, null, 2)}\n`)
}

function readBaseLibraryVersion(configPath) {
  if (!existsSync(configPath)) return null

  const config = JSON.parse(readFileSync(configPath, 'utf8'))
  return typeof config.libVersion === 'string' && config.libVersion ? config.libVersion : null
}
