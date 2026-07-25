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
      <el-table-column prop="farmId" label="养殖场" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          {{ farmNameMap[row.farmId] || row.farmId }}
        </template>
      </el-table-column>
      <el-table-column prop="birthDate" label="出生日期" min-width="120" align="center" />
      <el-table-column prop="status" label="状态" min-width="100" align="center">
        <template #default="{ row }">
          <StatusTag :type="pigStatusType(row.status)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="$emit('view', row)">
            查看详情
          </el-button>
          <el-button type="warning" link size="small" @click="$emit('edit', row)">
            编辑
          </el-button>
          <el-popconfirm
            title="确定删除该生猪档案吗？"
            confirm-button-text="确定"
            cancel-button-text="取消"
            @confirm="$emit('delete', row)"
          >
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
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

defineEmits(['view', 'edit', 'delete'])

const breedMap = {
  'changbai': '长白猪',
  'dabai': '大白猪',
  'du洛克': '杜洛克',
  'dulock': '杜洛克',
  'pitelan': '皮特兰'
}

const pigStatusType = (status) => {
  const map = { 1: 'raising', 2: 'transport', 3: 'slaughtered', 4: 'abnormal' }
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
