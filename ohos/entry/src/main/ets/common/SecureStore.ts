import preferences from '@ohos.data.preferences'

export class SecureStore {
  private name = 'VaultAIAuth'
  async set(key: string, value: string): Promise<void> {
    const prefs = await preferences.getPreferences(globalThis, this.name)
    await prefs.put(key, value)
    await prefs.flush()
  }
  async get(key: string): Promise<string | null> {
    const prefs = await preferences.getPreferences(globalThis, this.name)
    const v = await prefs.get(key, null)
    if (v === null) return null
    return String(v)
  }
}
/// <reference path="../types/shims.d.ts" />
