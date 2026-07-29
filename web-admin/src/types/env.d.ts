/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<{}, {}, any>
  export default component
}

declare module 'element-plus/dist/locale/zh-cn.mjs' {
  const zhCn: any
  export default zhCn
}

declare module 'qrcode' {
  const QRCode: {
    toCanvas(canvas: HTMLCanvasElement, text: string, options?: any): Promise<void>
    toDataURL(text: string, options?: any): Promise<string>
    toString(text: string, options?: any): Promise<string>
  }
  export = QRCode
}
