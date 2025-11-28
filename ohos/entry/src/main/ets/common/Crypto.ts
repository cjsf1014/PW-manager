export class Crypto {
  static async encryptV2(plain: string, password: string): Promise<string> {
    const salt = Crypto.randomBytes(16)
    const iv = Crypto.randomBytes(12)
    const key = await Crypto.pbkdf2(password, salt, 120000, 16)
    const cipher = await Crypto.aesGcmEncrypt(key, iv, Crypto.toUtf8(plain))
    const combined = new Uint8Array(salt.length + iv.length + cipher.length)
    combined.set(salt, 0)
    combined.set(iv, salt.length)
    combined.set(cipher, salt.length + iv.length)
    return 'v2:' + Crypto.toBase64(combined)
  }
  static async decryptAny(stored: string, password: string): Promise<string> {
    if (!stored.startsWith('v2:')) throw new Error('unsupported')
    const b64 = stored.substring(3)
    const combined = Crypto.fromBase64(b64)
    const salt = combined.slice(0, 16)
    const iv = combined.slice(16, 28)
    const body = combined.slice(28)
    const key = await Crypto.pbkdf2(password, salt, 120000, 16)
    const plain = await Crypto.aesGcmDecrypt(key, iv, body)
    return Crypto.fromUtf8(plain)
  }
  static async pbkdf2(password: string, salt: Uint8Array, iterations: number, keyLen: number): Promise<Uint8Array> {
    const pw = Crypto.toUtf8(password)
    let derived = new Uint8Array(keyLen)
    const blockCount = Math.ceil(keyLen / 32)
    let offset = 0
    for (let i = 1; i <= blockCount; i++) {
      const be = new Uint8Array(4)
      be[0] = (i >>> 24) & 0xff
      be[1] = (i >>> 16) & 0xff
      be[2] = (i >>> 8) & 0xff
      be[3] = i & 0xff
      let u = await Crypto.hmacSha256(pw, Crypto.concat(salt, be))
      let t = u.slice()
      for (let j = 2; j <= iterations; j++) {
        u = await Crypto.hmacSha256(pw, u)
        for (let k = 0; k < t.length; k++) t[k] ^= u[k]
      }
      const take = Math.min(32, keyLen - offset)
      derived.set(t.slice(0, take), offset)
      offset += take
    }
    return derived
  }
  static async aesGcmEncrypt(key: Uint8Array, iv: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
    const subtle = globalThis.crypto.subtle
    const algo = { name: 'AES-GCM', iv }
    const k = await subtle.importKey('raw', key, 'AES-GCM', false, ['encrypt'])
    const enc = await subtle.encrypt(algo, k, data)
    return new Uint8Array(enc)
  }
  static async aesGcmDecrypt(key: Uint8Array, iv: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
    const subtle = globalThis.crypto.subtle
    const algo = { name: 'AES-GCM', iv }
    const k = await subtle.importKey('raw', key, 'AES-GCM', false, ['decrypt'])
    const dec = await subtle.decrypt(algo, k, data)
    return new Uint8Array(dec)
  }
  static async hmacSha256(key: Uint8Array, data: Uint8Array): Promise<Uint8Array> {
    const subtle = globalThis.crypto.subtle
    const k = await subtle.importKey('raw', key, { name: 'HMAC', hash: 'SHA-256' }, false, ['sign'])
    const sig = await subtle.sign('HMAC', k, data)
    return new Uint8Array(sig)
  }
  static concat(a: Uint8Array, b: Uint8Array): Uint8Array {
    const out = new Uint8Array(a.length + b.length)
    out.set(a, 0)
    out.set(b, a.length)
    return out
  }
  static randomBytes(n: number): Uint8Array {
    const out = new Uint8Array(n)
    globalThis.crypto.getRandomValues(out)
    return out
  }
  static toUtf8(s: string): Uint8Array { return new TextEncoder().encode(s) }
  static fromUtf8(b: Uint8Array): string { return new TextDecoder().decode(b) }
  static toBase64(b: Uint8Array): string { return globalThis.btoa(String.fromCharCode(...b)) }
  static fromBase64(s: string): Uint8Array {
    const bin = globalThis.atob(s)
    const out = new Uint8Array(bin.length)
    for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i)
    return out
  }
}
/// <reference path="../types/shims.d.ts" />
