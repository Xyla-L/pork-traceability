import type { App } from 'vue'
import { useAuthStore } from '@/stores/auth'

/**
 * v-permission 按钮权限指令
 *
 * 用法:
 *   <el-button v-permission="'slaughter:inspect:create'">录入检验</el-button>
 *   <el-button v-permission="['admin:user:create', 'admin:user:edit']">操作</el-button>
 */
const permissionDirective = {
  mounted(el: HTMLElement, binding: any) {
    const { value } = binding
    if (!value) return

    const authStore = useAuthStore()
    const permissions = typeof value === 'string' ? [value] : value

    // 只要拥有任意一个权限即显示
    const hasPermission = permissions.some((perm: string) =>
      authStore.hasPermission(perm)
    )

    if (!hasPermission) {
      el.parentNode?.removeChild(el)
    }
  },
}

export function setupPermissionDirective(app: App) {
  app.directive('permission', permissionDirective)
}

export default permissionDirective
