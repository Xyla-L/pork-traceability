<template>
  <div class="top-bar">
    <div class="left-section">
      <el-button type="text" @click="toggleSidebar"><el-icon><Menu /></el-icon></el-button>
      <div class="logo">
        <el-icon><Coin /></el-icon>
        <span>猪肉溯源管理平台</span>
      </div>
    </div>
    <div class="center-section">
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
          <span v-if="index === breadcrumbs.length - 1">{{ item.title }}</span>
          <a v-else @click="router.push(item.path)">{{ item.title }}</a>
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>
    <div class="right-section">
      <el-button type="text" @click="showNotifications">
        <el-icon><Bell /></el-icon>
        <span v-if="notifications.length" class="badge">{{ notifications.length }}</span>
      </el-button>
      <div class="user-info">
        <span class="username">{{ user?.realName || user?.username }}</span>
        <el-dropdown @command="handleCommand">
          <span class="el-dropdown-link">
            <el-icon><User /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="profile">个人中心</el-dropdown-item>
              <el-dropdown-item command="password">修改密码</el-dropdown-item>
              <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { Menu, Bell, User, Coin } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const appStore = useAppStore()

const user = computed(() => authStore.user)
const notifications = computed(() => appStore.notifications.filter(n => !n.read))

const breadcrumbs = computed(() => {
  const matched = route.matched.filter(r => r.meta.title)
  return matched.map(r => ({ title: r.meta.title, path: r.path }))
})

const toggleSidebar = () => appStore.toggleSidebar()

const showNotifications = () => {}

const handleCommand = (command: string) => {
  if (command === 'logout') { authStore.logout(); router.push('/login') }
  else if (command === 'profile') {}
  else if (command === 'password') {}
}
</script>

<style lang="scss" scoped>
.top-bar { display: flex; align-items: center; justify-content: space-between; height: 60px; padding: 0 20px; background-color: #fff; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.left-section { display: flex; align-items: center; gap: 16px; }
.logo { display: flex; align-items: center; gap: 8px; font-size: 18px; font-weight: 600; color: #1890ff; }
.center-section { flex: 1; }
.right-section { display: flex; align-items: center; gap: 20px; }
.badge { position: absolute; top: -4px; right: -4px; min-width: 16px; height: 16px; line-height: 16px; font-size: 12px; text-align: center; background-color: #f56c6c; color: #fff; border-radius: 8px; }
.user-info { display: flex; align-items: center; gap: 8px; }
.username { font-size: 14px; color: #606266; }
</style>