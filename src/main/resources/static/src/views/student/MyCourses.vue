<template>
    <div class="my-courses-container">
        <PageHeader title="我的课程">
            <template #subtitle>
                查看已录取的课程
            </template>
            <template #extra>
                <el-button type="primary" @click="refreshAllCourses">
                    <el-icon><Refresh /></el-icon> 刷新
                </el-button>
            </template>
        </PageHeader>

        <!-- 已录取课程列表 -->
        <div v-if="loading.courseList" class="loading-container">
            <el-loading fullscreen />
        </div>

        <div v-else-if="myCourses.length === 0" class="empty-container">
            <Empty text="暂无已录取的课程" />
        </div>

        <div v-else class="course-grid">
            <CourseCard v-for="course in myCourses"
                        :key="course.id"
                        :course="course">
                <template #actions>
                    <el-tag type="success" size="small">已录取</el-tag>
                </template>
            </CourseCard>
        </div>
    </div>
</template>

<script setup>
    import { ref, onMounted } from 'vue'
    import { getMySelections } from '../../api/studentCourseAPI'
    import CourseCard from '../../components/business/CourseCard.vue'
    import PageHeader from '../../components/common/PageHeader.vue'
    import Empty from '../../components/common/Empty.vue'
    import { ElMessage, ElLoading, ElIcon } from 'element-plus'
    import { Refresh } from '@element-plus/icons-vue'

    const myCourses = ref([])
    const loading = ref({ courseList: true })

    onMounted(async () => {
        await fetchMyCourses()
    })

    // 获取已录取课程
    const fetchMyCourses = async () => {
        try {
            loading.value.courseList = true
            const res = await getMySelections()
            if (res.code === 0) {
                myCourses.value = (res.data.selections || []).map(sel => sel.course)
            } else {
                ElMessage.error(res.msg || '获取已录取课程失败')
                myCourses.value = []
            }
        } catch (error) {
            ElMessage.error('获取已录取课程失败，请稍后重试')
            myCourses.value = []
        } finally {
            loading.value.courseList = false
        }
    }

    const refreshAllCourses = async () => {
        try {
            const loadingInstance = ElLoading.service({ text: '刷新中...', lock: true })
            await fetchMyCourses()
            ElMessage.success('数据已刷新')
        } finally {
            ElLoading.service().close()
        }
    }
</script>

<style scoped>
    .my-courses-container {
        padding: 20px 40px;
        max-width: 1400px;
        margin: 0 auto;
    }

    .course-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: 24px;
        margin-top: 20px;
    }

    .loading-container,
    .empty-container {
        min-height: 400px;
        display: flex;
        align-items: center;
        justify-content: center;
    }
</style>
