<template>
  <div class="vaccine-table">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      style="width: 100%"
    >
      <el-table-column prop="earTagNo" label="关联生猪" min-width="140" show-overflow-tooltip />
      <el-table-column prop="vaccineName" label="疫苗名称" min-width="140" show-overflow-tooltip />
      <el-table-column prop="batchNo" label="疫苗批次号" min-width="140" show-overflow-tooltip />
      <el-table-column prop="injectTime" label="注射时间" min-width="160" align="center" />
      <el-table-column prop="dosage" label="剂量" min-width="100" align="center">
        <template #default="{ row }">
          {{ row.dosage ? `${row.dosage} ml` : '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="operator" label="操作人" min-width="100" show-overflow-tooltip />
      <el-table-column label="操作" width="160" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="handleViewCert(row)">
            查看凭证
          </el-button>
          <el-popconfirm
            title="确定删除该疫苗记录吗？"
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

    <!-- 凭证预览弹窗 -->
    <el-dialog
      v-model="certVisible"
      title="疫苗注射凭证"
      width="500px"
    >
      <div class="cert-preview">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="生猪耳标号">{{ currentCert.earTagNo }}</el-descriptions-item>
          <el-descriptions-item label="疫苗名称">{{ currentCert.vaccineName }}</el-descriptions-item>
          <el-descriptions-item label="疫苗批次号">{{ currentCert.batchNo }}</el-descriptions-item>
          <el-descriptions-item label="注射时间">{{ currentCert.injectTime }}</el-descriptions-item>
          <el-descriptions-item label="剂量">{{ currentCert.dosage }} ml</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ currentCert.operator }}</el-descriptions-item>
        </el-descriptions>
        <div v-if="currentCert.certPhoto" class="cert-photo">
          <p class="photo-label">凭证照片：</p>
          <el-image
            :src="currentCert.certPhoto"
            :preview-src-list="[currentCert.certPhoto]"
            fit="contain"
            style="max-width: 100%; max-height: 300px"
          />
        </div>
        <div v-else class="no-photo">
          暂无凭证照片
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'

defineProps({
  data: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  }
})

defineEmits(['delete'])

const certVisible = ref(false)
const currentCert = reactive({
  earTagNo: '',
  vaccineName: '',
  batchNo: '',
  injectTime: '',
  dosage: '',
  operator: '',
  certPhoto: ''
})

const handleViewCert = (row) => {
  Object.assign(currentCert, {
    earTagNo: row.earTagNo || '',
    vaccineName: row.vaccineName || '',
    batchNo: row.batchNo || '',
    injectTime: row.injectTime || '',
    dosage: row.dosage || '',
    operator: row.operator || '',
    certPhoto: row.certPhoto || ''
  })
  certVisible.value = true
}
</script>

<style lang="scss" scoped>
.vaccine-table {
  :deep(.el-table) {
    th.el-table__cell {
      background-color: #f5f7fa;
      color: #606266;
      font-weight: 600;
    }
  }
}

.cert-preview {
  .cert-photo {
    margin-top: 20px;

    .photo-label {
      margin-bottom: 10px;
      font-weight: 600;
      color: #606266;
    }
  }

  .no-photo {
    margin-top: 20px;
    text-align: center;
    color: #909399;
    padding: 30px 0;
    background-color: #f5f7fa;
    border-radius: 4px;
  }
}
</style>
