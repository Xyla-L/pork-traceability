<template>
  <el-dialog
    v-model="visible"
    :title="`产地检疫出证 · ${pig?.earTagNo || ''}`"
    width="580px"
    destroy-on-close
  >
    <div v-loading="loading" class="cert-body">
      <!-- 已有有效证 -->
      <template v-if="cert">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="检疫证编号">{{ cert.certNo }}</el-descriptions-item>
          <el-descriptions-item label="签发机构">{{ cert.issueOrg || '--' }}</el-descriptions-item>
          <el-descriptions-item label="签发时间">{{ cert.issueTime || '--' }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">
            <span :class="{ 'cert-expired': expired }">{{ cert.validUntil || '--' }}</span>
            <el-tag v-if="expired" type="danger" size="small" effect="plain" style="margin-left: 8px">
              已过期，入场查验核验不过
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="官方兽医">{{ cert.inspector || '--' }}</el-descriptions-item>
          <el-descriptions-item label="内容哈希">
            <span class="hash-cell">{{ cert.contentHash || '--' }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-alert
          class="cert-sub"
          type="warning"
          :closable="false"
          show-icon
          title="一头猪同一时间只能有一张有效证"
          description="如需换发，请先作废当前证再重新出具——作废与重出会各自上链，链上留下的是一条作废 + 一条新证，责任清楚，而不是把同一张证悄悄改掉。"
        />
      </template>

      <!-- 无证：出具 -->
      <el-form v-else ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="耳标号">
          <span>{{ pig?.earTagNo || '--' }}</span>
        </el-form-item>
        <el-form-item label="检疫证编号" prop="certNo">
          <el-input v-model="form.certNo" placeholder="如 QC-20261004-01" />
        </el-form-item>
        <el-form-item label="签发机构" prop="issueOrg">
          <el-input v-model="form.issueOrg" placeholder="如 顺义区动物卫生监督所" />
        </el-form-item>
        <el-form-item label="签发时间" prop="issueTime">
          <el-date-picker
            v-model="form.issueTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="选择签发时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="有效期至" prop="validUntil">
          <el-date-picker
            v-model="form.validUntil"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择有效期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="官方兽医" prop="inspector">
          <el-input v-model="form.inspector" placeholder="留空则记为当前登录人" />
        </el-form-item>
        <div class="cert-tip-line">
          演示环境：检疫证扫描件与 CA 签名自动占位；正式环境应接入扫描件上传与官方 CA 签名。
        </div>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button v-if="cert" type="danger" :loading="voiding" @click="handleVoid">作废</el-button>
      <el-button v-else type="primary" :loading="submitting" @click="handleIssue">出证并上链</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { pigApi } from '@/api/modules/breeding'
import { confirmVoid } from '@/composables/useVoidAction'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  pig: { type: Object, default: null },
})
const emit = defineEmits(['update:modelValue', 'changed'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const loading = ref(false)
const submitting = ref(false)
const voiding = ref(false)
const cert = ref(null)
const formRef = ref()

const form = reactive({
  certNo: '',
  issueOrg: '',
  issueTime: '',
  validUntil: '',
  inspector: '',
})

const todayStart = () => {
  const d = new Date()
  d.setHours(0, 0, 0, 0)
  return d
}

const expired = computed(() => {
  const v = cert.value?.validUntil
  if (!v) return false
  return new Date(String(v).slice(0, 10)) < todayStart()
})

const rules = {
  certNo: [{ required: true, message: '请填写检疫证编号', trigger: 'blur' }],
  issueOrg: [{ required: true, message: '请填写签发机构', trigger: 'blur' }],
  issueTime: [{ required: true, message: '请选择签发时间', trigger: 'change' }],
  validUntil: [
    {
      validator: (_rule, value, callback) => {
        if (!value) return callback()
        if (new Date(String(value).slice(0, 10)) < todayStart()) {
          return callback(new Error('有效期不能早于今天，否则入场查验会直接判为无效'))
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

const pad = (n) => String(n).padStart(2, '0')

function resetForm() {
  const d = new Date()
  const today = `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}`
  form.certNo = `QC-${today}-${String(Math.floor(Math.random() * 900) + 100)}`
  form.issueOrg = '顺义区动物卫生监督所'
  form.issueTime = `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  const valid = new Date(d.getTime() + 180 * 24 * 3600 * 1000)
  form.validUntil = `${valid.getFullYear()}-${pad(valid.getMonth() + 1)}-${pad(valid.getDate())}`
  form.inspector = ''
  form.unlockReason = ''
}

async function loadCert() {
  cert.value = null
  if (!props.pig?.id) return
  loading.value = true
  try {
    cert.value = await pigApi.getQuarantineCert(props.pig.id)
  } catch {
    // 无证时后端返回业务异常，属正常态——按「未出证」渲染
    cert.value = null
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (!open) return
    resetForm()
    refreshTicket()
    loadCert()
  },
)

async function handleIssue() {
  if (!formRef.value || !props.pig?.id) return
  const ok = await formRef.value.validate().catch(() => false)
  if (!ok) return

  submitting.value = true
  try {
    await pigApi.uploadQuarantineCert(props.pig.id, {
      certNo: form.certNo.trim(),
      issueOrg: form.issueOrg.trim(),
      issueTime: form.issueTime,
      validUntil: form.validUntil || undefined,
      inspector: form.inspector.trim() || undefined,
      // 演示占位：正式环境应由扫描件上传 + CA 签名提供
      fileId: `CERT-SCAN-${form.certNo.trim()}`,
      caSignature: `CA-SIG-${form.certNo.trim()}`,
    })
    ElMessage.success('产地检疫证已出具，内容哈希已上链')
    emit('changed')
    visible.value = false
  } catch (e) {
    ElMessage.error(e?.message || '出证失败')
  } finally {
    submitting.value = false
  }
}

async function handleVoid() {
  if (!cert.value) return
  const target = cert.value
  const done = await confirmVoid({
    title: '作废产地检疫证',
    message: `确认作废检疫证 ${target.certNo} 吗？作废后入场查验将无法再核到该证，如需调运须重新出证。`,
    onConfirm: async (reason) => {
      await pigApi.voidQuarantineCert(props.pig.id, target.id, reason)
    },
  })
  if (done) {
    emit('changed')
    loadCert()
  }
}

function refresh() {
  resetForm()
  refreshTicket()
  loadCert()
}

defineExpose({ refresh })
</script>

<style lang="scss" scoped>
.cert-sub {
  margin-top: 12px;
}

.cert-body {
  min-height: 80px;
}

.cert-expired {
  color: #f56c6c;
  font-weight: 600;
}

.hash-cell {
  font-family: monospace;
  word-break: break-all;
  color: #909399;
  font-size: 12px;
}

.cert-tip-line {
  margin-top: 4px;
  padding-left: 110px;
  color: #c0c4cc;
  font-size: 12px;
  line-height: 1.5;
}
</style>
