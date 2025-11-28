import { PasswordManager } from '../common/PasswordManager'
import { SecureStore } from '../common/SecureStore'
import picker from '@ohos.file.picker'
import fs from '@ohos.file.fs'

@Entry
@Component
struct Index {
  @State master: string = ''
  @State site: string = ''
  @State user: string = ''
  @State pass: string = ''
  @State note: string = ''
  @State items: Array<{siteName:string,username:string,password:string,note:string}> = []
  @State query: string = ''
  @State multiSelect: boolean = false
  @State selection: Set<string> = new Set()
  @State isDark: boolean = false
  private pm: PasswordManager = new PasswordManager()
  private ss: SecureStore = new SecureStore()
  build() {
    Column() {
      Row() {
        TextField({ text: this.master, placeholder: '主密码', type: InputType.Password })
          .onChange(v => this.master = v)
        Button('暗色').onClick(() => { this.isDark = !this.isDark })
        Button('加载').onClick(async () => { await this.load() })
        Button('导入').onClick(async () => { await this.importFile() })
        Button('导出').onClick(async () => { await this.exportFile() })
        Button(this.multiSelect ? '删除已选' : '批量删除').onClick(() => { this.onBulkDelete() })
      }.padding(12)
      TextField({ text: this.query, placeholder: '搜索' }).onChange(v => this.query = v).padding(8)
      List() {
        ForEach(this.filtered(), (e) => {
          ListItem() {
            Column() {
              Row() {
                if (this.multiSelect) {
                  Checkbox({ value: this.selection.has(e.siteName + '|' + e.username) })
                    .onChange(v => { if (v) this.selection.add(e.siteName + '|' + e.username); else this.selection.delete(e.siteName + '|' + e.username) })
                }
                Column() {
                  Text(e.siteName).fontSize(18)
                  Text(e.username).fontSize(14)
                }
              }
            }
          }
        }, (e) => e.siteName + '|' + e.username)
      }
      Column() {
        TextField({ text: this.site, placeholder: '网站' }).onChange(v => this.site = v)
        TextField({ text: this.user, placeholder: '用户名' }).onChange(v => this.user = v)
        TextField({ text: this.pass, placeholder: '密码', type: InputType.Password }).onChange(v => this.pass = v)
        TextField({ text: this.note, placeholder: '备注' }).onChange(v => this.note = v)
        Button('保存').onClick(async () => { await this.save() })
      }.padding(12)
    }
  }
  filtered(): Array<{siteName:string,username:string,password:string,note:string}> {
    if (!this.query) return this.items
    const q = this.query.toLowerCase()
    return this.items.filter(e => e.siteName.toLowerCase().includes(q) || e.username.toLowerCase().includes(q))
  }
  async load() {
    if (!(await this.verifyMaster())) return
    const list = await this.pm.all(this.master)
    const res: Array<{siteName:string,username:string,password:string,note:string}> = []
    for (const s of list) {
      const arr = s.split('\n')
      if (arr.length >= 3) {
        const siteName = arr[0].replace('网站: ', '')
        const username = arr[1].replace('用户名: ', '')
        const password = arr[2].replace('密码: ', '')
        const noteVal = arr.length >= 4 ? arr[3].replace('备注: ', '') : ''
        res.push({ siteName, username, password, note: noteVal })
      }
    }
    this.items = res
  }
  async save() {
    if (!(await this.verifyMaster())) return
    await this.pm.save(this.master, { siteName: this.site, username: this.user, password: this.pass, note: this.note })
    await this.load()
  }
  onBulkDelete() {
    if (!this.multiSelect) { this.multiSelect = true; this.selection.clear(); return }
    this.selection.forEach(id => {
      const parts = id.split('|')
      const siteName = parts[0] || ''
      const username = parts[1] || ''
      this.pm.delete(siteName, username)
    })
    this.multiSelect = false
    this.load()
  }
  async exportFile() {
    if (!(await this.verifyMaster())) return
    const list = await this.pm.all(this.master)
    const arr: Array<Record<string, string>> = []
    for (const s of list) {
      const a = s.split('\n')
      if (a.length >= 3) {
        const siteName = a[0].replace('网站: ', '')
        const username = a[1].replace('用户名: ', '')
        const password = a[2].replace('密码: ', '')
        const noteVal = a.length >= 4 ? a[3].replace('备注: ', '') : ''
        arr.push({ siteName, username, password, note: noteVal })
      }
    }
    const json = JSON.stringify(arr)
    const cipher = await Crypto.encryptV2(json, this.master)
    const docPicker = new picker.DocumentViewPicker()
    const ret = await docPicker.save({ defaultFileName: 'vault_export.dat' })
    if (ret && ret.uri) {
      const fd = await fs.open(ret.uri, fs.OpenMode.WRITE_ONLY | fs.OpenMode.CREATE)
      await fs.write(fd.fd, Buffer.from(cipher, 'utf-8'))
      await fs.close(fd)
    }
  }
  async importFile() {
    if (!(await this.verifyMaster())) return
    const docPicker = new picker.DocumentViewPicker()
    const ret = await docPicker.select()
    if (ret && ret.uri) {
      const fd = await fs.open(ret.uri, fs.OpenMode.READ_ONLY)
      const stat = await fs.stat(ret.uri)
      const buf = new ArrayBuffer(stat.size)
      await fs.read(fd.fd, buf)
      await fs.close(fd)
      const s = String.fromCharCode(...new Uint8Array(buf))
      const plain = await Crypto.decryptAny(s, this.master)
      const arr = JSON.parse(plain)
      for (const o of arr) {
        await this.pm.save(this.master, { siteName: o.siteName || '', username: o.username || '', password: o.password || '', note: o.note || '' })
      }
      await this.load()
    }
  }
  async verifyMaster(): Promise<boolean> {
    const k = 'master_password_hash'
    const stored = await this.ss.get(k)
    if (stored) return this.javaHash(this.master) === stored
    await this.ss.set(k, this.javaHash(this.master))
    return true
  }
  javaHash(s: string): string {
    let h = 0
    for (let i = 0; i < s.length; i++) { h = (31 * h + s.charCodeAt(i)) | 0 }
    return String(h)
  }
}
/// <reference path="../types/shims.d.ts" />
