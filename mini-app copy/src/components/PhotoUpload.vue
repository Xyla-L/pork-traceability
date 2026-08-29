<template>
  <view class="photo-upload">
    <view class="pu-list">
      <view v-for="(photo, idx) in photos" :key="idx" class="pu-item">
        <image class="pu-img" :src="photo" mode="aspectFill" @click="preview(idx)" />
        <view class="pu-del" @click="remove(idx)">✕</view>
      </view>

      <view v-if="photos.length < max" class="pu-add" @click="choose">
        <text class="pu-add-icon">＋</text>
        <text class="pu-add-text">添加图片</text>
      </view>
    </view>
    <view class="pu-tip">最多上传 {{ max }} 张（当前 {{ photos.length }} 张）</view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  max: {
    type: Number,
    default: 9,
  },
})

const emit = defineEmits(['change'])

const photos = ref([])

function choose() {
  const remain = props.max - photos.value.length
  uni.chooseImage({
    count: remain,
    success: (res) => {
      const paths = res.tempFilePaths || []
      photos.value = [...photos.value, ...paths].slice(0, props.max)
      emit('change', photos.value)
    },
  })
}

function remove(idx) {
  photos.value.splice(idx, 1)
  emit('change', photos.value)
}

function preview(idx) {
  uni.previewImage({
    urls: photos.value,
    current: idx,
  })
}

defineExpose({ photos })
</script>

<style lang="scss" scoped>
.photo-upload {
  .pu-list {
    display: flex;
    flex-wrap: wrap;
    gap: 16rpx;

    .pu-item {
      position: relative;
      width: 160rpx;
      height: 160rpx;

      .pu-img {
        width: 100%;
        height: 100%;
        border-radius: 12rpx;
      }

      .pu-del {
        position: absolute;
        top: -12rpx;
        right: -12rpx;
        width: 40rpx;
        height: 40rpx;
        border-radius: 50%;
        background: #f56c6c;
        color: #fff;
        font-size: 24rpx;
        display: flex;
        align-items: center;
        justify-content: center;
      }
    }

    .pu-add {
      width: 160rpx;
      height: 160rpx;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      border: 2rpx dashed #c0c4cc;
      border-radius: 12rpx;
      color: #909399;

      .pu-add-icon {
        font-size: 48rpx;
        line-height: 1;
      }

      .pu-add-text {
        font-size: 22rpx;
        margin-top: 8rpx;
      }
    }
  }

  .pu-tip {
    font-size: 22rpx;
    color: #c0c4cc;
    margin-top: 12rpx;
  }
}
</style>
