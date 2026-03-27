<template>
    <div class="course-list-container">
        <PageHeader title="选课中心">
            <template #subtitle>
                在这里选择您感兴趣的课程进行学习
            </template>
        </PageHeader>

        <!-- 筛选栏 -->
        <div class="filter-bar">
            <!--<el-input v-model="searchKeyword"
                      placeholder="搜索课程名称或教师"
                      clearable
                      class="search-input"
                      @clear="handleSearch"
                      @keyup.enter="handleSearch" />-->
            <el-select v-model="statusFilter"
                       placeholder="课程状态"
                       clearable
                       @change="handleSearch"
                       style="width: 240px">
                <el-option label="全部" value="" />
                <el-option label="选课中" value="published" />
                <el-option label="已结束" value="closed" />
            </el-select>
            <el-button type="default" @click="resetFilter">重置</el-button>
        </div>

        <!-- 课程列表 -->
        <div v-if="loading" class="loading-container">
            <el-loading fullscreen />
        </div>

        <div v-else-if="filteredCourses.length === 0" class="empty-container">
            <Empty text="暂无符合条件的课程" />
        </div>

        <div v-else class="course-grid">
            <CourseCard v-for="course in paginatedCourses"
                        :key="course.id"
                        :course="course">
                <template #actions>
                    <el-button :type="canSelect(course) ? 'primary' : 'default'"
                               size="small"
                               @click="handleSelect(course.id)"
                               :disabled="!canSelect(course)">
                        {{ getButtonText(course) }}
                    </el-button>
                    <el-button type="danger"
                               size="small"
                               @click="handleDrop(course.id)"
                               :disabled=" course.status === 'closed'">
                        退课
                    </el-button>
                </template>
            </CourseCard>
        </div>

        <!-- 分页 -->
        <el-pagination v-if="filteredCourses.length > 0"
                       class="pagination"
                       layout="prev, pager, next"
                       :total="filteredCourses.length"
                       :page-size="pageSize"
                       :current-page="currentPage"
                       @current-change="handlePageChange" />
    </div>
</template>

