<template>
  <div class="sidebar-menu">
    <!-- Logo 区域 -->
    <div class="sidebar-logo">
      <span v-if="!collapsed" class="logo-text">🐷 猪肉溯源</span>
      <span v-else class="logo-icon">🐷</span>
    </div>

    <!-- 菜单列表 -->
    <el-scrollbar class="sidebar-scroll">
      <el-menu
        :default-active="activeMenu"
        :default-openeds="openedSubMenus"
        :collapse="collapsed"
        :collapse-transition="false"
        :unique-opened="true"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        router
      >
        <template v-for="item in visibleMenu" :key="item.path">
          <!-- 有子菜单 -->
          <el-sub-menu v-if="item.children && item.children.length > 0" :index="item.path">
            <template #title>
              <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </template>
            <el-menu-item
              v-for="child in item.children"
              :key="child.path"
              :index="child.path"
            >
              {{ child.title }}
            </el-menu-item>
          </el-sub-menu>

          <!-- 无子菜单 -->
          <el-menu-item v-else :index="item.path">
            <el-icon v-if="item.icon"><component :is="item.icon" /></el-icon>
            <template #title>{{ item.title }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { menuConfig, filterMenuByRole } from '@/config/menuConfig'
import type { RoleType } from '@/types/common'

defineProps<{
  collapsed: boolean
}>()

const route = useRoute()
const authStore = useAuthStore()

// 当前激活的菜单项 —— 匹配最长前缀，兼容 /farm/pigs/1 这种详情路由
const activeMenu = computed(() => {
  const allPaths = flattenMenuPaths(menuConfig)
  // 按路径长度降序排列，优先匹配更具体的路径
  const sorted = [...allPaths].sort((a, b) => b.length - a.length)
  const matched = sorted.find((p) => route.path.startsWith(p))
  return matched || route.path
})

// 需要自动展开的父菜单
const openedSubMenus = computed(() => {
  const parents: string[] = []
  for (const item of menuConfig) {
    if (item.children?.some((c) => route.path.startsWith(c.path))) {
      parents.push(item.path)
    }
  }
  return parents
})

/** 递归收集所有菜单项的 path（含 children） */
function flattenMenuPaths(items: typeof menuConfig): string[] {
  const paths: string[] = []
  for (const item of items) {
    paths.push(item.path)
    if (item.children) {
      paths.push(...flattenMenuPaths(item.children))
    }
  }
  return paths
}

// 根据角色过滤可见菜单
const visibleMenu = computed(() => {
  const role = authStore.role
  // 开发阶段：无角色时显示全部菜单（后续接入登录后删掉 !role 判断）
  if (!role || role === 'ADMIN') return menuConfig
  return filterMenuByRole(menuConfig, [role])
})
</script>

<style lang="scss" scoped>
.sidebar-menu {
  height: 100%;
  display: flex;
  flex-direction: column;

  .sidebar-logo {
    height: 56px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-bottom: 1px solid rgba(255, 255, 255, 0.08);

    .logo-text {
      color: #fff;
      font-size: 16px;
      font-weight: 600;
      white-space: nowrap;
    }

    .logo-icon {
      font-size: 24px;
    }
  }

  .sidebar-scroll {
    flex: 1;
    overflow-y: auto;

    :deep(.el-menu) {
      border-right: none;
    }

    :deep(.el-menu-item) {
      &.is-active {
        background-color: #263445 !important;
      }

      &:hover {
        background-color: #263445 !important;
      }
    }

    :deep(.el-sub-menu__title) {
      &:hover {
        background-color: #263445 !important;
      }
    }
  }
}
</style>
