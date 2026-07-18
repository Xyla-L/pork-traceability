<template>
  <div class="topbar">
    <!-- 左侧：折叠按钮 + 面包屑 -->
    <div class="topbar-left">
      <el-icon class="collapse-btn" @click="$emit('toggleSidebar')">
        <Fold v-if="!appStore.sidebarCollapsed" />
        <Expand v-else />
      </el-icon>
      <BreadcrumbNav />
    </div>

    <!-- 右侧：通知 + 用户 -->
    <div class="topbar-right">
      <!-- 通知铃铛 -->
      <el-popover placement="bottom" :width="320" trigger="click">
        <template #reference>
          <el-badge :value="unreadCount" :max="99" :hidden="unreadCount === 0">
            <el-icon class="topbar-icon"><Bell /></el-icon>
          </el-badge>
        </template>
        <div class="notification-list">
          <div v-if="notifications.length === 0" class="empty-notify">
            暂无新通知
          </div>
          <div
            v-for="item in notifications"
            :key="item.id"
            class="notify-item"
            :class="{ unread: !item.read }"
            @click="handleNotificationClick(item)"
          >
            <div class="notify-title">{{ item.title }}</div>
            <div class="notify-content">{{ item.content }}</div>
            <div class="notify-time">{{ item.createTime }}</div>
          </div>
        </div>
      </el-popover>

      <!-- 用户下拉 -->
      <el-dropdown trigger="click" @command="handleCommand">
        <span class="user-dropdown">
          <el-avatar :size="32" icon="UserFilled" />
          <span class="username">{{ authStore.user?.realName || authStore.user?.username || '未登录' }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>个人信息
            </el-dropdown-item>
            <el-dropdown-item command="password">
              <el-icon><Lock /></el-icon>修改密码
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import BreadcrumbNav from './BreadcrumbNav.vue'

defineEmits<{
  toggleSidebar: []
}>()

const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()

const notifications = computed(() => appStore.notifications)
const unreadCount = computed(
  () => notifications.value.filter((n) => !n.read).length
)

onMounted(() => {
  appStore.fetchNotifications()
})

function handleNotificationClick(item: any) {
  appStore.markNotificationRead(item.id)
  // TODO: 跳转到对应的业务页面
}

async function handleCommand(command: string) {
  switch (command) {
    case 'profile':
      // TODO: 跳转个人信息页
      break
    case 'password':
      // TODO: 打开修改密码弹窗
      break
    case 'logout':
      try {
        await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning',
        })
        await authStore.logout()
        router.push('/login')
      } catch {
        // 取消
      }
      break
  }
}
</script>

<style lang="scss" scoped>
.topbar {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;

  .topbar-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .collapse-btn {
    font-size: 20px;
    cursor: pointer;
    color: #606266;
    transition: color 0.2s;

    &:hover {
      color: #409eff;
    }
  }

  .topbar-right {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  .topbar-icon {
    font-size: 20px;
    color: #606266;
    cursor: pointer;

    &:hover {
      color: #409eff;
    }
  }

  .user-dropdown {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;

    .username {
      font-size: 14px;
      color: #303133;
      max-width: 100px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
}

// 通知列表样式
.notification-list {
  max-height: 320px;
  overflow-y: auto;

  .empty-notify {
    text-align: center;
    color: #909399;
    padding: 20px;
  }

  .notify-item {
    padding: 10px 12px;
    border-bottom: 1px solid #f0f0f0;
    cursor: pointer;
    transition: background 0.2s;

    &:hover {
      background: #f5f7fa;
    }

    &.unread {
      background: #ecf5ff;

      .notify-title {
        font-weight: 600;
      }
    }

    .notify-title {
      font-size: 13px;
      color: #303133;
    }

    .notify-content {
      font-size: 12px;
      color: #909399;
      margin-top: 4px;
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
    }

    .notify-time {
      font-size: 11px;
      color: #c0c4cc;
      margin-top: 4px;
    }
  }
}
</style>
