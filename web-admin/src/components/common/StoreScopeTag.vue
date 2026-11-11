<template>
  <el-tag v-if="scopeName" type="warning" effect="plain" size="small" class="store-scope-tag">
    当前仅显示本门店：{{ scopeName }}
  </el-tag>
</template>

<script setup lang="ts">
/**
 * 门店数据边界的落点。
 * <p>
 * 零售商看到的是被服务端收敛过的数据（只含自己门店），列表因此比管理员短很多。
 * 页面上如果不说明这一点，用户会以为「数据丢了」或「筛选坏了」。
 * 这里只陈述事实（当前范围是哪家店），不是解释设计理由的横幅。
 */
import { computed } from 'vue'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const scopeName = computed(() =>
  authStore.role === 'RETAILER' ? authStore.user?.orgName || '' : ''
)
</script>

<style scoped>
.store-scope-tag {
  margin-right: 8px;
}
</style>
