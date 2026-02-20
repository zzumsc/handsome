<template>
    <div class="course-list-container">
        <PageHeader title="选课中心">
            <template #subtitle>
                竞价选课：为心仪课程分配积分，选课结束后按积分高低录取
            </template>
        </PageHeader>

        <!-- 筛选栏 -->
        <div class="filter-bar">
            <el-select v-model="statusFilter"
                       placeholder="课程状态"
                       clearable
                       @change="handleSearch"
                       style="width: 240px">
                <el-option label="全部" value="" />
                <el-option label="竞价中" value="published" />
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
                    <!-- 竞价中的课程 -->
                    <template v-if="course.status === 'published'">
                        <!-- 已经被往期录取 -->
                        <template v-if="isAdmitted(course.id)">
                            <el-tag type="success" size="small">已录取</el-tag>
                        </template>
                        <!-- 尚未竞价 -->
                        <template v-else-if="!getBidInfo(course.id)">
                            <el-button type="primary"
                                       size="small"
                                       @click="handleBid(course)">
                                竞价预选
                            </el-button>
                        </template>
                        <!-- 已竞价 -->
                        <template v-else>
                            <el-tag type="success" size="small" class="bid-tag">
                                已投 {{ formatPoint(getBidInfo(course.id).bidPoints) }} 分
                            </el-tag>
                            <el-button type="warning"
                                       size="small"
                                       @click="handleUpdateBid(course)">
                                修改积分
                            </el-button>
                            <el-button type="danger"
                                       size="small"
                                       @click="handleCancelBid(course.id)">
                                取消预选
                            </el-button>
                        </template>
                    </template>
                    <!-- 已结束的课程 -->
                    <template v-if="course.status === 'closed'">
                        <el-tag v-if="isAdmitted(course.id)" type="success" size="small">已录取</el-tag>
                        <el-tag v-else type="info" size="small">未参与</el-tag>
                    </template>
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

        <!-- 竞价弹窗 -->
        <el-dialog v-model="bidDialogVisible"
                   :title="isUpdateMode ? '修改竞价积分' : '竞价预选'"
                   width="400px"
                   :close-on-click-modal="false">
            <div class="bid-dialog-content">
                <p class="bid-course-name">课程：{{ bidTargetCourse?.name }}</p>
                <p class="bid-hint" v-if="bidTargetCourse?.lastYearScore > 0">
                    往年最低录取分：<strong>{{ formatPoint(bidTargetCourse?.lastYearScore) }}</strong>（仅供参考）
                </p>
                <p class="bid-hint">当前竞价人数：<strong>{{ bidTargetCourse?.bidCount || 0 }}</strong> 人</p>
                <el-form-item label="投入积分">
                    <el-input-number v-model="bidPointsInput"
                                     :min="0.001"
                                     :precision="3"
                                     :step="1"
                                     controls-position="right"
                                     style="width: 100%" />
                </el-form-item>
            </div>
            <template #footer>
                <el-button @click="bidDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitBid" :loading="bidSubmitting">
                    {{ isUpdateMode ? '确认修改' : '确认竞价' }}
                </el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
    import { ref, onMounted, computed } from 'vue'
    import { listAvailableCourses, bidCourse, updateBid, cancelBid, getMyBid, getMySelections } from '../../api/studentCourseAPI'
    import { useUserStore } from '../../store/userStore'
    import CourseCard from '../../components/business/CourseCard.vue'
    import PageHeader from '../../components/common/PageHeader.vue'
    import Empty from '../../components/common/Empty.vue'
    import { ElMessage, ElLoading, ElMessageBox } from 'element-plus'
    import { getMyPoints } from '../../api/studentAPI'
    import { formatPoint } from '../../utils/format'

    const userStore = useUserStore()

    // 课程数据
    const courseList = ref([])
    const myBids = ref({})              // { courseId: { bidPoints, bidTime, hasBid } }
    const selectedCourseIds = ref([])   // 已录取课程ID列表（从DB selections查）
    const admittedFromRedis = ref(new Set()) // 往期已录取的courseId集合（从Redis admitted Set查）
    const loading = ref(true)

    // 筛选条件
    const statusFilter = ref('')
    const currentPage = ref(1)
    const pageSize = ref(6)

    // 竞价弹窗
    const bidDialogVisible = ref(false)
    const bidTargetCourse = ref(null)
    const bidPointsInput = ref(1)
    const isUpdateMode = ref(false)
    const bidSubmitting = ref(false)

    onMounted(async () => {
        await fetchCourses()
        await fetchMySelections()
    })

    // 获取可选课程列表
    const fetchCourses = async () => {
        try {
            loading.value = true
            const res = await listAvailableCourses()
            if (res.code === 0) {
                courseList.value = res.data.courses || []
                // 获取每个竞价中课程的竞价信息
                await fetchAllBids()
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

    // 获取所有竞价中课程的我的竞价信息（含已录取标记）
    const fetchAllBids = async () => {
        const publishedCourses = courseList.value.filter(c => c.status === 'published')
        const bidPromises = publishedCourses.map(async (course) => {
            try {
                const res = await getMyBid(course.id)
                if (res.code === 0) {
                    // 后端返回 admitted=true 表示往期已录取
                    if (res.data.admitted) {
                        admittedFromRedis.value.add(course.id)
                    } else if (res.data.bid && res.data.bid.hasBid) {
                        myBids.value[course.id] = res.data.bid
                    }
                }
            } catch (e) {
                // 忽略单个课程查询失败
            }
        })
        await Promise.all(bidPromises)
    }

    // 获取已录取课程
    const fetchMySelections = async () => {
        try {
            const res = await getMySelections()
            if (res.code === 0) {
                selectedCourseIds.value = (res.data.selections || []).map(sel => sel.courseId)
            }
        } catch (error) {
            selectedCourseIds.value = []
        }
    }

    // 获取指定课程的竞价信息
    const getBidInfo = (courseId) => {
        return myBids.value[courseId] || null
    }

    // 是否已录取（DB选课记录 或 Redis往期已录取标记）
    const isAdmitted = (courseId) => {
        return selectedCourseIds.value.includes(courseId) || admittedFromRedis.value.has(courseId)
    }

    // 筛选后的课程列表
    const filteredCourses = computed(() => {
        if (!Array.isArray(courseList.value)) return []
        return courseList.value.filter(course => {
            return !statusFilter.value || course.status === statusFilter.value
        })
    })

    // 分页处理
    const paginatedCourses = computed(() => {
        const startIndex = (currentPage.value - 1) * pageSize.value
        return filteredCourses.value.slice(startIndex, startIndex + pageSize.value)
    })

    // 打开竞价弹窗
    const handleBid = (course) => {
        bidTargetCourse.value = course
        bidPointsInput.value = course.lastYearScore > 0 ? Number(course.lastYearScore) : 1
        isUpdateMode.value = false
        bidDialogVisible.value = true
    }

    // 打开修改竞价弹窗
    const handleUpdateBid = (course) => {
        bidTargetCourse.value = course
        const existingBid = getBidInfo(course.id)
        bidPointsInput.value = existingBid ? Number(existingBid.bidPoints) : 1
        isUpdateMode.value = true
        bidDialogVisible.value = true
    }

    // 提交竞价/修改竞价
    const submitBid = async () => {
        if (!bidTargetCourse.value || bidPointsInput.value <= 0) {
            ElMessage.warning('请输入有效的竞价积分')
            return
        }

        bidSubmitting.value = true
        try {
            let res
            if (isUpdateMode.value) {
                res = await updateBid(bidTargetCourse.value.id, bidPointsInput.value)
            } else {
                res = await bidCourse(bidTargetCourse.value.id, bidPointsInput.value)
            }

            if (res.code === 0) {
                ElMessage.success(res.msg || '操作成功')
                bidDialogVisible.value = false
                // 刷新数据
                myBids.value[bidTargetCourse.value.id] = {
                    courseId: bidTargetCourse.value.id,
                    bidPoints: bidPointsInput.value,
                    bidTime: new Date(),
                    hasBid: true
                }
                await fetchCourses()
                await updateUserPoints()
            } else {
                ElMessage.error(res.msg || '操作失败')
            }
        } catch (error) {
            ElMessage.error(error.message || '操作失败，请稍后重试')
        } finally {
            bidSubmitting.value = false
        }
    }

    // 取消预选
    const handleCancelBid = async (courseId) => {
        try {
            await ElMessageBox.confirm(
                '确认取消预选？积分将全额退还',
                '取消预选确认',
                {
                    confirmButtonText: '确认取消',
                    cancelButtonText: '返回',
                    type: 'warning'
                }
            )

            const loadingInstance = ElLoading.service({ text: '处理中...', lock: true })

            const res = await cancelBid(courseId)
            if (res.code === 0) {
                ElMessage.success('取消预选成功，积分已退还')
                delete myBids.value[courseId]
                await fetchCourses()
                await updateUserPoints()
            } else {
                ElMessage.error(res.msg || '取消预选失败')
            }

            loadingInstance.close()
        } catch (error) {
            if (error !== 'cancel') {
                ElMessage.error(error.message || '取消预选失败')
            }
        }
    }

    // 更新积分显示
    const updateUserPoints = async () => {
        try {
            const pointsRes = await getMyPoints()
            if (pointsRes.code === 0) {
                userStore.userInfo = {
                    ...userStore.userInfo,
                    points: pointsRes.data.point
                }
            }
        } catch (error) {
            console.error('积分更新失败：', error)
        }
    }

    const handleSearch = () => {
        currentPage.value = 1
    }

    const resetFilter = () => {
        statusFilter.value = ''
        currentPage.value = 1
    }

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

    .filter-bar {
        display: flex;
        gap: 16px;
        margin-bottom: 24px;
        align-items: center;
        flex-wrap: wrap;
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

    .loading-container,
    .empty-container {
        min-height: 400px;
        display: flex;
        align-items: center;
        justify-content: center;
    }

    .bid-tag {
        margin-right: 4px;
    }

    .bid-dialog-content {
        padding: 0 10px;
    }

    .bid-course-name {
        font-size: 16px;
        font-weight: 600;
        margin-bottom: 12px;
    }

    .bid-hint {
        color: #6b7280;
        font-size: 14px;
        margin-bottom: 8px;
    }

    .bid-hint strong {
        color: #d97706;
    }
</style>
