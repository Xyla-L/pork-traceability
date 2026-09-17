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
      <el-table-column prop="birthDate" label="出生日期" min-width="120" align="center" />
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
      <el-table-column label="操作" width="160" fixed="right" align="center">
        <template #default="{ row }">
          <el-button v-if="row.status === 1" type="success" link size="small" @click="$emit('apply', row)">
            申请出栏
          </el-button>
          <el-button type="primary" link size="small" @click="$emit('edit', row)">编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import StatusTag from '@/components/common/StatusTag.vue'

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

defineEmits(['apply', 'edit'])

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
  :deep(.el-table) {
    th.el-table__cell {
      background-color: #f5f7fa;
      color: #606266;
      font-weight: 600;
    }
  }
}
</style>
