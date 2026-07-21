<template>
  <el-container class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapsed ? '64px' : '220px'" class="admin-aside">
      <SidebarMenu :collapsed="isCollapsed" />
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container class="admin-main">
      <!-- 顶栏 -->
      <el-header height="56px" class="admin-header">
        <TopBar @toggle-sidebar="toggleSidebar" />
      </el-header>

      <!-- 内容区 -->
      <el-main class="admin-content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useAppStore } from '@/stores/app'
import SidebarMenu from './SidebarMenu.vue'
import TopBar from './TopBar.vue'

const appStore = useAppStore()
const isCollapsed = computed(() => appStore.sidebarCollapsed)

function toggleSidebar() {
  appStore.toggleSidebar()
}
</script>

<style lang="scss" scoped>
.admin-layout {
  width: 100%;
  height: 100vh;

  .admin-aside {
    background-color: #304156;
    transition: width 0.3s ease;
    overflow: hidden;
    flex-shrink: 0;
  }

  .admin-main {
    flex-direction: column;
    min-width: 0;
  }

  .admin-header {
    background: #fff;
    border-bottom: 1px solid #e4e7ed;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
    padding: 0 20px;
    display: flex;
    align-items: center;
    flex-shrink: 0;
    z-index: 10;
  }

  .admin-content {
    background: #f0f2f5;
    padding: 20px;
    overflow-y: auto;
    flex: 1;
  }
}

// 页面切换过渡动画
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
