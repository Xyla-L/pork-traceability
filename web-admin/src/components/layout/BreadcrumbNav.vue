<template>
  <el-breadcrumb separator="/">
    <el-breadcrumb-item :to="{ path: '/admin/dashboard' }">工作台</el-breadcrumb-item>
    <el-breadcrumb-item
      v-for="item in breadcrumbItems"
      :key="item.path"
      :to="item.path ? { path: item.path } : undefined"
    >
      {{ item.title }}
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { menuConfig } from '@/config/menuConfig'
import type { MenuItem } from '@/types/common'

const route = useRoute()

/**
 * 根据当前路由路径，从菜单配置中查找对应的面包屑路径
 */
const breadcrumbItems = computed(() => {
  const items: { title: string; path?: string }[] = []
  const currentPath = route.path

  function findPath(menu: MenuItem[], ancestors: MenuItem[]): MenuItem[] | null {
    for (const item of menu) {
      const chain = [...ancestors, item]
      if (item.path === currentPath) return chain
      if (item.children) {
        const found = findPath(item.children, chain)
        if (found) return found
      }
    }
    return null
  }

  const chain = findPath(menuConfig, [])
  if (chain && chain.length > 1) {
    // 去掉最后一个元素（当前页），只留祖先
    chain.pop()
    for (const item of chain) {
      // 只有有子菜单的父级才可点击
      items.push({
        title: item.title,
        path: item.children ? item.path : undefined,
      })
    }
    // 加上当前页面名（不可点击）
    const currentItem = chain[chain.length] || route.meta?.title
    // 用路由meta中的title
  }

  // 兜底：用路由 meta.title
  if (items.length === 0 && route.meta?.title) {
    items.push({ title: route.meta.title as string })
  }

  return items
})
</script>

<style lang="scss" scoped>
:deep(.el-breadcrumb__inner) {
  font-size: 13px;
}
</style>
