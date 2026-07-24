<template>
  <div class="sidebar" :class="{ collapsed: isCollapsed }">
    <div class="sidebar-header">
      <div class="logo">
        <el-icon><PiggyBank /></el-icon>
        <span v-if="!isCollapsed">猪肉溯源</span>
      </div>
    </div>
    <el-scrollbar class="menu-scroll">
      <el-menu :default-active="activeMenu" :collapse="isCollapsed" :collapse-transition="false" mode="vertical" background-color="#001529" text-color="#bfcbd9" active-text-color="#409eff">
        <template v-for="item in menuList" :key="item.path">
          <el-sub-menu v-if="item.children" :index="item.path">
            <template #title>
              <el-icon :size="20"><component :is="getIcon(item.icon)" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item v-for="child in item.children" :key="child.path" :index="child.path" @click="router.push(child.path)">
              <span>{{ child.title }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="item.path" @click="router.push(item.path)">
            <el-icon :size="20"><component :is="getIcon(item.icon)" /></el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>
    <div class="sidebar-footer" @click="toggleSidebar">
      <el-icon><component :is="isCollapsed ? Expand : Fold" /></el-icon>
    </div>
  </div>
</template>

<script setup>
import { computed, markRaw } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { PiggyBank, Monitor, User, Syringe, ClipboardCheck, DoorOpen, Knife, FlaskConical, Stamp, Package, Receiving, QrCode, ShoppingCart, AlertTriangle, RotateCcw, MessageWarning, UserFilled, Building, FileSearch, Fold, Expand } from '@element-plus/icons-vue'
import { useAppStore } from '@/stores/app'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const route = useRoute()
const appStore = useAppStore()
const authStore = useAuthStore()

const isCollapsed = computed(() => appStore.sidebarCollapsed)

const activeMenu = computed(() => route.path)

const iconMap: Record<string, any> = { Monitor: markRaw(Monitor), User: markRaw(User), Syringe: markRaw(Syringe), ClipboardCheck: markRaw(ClipboardCheck), DoorOpen: markRaw(DoorOpen), Knife: markRaw(Knife), FlaskConical: markRaw(FlaskConical), Stamp: markRaw(Stamp), Package: markRaw(Package), Receiving: markRaw(Receiving), QrCode: markRaw(QrCode), ShoppingCart: markRaw(ShoppingCart), AlertTriangle: markRaw(AlertTriangle), RotateCcw: markRaw(RotateCcw), MessageWarning: markRaw(MessageWarning), UserFilled: markRaw(UserFilled), Building: markRaw(Building), FileSearch: markRaw(FileSearch) }

const getIcon = (iconName: string) => iconMap[iconName] || Monitor

const menuList = computed(() => {
  const userRole = authStore.role || '*'
  return [
    { path: '/admin/dashboard', title: '工作台', icon: 'Monitor', roles: ['*'] },
    { path: '/admin/farm', title: '养殖管理', icon: 'User', roles: ['FARMER', 'SUPERVISOR', 'ADMIN'], children: [{ path: '/admin/farm/pigs', title: '生猪档案', roles: ['*'] }, { path: '/admin/farm/vaccines', title: '疫苗记录', roles: ['FARMER', 'SUPERVISOR', 'ADMIN'] }, { path: '/admin/farm/apply', title: '出栏审批', roles: ['SUPERVISOR', 'ADMIN'] }] },
    { path: '/admin/slaughter', title: '屠宰管理', icon: 'Knife', roles: ['*'], children: [{ path: '/admin/slaughter/entry', title: '入场查验', roles: ['*'] }, { path: '/admin/slaughter/inspect', title: '屠宰检验', roles: ['*'] }, { path: '/admin/slaughter/ractopamine', title: '瘦肉精检测', roles: ['*'] }, { path: '/admin/slaughter/stamp', title: '检疫盖章', roles: ['*'] }] },
    { path: '/admin/distribution', title: '分割配送', icon: 'Package', roles: ['*'], children: [{ path: '/admin/distribution/batch', title: '胴体批次', roles: ['*'] }, { path: '/admin/distribution/receipt', title: '门店签收', roles: ['*'] }] },
    { path: '/admin/sales', title: '销售管理', icon: 'ShoppingCart', roles: ['*'], children: [{ path: '/admin/sales/qrcode', title: '二维码管理', roles: ['*'] }, { path: '/admin/sales/records', title: '销售记录', roles: ['*'] }, { path: '/admin/sales/warnings', title: '过期预警', roles: ['*'] }, { path: '/admin/sales/recall', title: '产品召回', roles: ['*'] }] },
    { path: '/admin/trace', title: '应急追溯', icon: 'MessageWarning', roles: ['*'], children: [{ path: '/admin/trace/complaints', title: '举报管理', roles: ['*'] }] },
    { path: '/admin/system', title: '系统管理', icon: 'FileSearch', roles: ['ADMIN'], children: [{ path: '/admin/system/users', title: '用户管理', roles: ['ADMIN'] }, { path: '/admin/system/orgs', title: '机构管理', roles: ['ADMIN'] }, { path: '/admin/system/audit', title: '审计日志', roles: ['ADMIN'] }] }
  ].filter(menu => {
    if (menu.roles.includes('*')) return true
    return menu.roles.includes(userRole)
  }).map(menu => {
    if (menu.children) {
      return { ...menu, children: menu.children.filter(child => child.roles.includes('*') || child.roles.includes(userRole)) }
    }
    return menu
  }).filter(menu => !menu.children || menu.children.length > 0)
})

const toggleSidebar = () => appStore.toggleSidebar()
</script>

<style lang="scss" scoped>
.sidebar { width: 200px; background-color: #001529; transition: width 0.3s; display: flex; flex-direction: column; height: 100vh; position: fixed; left: 0; top: 0; z-index: 100; &.collapsed { width: 64px; } }
.sidebar-header { padding: 20px; border-bottom: 1px solid #001529; }
.logo { display: flex; align-items: center; justify-content: center; gap: 8px; color: #fff; font-size: 16px; font-weight: 600; }
.menu-scroll { flex: 1; overflow-y: auto; }
.sidebar-footer { padding: 16px; text-align: center; color: #bfcbd9; cursor: pointer; transition: color 0.2s; &:hover { color: #fff; } }
</style>