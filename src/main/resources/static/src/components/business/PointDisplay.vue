<!-- src/components/business/PointDisplay.vue -->
<template>
    <div class="point-display">
        <el-badge :value="point"
                  :type="earned ? 'success' : 'info'"
                  class="point-badge">
            <template #icon>
                <Star class="point-icon" /> <!-- 用 Star 替代 Grade -->
            </template>
            <!--<span class="point-text">
                {{ earned ? '已获得学分' : '课程学分' }}
            </span>-->
        </el-badge>
    </div>
</template>

<script setup>
    // 导入 Star 图标（删除 Grade 导入）
    import { Star } from '@element-plus/icons-vue'
    import { defineProps } from 'vue'

    const props = defineProps({
        point: {
            type: Number,
            required: true,
            validator: (value) => value >= 0
        },
        earned: {
            type: Boolean,
            default: false
        }
    })
</script>

<style scoped>
    /* 样式保持不变 */
    .point-display {
        display: inline-flex;
        align-items: center;
    }

    .point-badge {
        padding: 4px 8px;
        font-size: 13px;
    }

    .point-icon {
        margin-right: 4px;
        width: 16px;
        height: 16px;
    }

    .point-text {
        margin-right: 4px;
    }
</style>