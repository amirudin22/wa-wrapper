import Dexie from 'dexie'

interface AppCacheEntry {
  key: string
  value: unknown
  timestamp: number
}

interface StaticAsset {
  url: string
  blob: Blob
  timestamp: number
  ttl: number
}

const db = new Dexie('WAWrapperCache') as Dexie & {
  appCache: Dexie.Table<AppCacheEntry, 'key'>
  staticAssets: Dexie.Table<StaticAsset, 'url'>
}

db.version(1).stores({
  appCache: 'key, timestamp',
  staticAssets: 'url, timestamp',
})

const CACHE_TTL = 7 * 24 * 60 * 60 * 1000

export async function setCache(key: string, value: unknown): Promise<void> {
  await db.appCache.put({ key, value, timestamp: Date.now() })
}

export async function getCache<T>(key: string): Promise<T | undefined> {
  const entry = await db.appCache.get(key)
  if (!entry) return undefined
  return entry.value as T
}

export async function clearExpired(): Promise<void> {
  const cutoff = Date.now() - CACHE_TTL
  await db.appCache.where('timestamp').below(cutoff).delete()
  await db.staticAssets.where('timestamp').below(cutoff).delete()
}