<script setup>
    import { ref, onMounted, computed } from 'vue'
    import { listAvailableCourses, selectCourse, dropCourse, getMySelections } from '../../api/studentCourseAPI'
    import { useUserStore } from '../../store/userStore'
    import CourseCard from '../../components/business/CourseCard.vue'
    import PageHeader from '../../components/common/PageHeader.vue'
    import Empty from '../../components/common/Empty.vue'
    import { ElMessage, ElLoading,ElMessageBox } from 'element-plus'
    import { getMyPoints } from '../../api/studentAPI' 

    // 状态管理
    const userStore = useUserStore()

    // 课程数据
    const courseList = ref([])
    const selectedCourseIds = ref([]) // 存储已选课程的ID列表
    const loading = ref(true)

    // 筛选条件
    const searchKeyword = ref('')
    const statusFilter = ref('')
    const currentPage = ref(1)
    const pageSize = ref(6)

    // 页面加载时获取课程列表
    onMounted(async () => {
        await Promise.all([
            fetchCourses(),
            fetchMySelections()
        ])
    })

    // 获取可选课程列表
    const fetchCourses = async () => {
        try {
            loading.value = true
            const res = await listAvailableCourses()
            if (res.code === 0) {
                courseList.value = res.data.courses || [] // 后端数据在 data.courses 中
                markSelectedCourses() // 标记已选课程
            } else {
                ElMessage.error(res.msg || '获取课程列表失败')
                courseList.value = []
            }
        } catch (error) {
            ElMessage.error('获取课程列表失败，请稍后重试')
            courseList.value = []
        } finally {
            loading.value = false
        }
    }

    // 获取已选课程（假设接口返回已选课程的courseId列表）
    const fetchMySelections = async () => {
        try {
            const res = await getMySelections()
            if (res.code === 0) {
                // 从接口返回中提取已选课程的ID
                selectedCourseIds.value = (res.data || []).map(sel => sel.courseId)
                markSelectedCourses() // 标记已选课程
            } else {
                ElMessage.error(res.msg || '获取已选课程失败')
                selectedCourseIds.value = []
            }
        } catch (error) {
            //ElMessage.error('获取已选课程失败，请稍后重试')
            selectedCourseIds.value = []
        }
    }

    // 标记已选课程
    const markSelectedCourses = () => {
        if (!courseList.value.length) return

        courseList.value.forEach(course => {
            // 通过ID判断是否已选
            course.isSelected = selectedCourseIds.value.includes(course.id)
        })
    }

    // 筛选后的课程列表（移除category筛选）
    const filteredCourses = computed(() => {
        if (!Array.isArray(courseList.value)) return []

        return courseList.value.filter(course => {
            // 搜索匹配：名称/教师/课程代码
            const matchesSearch = course.name?.includes(searchKeyword.value) ||
                course.teacherName?.includes(searchKeyword.value) ||
                course.courseCode?.includes(searchKeyword.value)

            // 状态匹配
            const matchesStatus = !statusFilter.value || course.status === statusFilter.value

            return matchesSearch && matchesStatus
        })
    })

    // 分页处理
    const paginatedCourses = computed(() => {
        const startIndex = (currentPage.value - 1) * pageSize.value
        return filteredCourses.value.slice(startIndex, startIndex + pageSize.value)
    })

    // 检查是否可以选课
    const canSelect = (course) => {
        return course.status === 'published' &&
            course.currentStudents < course.maxStudents &&
            !course.isSelected
    }

    // 获取按钮文本
    const getButtonText = (course) => {
        if (course.status !== 'published') return '未开放'
        if (course.currentStudents >= course.maxStudents) return '已选满'
        return course.isSelected ? '已选择' : '选择课程'
    }

    // 选课操作
    const handleSelect = async (courseId) => {
        try {
            await ElMessageBox.confirm(
                '确认选择该课程？本次选课将消耗部分积分',
                '选课确认',
                {
                    confirmButtonText: '确认选课',
                    cancelButtonText: '取消',
                    type: 'info'
                }
            )

            const loadingInstance = ElLoading.service({
                text: '处理中...',
                lock: true
            })

            const res = await selectCourse(courseId)
            if (res.code === 0) {
                ElMessage.success('选课成功！')
                await fetchCourses()
                await fetchMySelections()
                await updateUserPoints()
            } else {
                ElMessage.error(res.msg || '选课失败，请稍后重试')
            }
        } catch (error) {
            ElMessage.error(error.message || '选课失败，请稍后重试')
        } finally {
            ElLoading.service().close()
        }
    }

    // 退课操作
    const handleDrop = async (courseId) => {
        try {
            await ElMessageBox.confirm(
                '确认退选该课程？退课将返还原积分的80%',
                '退课确认',
                {
                    confirmButtonText: '确认退课',
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
                ElMessage.success('退课成功！')
                await fetchCourses()
                await fetchMySelections()
                await updateUserPoints()
            } else {
                ElMessage.error(res.msg || '退课失败，请稍后重试')
            }
        } catch (error) {
            ElMessage.error(error.message || '退课失败，请稍后重试')
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

    // 搜索处理
    const handleSearch = () => {
        currentPage.value = 1
    }

    // 重置筛选条件（移除category相关逻辑）
    const resetFilter = () => {
        searchKeyword.value = ''
        statusFilter.value = ''
        currentPage.value = 1
    }

    // 页码变化
    const handlePageChange = (page) => {
        currentPage.value = page
        window.scrollTo({ top: 0, behavior: 'smooth' })
    }
</script>

<style scoped>
    .course-list-container {
        padding: 20px 40px;
        max-width: 1400px;
        margin: 0 auto;
    }

    .page-header {
        margin-bottom: 30px;
    }

    .filter-bar {
        display: flex;
        gap: 16px;
        margin-bottom: 24px;
        align-items: center;
        flex-wrap: wrap;
    }

    .search-input {
        width: 300px;
    }

    .course-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
        gap: 24px;
    }

    .pagination {
        margin-top: 30px;
        text-align: center;
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