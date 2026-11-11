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

    <!-- 右侧：助手 + 通知 + 用户 -->
    <div class="topbar-right">
      <!-- 智能助手：全局入口。落点选在这里而不是工作台，是因为它回答的是「随手一问」，
           不该占掉工作台按角色分好的版面。现阶段只对管理员显示（角色表见 utils/agentAccess） -->
      <el-tooltip v-if="canUseAgent" content="智能助手（只读问答）" placement="bottom">
        <el-icon class="topbar-icon" @click="agentVisible = true"><ChatDotRound /></el-icon>
      </el-tooltip>

      <!-- 通知铃铛 -->
      <el-popover v-model:visible="notifyVisible" placement="bottom" :width="320" trigger="click">
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

    <!-- 修改密码弹窗 -->
    <ChangePasswordDialog v-model="changePwdVisible" />

    <!-- 智能助手抽屉 -->
    <AgentAssistant v-if="canUseAgent" v-model="agentVisible" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useAppStore } from '@/stores/app'
import { resolveNotificationTarget } from '@/utils/notificationLink'
import { agentEnabled } from '@/utils/agentAccess'
import type { AppNotification } from '@/types/notification'
import BreadcrumbNav from './BreadcrumbNav.vue'
import ChangePasswordDialog from '@/components/common/ChangePasswordDialog.vue'
import AgentAssistant from '@/components/common/AgentAssistant.vue'

defineEmits<{
  toggleSidebar: []
}>()

const router = useRouter()
const authStore = useAuthStore()
const appStore = useAppStore()
const changePwdVisible = ref(false)
const notifyVisible = ref(false)
const agentVisible = ref(false)
// 入口按角色显示：后端对非管理员一律 403，这里只是不让人看到一个点开就报错的图标
const canUseAgent = computed(() => agentEnabled(authStore.role))

const notifications = computed(() => appStore.notifications)
const unreadCount = computed(
  () => notifications.value.filter((n) => !n.read).length
)

onMounted(() => {
  appStore.fetchNotifications()
})

/**
 * 点通知：先标已读（本地乐观 + 后台同步），再跳到这条通知指向的业务页面。
 * 跳哪一页由 bizType 决定；能按编号搜的页面顺带把编号带上，落地就是那条记录，
 * 而不是让人在列表里自己翻。
 */
async function handleNotificationClick(item: AppNotification) {
  appStore.markNotificationRead(item.id)
  const target = resolveNotificationTarget(item)
  notifyVisible.value = false
  if (!target) return
  await router.push({ path: target.path, query: target.query })
}

async function handleCommand(command: string) {
  switch (command) {
    case 'profile':
      router.push('/admin/system/profile')
      break
    case 'password':
      changePwdVisible.value = true
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
