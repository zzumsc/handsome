<template>
    <div class="empty-container">
        <img src="../../assets/img/empty.png"
             alt="空数据提示"
             class="empty-img">
        <p class="empty-text">{{ text || '暂无数据' }}</p>
        <slot name="action"></slot>
    </div>
</template>

<script setup>
import { defineProps } from 'vue'

// 组件参数
const props = defineProps({
  // 空数据提示文本
  text: {
    type: String,
    default: ''
  }
})
</script>

<style scoped>
    .empty-container {
        display: flex;
        flex-direction: column;
        align-items: center;
        justify-content: center;
        padding: 40px 20px;
        text-align: center;
    }

    .empty-img {
        width: 120px;
        height: 120px;
        margin-bottom: 16px;
        opacity: 0.6;
    }

    .empty-text {
        margin: 0;
        color: #6b7280;
        font-size: 14px;
    }
</style>
