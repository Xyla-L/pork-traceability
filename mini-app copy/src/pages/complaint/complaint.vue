<template>
  <view class="page">
    <view class="card">
      <view class="section-title">问题产品信息</view>
      <view class="info-row">
        <text class="info-label">二维码</text>
        <text class="info-value">{{ qrCode || '--' }}</text>
      </view>
      <view class="info-row">
        <text class="info-label">批次号</text>
        <text class="info-value">{{ batchNo || '--' }}</text>
      </view>
    </view>

    <view class="card">
      <view class="section-title">问题描述</view>
      <textarea
        v-model="form.complaintText"
        class="complaint-textarea"
        placeholder="请详细描述您遇到的问题..."
        maxlength="500"
      />
      <view class="char-count">{{ form.complaintText.length }}/500</view>
    </view>

    <view class="card">
      <view class="section-title">上传照片（选填）</view>
      <PhotoUpload ref="photoRef" @change="onPhotosChange" />
    </view>

    <view class="card">
      <view class="section-title">联系方式（选填，便于反馈）</view>
      <view class="contact-row">
        <input v-model="form.reporterName" class="contact-input" type="text" placeholder="姓名" />
      </view>
      <view class="contact-row">
        <input v-model="form.reporterPhone" class="contact-input" type="number" placeholder="手机号" />
      </view>
    </view>

    <button class="submit-btn" :loading="submitting" @click="handleSubmit">提交举报</button>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { useComplaintStore } from '@/stores/complaint'
import PhotoUpload from '@/components/PhotoUpload.vue'

const complaintStore = useComplaintStore()
const photoRef = ref()
const submitting = ref(false)
const qrCode = ref('')
const batchNo = ref('')

const form = reactive({
  targetQrCode: '',
  targetBatch: '',
  complaintText: '',
  reporterName: '',
  reporterPhone: '',
  fileIds: [],
})

onLoad((options) => {
  qrCode.value = decodeURIComponent(options.qrCode || '')
  batchNo.value = decodeURIComponent(options.batchNo || '')
  form.targetQrCode = qrCode.value
  form.targetBatch = batchNo.value
})

function onPhotosChange(photos) {
  // 实际提交时需先 uploadFile 拿到 fileId；Mock 阶段直接存本地路径
  form.fileIds = photos
}

async function handleSubmit() {
  if (!form.complaintText.trim()) {
    uni.showToast({ title: '请填写问题描述', icon: 'none' })
    return
  }

  uni.showModal({
    title: '确认提交',
    content: '确定要提交该举报吗？',
    confirmText: '确定',
    cancelText: '取消',
    success: async (res) => {
      if (!res.confirm) return
      submitting.value = true
      try {
        await complaintStore.submit({ ...form })
        uni.showToast({ title: '举报提交成功', icon: 'success' })
        setTimeout(() => {
          uni.redirectTo({ url: '/pages/complaint-list/complaint-list' })
        }, 1200)
      } finally {
        submitting.value = false
      }
    },
  })
}
</script>

<style lang="scss" scoped>
.page {
  padding: 24rpx;
}

.info-row {
  display: flex;
  gap: 20rpx;
  padding: 12rpx 0;

  .info-label {
    flex: 0 0 120rpx;
    color: #909399;
    font-size: 26rpx;
  }

  .info-value {
    flex: 1;
    color: #303133;
    font-size: 26rpx;
    word-break: break-all;
  }
}

.complaint-textarea {
  width: 100%;
  height: 240rpx;
  background: #f5f7fa;
  border-radius: 12rpx;
  padding: 20rpx;
  font-size: 28rpx;
}

.char-count {
  text-align: right;
  font-size: 22rpx;
  color: #c0c4cc;
  margin-top: 8rpx;
}

.contact-row {
  margin-bottom: 16rpx;

  &:last-child {
    margin-bottom: 0;
  }

  .contact-input {
    height: 72rpx;
    background: #f5f7fa;
    border-radius: 12rpx;
    padding: 0 24rpx;
    font-size: 28rpx;
  }
}

.submit-btn {
  margin-top: 24rpx;
  height: 88rpx;
  line-height: 88rpx;
  background: #f56c6c;
  color: #fff;
  font-size: 30rpx;
  font-weight: 600;
  border-radius: 16rpx;

  &::after {
    border: none;
  }
}
</style>
