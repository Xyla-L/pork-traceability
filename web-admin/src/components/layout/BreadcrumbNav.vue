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
  const currentPath = route.path

  // 在菜单树中查找匹配当前路径的节点（支持前缀匹配，兼容 /pigs/1 详情路由）
  function findBestMatch(menu: MenuItem[], ancestors: MenuItem[]): MenuItem[] | null {
    let best: MenuItem[] | null = null
    for (const item of menu) {
      const chain = [...ancestors, item]
      if (currentPath.startsWith(item.path)) {
        best = chain
      }
      if (item.children) {
        const child = findBestMatch(item.children, chain)
        if (child) {
          // 子节点匹配更精确
          if (!best || child[child.length - 1].path.length > best[best.length - 1].path.length) {
            best = child
          }
        }
      }
    }
    return best
  }

  const chain = findBestMatch(menuConfig, [])
  const items: { title: string; path?: string }[] = []

  if (chain && chain.length >= 1) {
    // 所有祖先 + 当前节点
    for (let i = 0; i < chain.length; i++) {
      const item = chain[i]
      items.push({
        title: item.title,
        // 有子菜单的父级可点击，叶子节点不可点击
        path: item.children ? item.path : undefined,
      })
    }
    // 最后一个如果不是精确匹配（比如详情页），追加路由meta中的title
    const lastMatched = chain[chain.length - 1]
    if (lastMatched.path !== currentPath) {
      items.push({ title: (route.meta?.title as string) || currentPath })
    }
  }

  // 兜底
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
