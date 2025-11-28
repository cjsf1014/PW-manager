import preferences from '@ohos.data.preferences'
import { Crypto } from './Crypto'

export class PasswordManager {
  private name = 'VaultAIPasswords'
  private prefix = 'password_'
  async save(master: string, entry: { siteName: string, username: string, password: string, note: string }): Promise<boolean> {
    const plain = `网站: ${entry.siteName}\n用户名: ${entry.username}\n密码: ${entry.password}\n备注: ${entry.note}`
    const enc = await Crypto.encryptV2(plain, master)
    const key = this.buildKey(entry.siteName, entry.username)
    const prefs = await preferences.getPreferences(globalThis, this.name)
    await prefs.put(key, enc)
    await prefs.flush()
    return true
  }
  async all(master: string): Promise<string[]> {
    const prefs = await preferences.getPreferences(globalThis, this.name)
    const keys = await prefs.getAll()
    const out: string[] = []
    for (const k of Object.keys(keys)) {
      if (k.startsWith(this.prefix)) {
        const s = keys[k]
        if (typeof s === 'string' && s.trim().length > 0) {
          try {
            const d = await Crypto.decryptAny(s, master)
            out.push(d)
          } catch {}
        }
      }
    }
    return out
  }
  async delete(site: string, user: string): Promise<boolean> {
    const key = this.buildKey(site, user)
    const prefs = await preferences.getPreferences(globalThis, this.name)
    if (await prefs.get(key, null) !== null) {
      await prefs.delete(key)
      await prefs.flush()
      return true
    }
    return false
  }
  buildKey(site: string, user: string): string { return this.prefix + site + '|' + user }
}
/// <reference path="../types/shims.d.ts" />
