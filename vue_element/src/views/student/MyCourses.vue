<template>
    <div class="my-courses-container">
        <PageHeader title="我的课程">
            <template #extra>
                <el-button type="primary" @click="refreshAllCourses">
                    <el-icon><Refresh /></el-icon> 刷新
                </el-button>
            </template>
        </PageHeader>

        <!-- 已选课程标签页 -->
        <el-tabs v-model="activeTab" type="card" style="margin-top: 20px">
            <el-tab-pane label="进行中" name="ongoing">
                <div v-if="loading.courseList" class="loading-container">
                    <el-loading fullscreen />
                </div>
                <div v-else-if="ongoingCourses.length === 0" class="empty-container">
                    <Empty text="暂无进行中的课程" />
                </div>
                <div v-else class="course-grid">
                    <CourseCard v-for="course in ongoingCourses"
                                :key="course.id"
                                :course="course">
                        <template #actions>
                            <el-button type="danger"
                                       size="small"
                                       @click="handleDropCourse(course.id)">
                                退课
                            </el-button>
                        </template>
                    </CourseCard>
                </div>
            </el-tab-pane>

            <el-tab-pane label="已结束" name="completed">
                <div v-if="loading.courseList" class="loading-container">
                    <el-loading fullscreen />
                </div>
                <div v-else-if="completedCourses.length === 0" class="empty-container">
                    <Empty text="暂无已结束的课程" />
                </div>
                <div v-else class="course-grid">
                    <CourseCard v-for="course in completedCourses"
                                :key="course.id"
                                :course="course">
                        <template #actions>
                            <el-button type="default" size="small" disabled>
                                课程已结束
                            </el-button>
                        </template>
                    </CourseCard>
                </div>
            </el-tab-pane>
        </el-tabs>
    </div>
</template>

<script setup>
    import { ref, onMounted, computed } from 'vue'
    import { getMySelections, dropCourse } from '../../api/studentCourseAPI'
    import { useUserStore } from '../../store/userStore'
    import CourseCard from '../../components/business/CourseCard.vue'
    import PageHeader from '../../components/common/PageHeader.vue'
    import Empty from '../../components/common/Empty.vue'
    import { ElMessage, ElMessageBox, ElLoading, ElIcon } from 'element-plus'
    import { Refresh } from '@element-plus/icons-vue'
    import { getMyPoints } from '../../api/studentAPI' 

    // 已选课程列表（存储课程对象）
    const myCourses = ref([])

    // 加载状态
    const loading = ref({
        courseList: true
    })

    const userStore = useUserStore()
    const activeTab = ref('ongoing')

    // 页面加载时获取课程数据
    onMounted(async () => {
        await fetchMyCourses()
    })

    // 获取已选课程（从 selections 中提取 course 对象）
    const fetchMyCourses = async () => {
        try {
            loading.value.courseList = true
            const res = await getMySelections()
            if (res.code === 0) {
                // 核心修改：从 selections 中取出每个选课记录的 course 对象
                myCourses.value = (res.data.selections || []).map(sel => sel.course)
            } else {
                ElMessage.error(res.msg || '获取已选课程失败')
                myCourses.value = []
            }
        } catch (error) {
            ElMessage.error('获取已选课程失败，请稍后重试')
            console.error(error)
            myCourses.value = []
        } finally {
            loading.value.courseList = false
        }
    }

    // 进行中的课程（状态为 published）
    const ongoingCourses = computed(() => {
        return myCourses.value.filter(course => course.status === 'published' || course.status === 'draft')
    })

    // 已结束的课程（状态为 closed）
    const completedCourses = computed(() => {
        return myCourses.value.filter(course => course.status === 'closed')
    })

    // 退课操作（course.id 为课程ID）
    const handleDropCourse = async (courseId) => {
        try {
            await ElMessageBox.confirm(
                '确定要退选这门课程吗？退课将返还原积分的80%',
                '确认退课',
                {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                }
            )

            const loadingInstance = ElLoading.service({
                text: '处理中...',
                lock: true
            })

            const res = await dropCourse(courseId)
            if (res.code === 0) {
                ElMessage.success('退课成功')
                await fetchMyCourses()
                await userStore.getMyInfo()
                await updateUserPoints()
            } else {
                ElMessage.error(res.msg || '退课失败')
            }
        } catch (error) {
            if (error !== 'cancel') {
                ElMessage.error(error.message || '退课失败，请稍后重试')
            }
        } finally {
            ElLoading.service().close()
        }
    }

    const updateUserPoints = async () => {
        try {
            const pointsRes = await getMyPoints()
            if (pointsRes.code === 0) {
                // 更新到 userStore，自动同步到 StudentLayout 显示
                userStore.userInfo = {
                    ...userStore.userInfo,
                    points: pointsRes.data.point
                }
            }
        } catch (error) {
            // 积分更新失败不影响主操作，仅提示
            console.error('积分更新失败：', error)
            ElMessage.warning('积分同步延迟，请刷新页面查看最新积分')
        }
    }

    // 刷新课程数据
    const refreshAllCourses = async () => {
        try {
            const loadingInstance = ElLoading.service({
                text: '刷新中...',
                lock: true
            })
            await fetchMyCourses()
            ElMessage.success('课程数据已刷新')
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

    .loading-container {
        min-height: 400px;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .empty-container {
        min-height: 400px;
        display: flex;
        align-items: center;
        justify-content: center;
    }
</style>