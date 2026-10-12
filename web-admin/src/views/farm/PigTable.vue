<template>
  <div class="pig-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="earTagNo" label="耳标号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="breed" label="品种" min-width="100" align="center">
        <template #default="{ row }">
          {{ breedLabel(row.breed) }}
        </template>
      </el-table-column>
      <el-table-column prop="penNo" label="圈舍号" min-width="100" align="center" />
      <el-table-column prop="farmId" label="养殖场" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ farmNameMap[row.farmId] || row.farmId }}
        </template>
      </el-table-column>
      <el-table-column prop="birthDate" label="出生日期" min-width="120" align="center">
        <template #default="{ row }">
          <!-- 设备建档只读得到耳标，拿不到出生日期，故该列可能为空，显示占位符而非留白 -->
          <span v-if="row.birthDate">{{ row.birthDate }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column prop="gender" label="性别" min-width="80" align="center">
        <template #default="{ row }">
          {{ row.gender === 2 ? '母' : '公' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <StatusTag :type="pigStatusType(row.status)" />
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="采集时间" min-width="170" align="center">
        <template #default="{ row }">
          <span v-if="row.createTime">{{ formatTime(row.createTime) }}</span>
          <span v-else class="empty-cell">--</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" type="success" link size="small" @click="$emit('apply', row)">
            申请出栏
          </el-button>
          <el-button type="danger" link size="small" @click="handleVoid(row)">作废</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import StatusTag from '@/components/common/StatusTag.vue'
import { pigApi } from '@/api/modules/breeding'
import { confirmVoid } from '@/composables/useVoidAction'

defineProps({
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  farmNameMap: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['apply', 'voided'])

// 作废：生猪档案是追溯链的起点，删掉一条档案，以它为前提的下游事件全成悬空哈希，因此只允许作废
const handleVoid = async (row) => {
  const ok = await confirmVoid({
    title: '作废生猪档案',
    message: `确认作废耳标 ${row.earTagNo || ''} 的养殖档案吗？作废后该档案不再生效，但仍保留在库中并可追溯。`,
    onConfirm: (reason) => pigApi.voidPig(row.id, reason),
  })
  if (ok) emit('voided')
}

/**
 * 采集时间：设备上报后即写入档案，所以建档时刻就是采集时刻；人工录入则为录入时刻。
 * 后端返回的是 ISO 格式（2026-10-05T17:33:38），界面按熟悉的写法展示到秒。
 */
const formatTime = (value) => String(value).replace('T', ' ').slice(0, 19)

const breedMap = {
  'changbai': '长白猪',
  'dabai': '大白猪',
  'duroc': '杜洛克',
  'dulock': '杜洛克',
  'pitelan': '皮特兰'
}

const pigStatusType = (status) => {
  // 生猪状态：1在养 2已出栏 3已屠宰 4异常死亡/淘汰（对应 breeding 枚举）
  const map = { 1: 'raising', 2: 'soldOut', 3: 'slaughtered', 4: 'abnormal' }
  return map[status] || ''
}

const breedLabel = (breed) => {
  if (!breed) return '-'
  return breedMap[breed] || breed
}
</script>

<style lang="scss" scoped>
.pig-table {
  .empty-cell {
    color: #c0c4cc;
  }

  :deep(.el-table) {
    th.el-table__cell {
      background-color: #f5f7fa;
      color: #606266;
      font-weight: 600;
    }
  }
}
</style>
