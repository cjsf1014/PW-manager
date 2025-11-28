declare module '@ohos.data.preferences' {
  export function getPreferences(ctx: any, name: string): Promise<Preferences>
  export interface Preferences {
    put(key: string, value: any): Promise<void>
    get(key: string, defaultValue: any): Promise<any>
    getAll(): Promise<Record<string, any>>
    delete(key: string): Promise<void>
    flush(): Promise<void>
  }
}

declare module '@ohos.file.picker' {
  export class DocumentViewPicker {
    select(options?: any): Promise<{ uri: string }>
    save(options?: { defaultFileName?: string }): Promise<{ uri: string }>
  }
}

declare module '@ohos.file.fs' {
  export const OpenMode: { READ_ONLY: number; WRITE_ONLY: number; CREATE: number }
  export function open(uri: string, mode: number): Promise<{ fd: number }>
  export function read(fd: number, buffer: ArrayBuffer): Promise<number>
  export function write(fd: number, buf: Uint8Array): Promise<number>
  export function close(fd: any): Promise<void>
  export function stat(uri: string): Promise<{ size: number }>
}

// Intentionally no global declarations to avoid conflicts with lib.dom.d.ts
