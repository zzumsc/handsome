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

        <!-- 3. 状态信息（往年录取分、竞价人数/录取人数、课程状态） -->
        <div class="course-status-info">
            <div class="status-item">
                <span>往年录取分：</span>
                <PointDisplay :point="course.lastYearScore" />
            </div>
            <div class="status-item" v-if="course.status === 'published'">
                <span class="bid-count">竞价人数：{{ course.bidCount || 0 }}</span>
            </div>
            <div class="status-item" v-if="course.status === 'closed'">
                <span>录取人数：{{ course.currentStudents }}/{{ course.maxStudents }}</span>
            </div>
            <div class="status-item">
                <span>容量：{{ course.maxStudents }}人</span>
            </div>
            <div class="status-item">
                <span :class="statusClass">{{ statusText }}</span>
            </div>
        </div>

        <!-- 4. 操作区（插槽，父组件传递按钮） -->
        <div class="course-actions">
            <slot name="actions"></slot>
        </div>
    </div>
</template>

<script setup>
    import { defineProps, computed } from 'vue';
    import PointDisplay from './PointDisplay.vue';

    const props = defineProps({
        course: {
            type: Object,
            required: true,
            validator: (val) => {
                const requiredKeys = ['name', 'courseCode', 'teacherName', 'credit', 'maxStudents', 'status'];
                return requiredKeys.every(key => val[key] !== undefined);
            }
        }
    });

    const statusText = computed(() => {
        const statusMap = {
            published: '竞价中',
            closed: '已结束',
            draft: '未发布'
        };
        return statusMap[props.course.status] || '未知状态';
    });

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
    .course-card {
        border: 1px solid #e5e7eb;
        border-radius: 8px;
        padding: 16px;
        margin-bottom: 24px;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
        display: flex;
        flex-direction: column;
        height: 100%;
    }

    .course-header {
        margin-bottom: 12px;
    }

    .course-name {
        font-size: 18px;
        font-weight: 600;
        color: #1f2937;
        margin: 0;
    }

    .course-basic-info {
        display: flex;
        gap: 16px;
        margin-bottom: 12px;
        color: #6b7280;
        font-size: 14px;
        flex-wrap: wrap;
    }

    .course-status-info {
        display: flex;
        gap: 16px;
        margin-bottom: 16px;
        color: #374151;
        font-size: 14px;
        flex-wrap: wrap;
        align-items: center;
    }

    .status-item {
        display: flex;
        align-items: center;
    }

    .bid-count {
        color: #d97706;
        font-weight: 500;
    }

    .status-tag {
        padding: 2px 8px;
        border-radius: 12px;
        font-size: 12px;
        font-weight: 500;
    }

    .status-published {
        background: #fef3c7;
        color: #d97706;
    }

    .status-closed {
        background: #fef2f2;
        color: #dc2626;
    }

    .status-draft {
        background: #f3f4f6;
        color: #6b7280;
    }

    .course-actions {
        margin-top: auto;
        display: flex;
        justify-content: flex-end;
        gap: 8px;
        padding-top: 12px;
        border-top: 1px solid #e5e7eb;
    }
</style>
