<template>
    <div class="page-header">
        <el-button v-if="showBack"
                   icon="ArrowLeft"
                   size="small"
                   type="text"
                   @click="$router.back()"></el-button>
        <h2 class="title">{{ title }}</h2>
        <div class="extra">
            <slot name="extra"></slot>
        </div>
    </div>
</template>

<script setup>
import { defineProps } from 'vue'
import { ArrowLeft } from '@element-plus/icons-vue'

// 组件参数
const props = defineProps({
  // 页面标题
  title: {
    type: String,
    required: true
  },
  // 是否显示返回按钮
  showBack: {
    type: Boolean,
    default: true
  }
})
</script>

<style scoped>
    .page-header {
        display: flex;
        align-items: center;
        gap: 12px;
        padding: 16px 0;
        border-bottom: 1px solid #e5e7eb;
        margin-bottom: 20px;
    }

    .title {
        margin: 0;
        font-size: 20px;
        font-weight: 500;
        color: #1f2937;
        flex: 1;
    }

    .extra {
        display: flex;
        gap: 8px;
    }
</style>
