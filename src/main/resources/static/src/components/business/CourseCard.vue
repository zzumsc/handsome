<template>
    <div class="course-card">
        <!-- 1. 课程头部（名称） -->
        <div class="course-header">
            <h3 class="course-name">{{ course.name }}</h3>
        </div>

        <!-- 2. 基础信息（代码、教师、学分） -->
        <div class="course-basic-info">
            <span>代码：{{ course.courseCode }}</span>
            <span>教师：{{ course.teacherName }}</span>
            <span>学分：{{ course.credit }}</span>
        </div>

        <!-- 3. 状态信息（积分、人数、课程状态） -->
        <div class="course-status-info">
            <div class="status-item">
                <span>积分：</span>
                <PointDisplay :point="course.currentPoints" />
            </div>
            <div class="status-item">
                <span>人数：{{ course.currentStudents }}/{{ course.maxStudents }}</span>
            </div>
            <div class="status-item">
                <span :class="statusClass">{{ statusText }}</span>
            </div>
        </div>

        <!-- 4. 操作区（插槽，父组件传递“选课/退课”按钮） -->
        <div class="course-actions">
            <slot name="actions"></slot>
        </div>
    </div>
</template>

<script setup>
    import { defineProps, computed } from 'vue';
    import PointDisplay from './PointDisplay.vue';

    // 接收父组件传递的课程数据
    const props = defineProps({
        course: {
            type: Object,
            required: true,
            validator: (val) => {
                const requiredKeys = ['name', 'courseCode', 'teacherName', 'credit', 'currentPoints', 'currentStudents', 'maxStudents', 'status'];
                return requiredKeys.every(key => val[key] !== undefined);
            }
        }
    });

    // 计算课程状态文本（已发布/已结束/草稿）
    const statusText = computed(() => {
        const statusMap = {
            published: '可选课',
            closed: '已结束',
            draft: '未发布'
        };
        return statusMap[props.course.status] || '未知状态';
    });

    // 计算状态文本样式
    const statusClass = computed(() => {
        const classMap = {
            published: 'status-published',
            closed: 'status-closed',
            draft: 'status-draft'
        };
        return `status-tag ${classMap[props.course.status] || ''}`;
    });
</script>

<style scoped>
    /* 卡片整体：Flex垂直布局 + 统一边框/阴影 */
    .course-card {
        border: 1px solid #e5e7eb;
        border-radius: 8px;
        padding: 16px;
        margin-bottom: 24px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
        display: flex;
        flex-direction: column; /* 子元素垂直排列 */
        height: 100%; /* 确保卡片高度一致，方便对齐 */
    }

    /* 头部：课程名称 */
    .course-header {
        margin-bottom: 12px;
    }

    .course-name {
        font-size: 18px;
        font-weight: 600;
        color: #1f2937;
        margin: 0;
    }

    /* 基础信息：代码、教师、学分 */
    .course-basic-info {
        display: flex;
        gap: 16px;
        margin-bottom: 12px;
        color: #6b7280;
        font-size: 14px;
        flex-wrap: wrap; /* 防止信息溢出，自动换行 */
    }

    /* 状态信息：积分、人数、状态标签 */
    .course-status-info {
        display: flex;
        gap: 16px;
        margin-bottom: 16px;
        color: #374151;
        font-size: 14px;
        flex-wrap: wrap; /* 防止信息溢出，自动换行 */
        align-items: center; /* 垂直居中 */
    }

    .status-item {
        display: flex;
        align-items: center;
    }

    /* 状态标签样式 */
    .status-tag {
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: 500;
    }

    .status-published {
        background: #d1fae5;
        color: #059669;
    }

    .status-closed {
        background: #fef2f2;
        color: #dc2626;
    }

    .status-draft {
        background: #f3f4f6;
        color: #6b7280;
    }

    /* 操作区：固定在底部右侧 */
    .course-actions {
        margin-top: auto; /* 自动推到容器底部 */
        display: flex;
        justify-content: flex-end; /* 按钮靠右 */
        gap: 8px; /* 按钮间距 */
        padding-top: 12px;
        border-top: 1px solid #e5e7eb; /* 分隔线 */
    }
</style>