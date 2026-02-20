<template>
    <div class="course-manage-container">
        <div class="header-actions">
            <h2>课程管理</h2>
            <div class="header-buttons">
                <el-button type="primary" :icon="Plus" @click="openAddDialog">新增课程</el-button>
                <el-button type="success"
                           :disabled="selectedCourseIds.length === 0"
                           @click="handleBatchStart">
                    批量开始 ({{ selectedCourseIds.length }})
                </el-button>
                <el-button type="warning"
                           :disabled="selectedCourseIds.length === 0"
                           @click="handleBatchEnd">
                    批量结束 ({{ selectedCourseIds.length }})
                </el-button>
            </div>
        </div>

        <!-- 精简课程列表 -->
        <el-table :data="courseList"
                  border
                  stripe
                  style="width: 100%; margin-top: 16px"
                  v-loading="loading"
                  @selection-change="handleSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="courseCode" label="课程代码" width="150" />
            <el-table-column prop="name" label="课程名称" width="400" show-overflow-tooltip />
            <el-table-column prop="teacherName" label="授课教师" width="150" />
            <el-table-column label="人数" width="120" align="center">
                <template #default="scope">
                    <span>
                        {{ scope.row.currentStudents }}/{{ scope.row.maxStudents }}
                    </span>
                </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100" align="center">
                <template #default="scope">
                    <el-tag :type="getStatusTagType(scope.row.status)" size="small">
                        {{ getStatusText(scope.row.status) }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="400" align="center">
                <template #default="scope">
                    <el-button type="danger" size="small"
                               @click="handleDeleteCourse(scope.row.id)">
                        删除
                    </el-button>
                    <el-button type="success" size="small"
                               @click="handleStartSingle(scope.row)"
                               :disabled="scope.row.status === 'published'">
                        开始选课
                    </el-button>
                    <el-button type="warning" size="small"
                               @click="handleEndSingle(scope.row)"
                               :disabled="scope.row.status !== 'published'">
                        结束选课
                    </el-button>
                    <el-button type="primary" size="small"
                               @click="openDetailDialog(scope.row)">
                        详细信息
                    </el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 分页 -->
        <el-pagination :total="total"
                       :page-size="pageSize"
                       :current-page="currentPage"
                       :page-sizes="[20, 50]"
                       layout="total, sizes, prev, pager, next, jumper"
                       @size-change="handleSizeChange"
                       @current-change="handleCurrentChange"
                       style="margin-top: 20px; text-align: right" />

        <!-- ==================== 课程详情弹窗 ==================== -->
        <el-dialog v-model="detailVisible"
                   :title="detailCourse ? detailCourse.name : '课程详情'"
                   width="720px"
                   top="5vh"
                   destroy-on-close>
            <div v-if="detailCourse" class="detail-content">
                <!-- 基本信息卡片 -->
                <div class="info-card">
                    <div class="info-header">
                        <div class="info-title">
                            <span class="course-code">{{ detailCourse.courseCode }}</span>
                            <el-tag :type="getStatusTagType(detailCourse.status)" size="small">
                                {{ getStatusText(detailCourse.status) }}
                            </el-tag>
                        </div>
                        <div class="info-actions">
                            <el-button type="success" size="small"
                                       @click="handleStartSingle(detailCourse)"
                                       :disabled="detailCourse.status === 'published'">
                                开始选课
                            </el-button>
                            <el-button type="warning" size="small"
                                       @click="handleEndSingle(detailCourse)"
                                       :disabled="detailCourse.status !== 'published'">
                                结束选课
                            </el-button>
                            <el-button type="danger" size="small"
                                       @click="handleDeleteCourse(detailCourse.id)">
                                删除
                            </el-button>
                        </div>
                    </div>

                    <el-descriptions :column="2" border size="small" class="info-desc">
                        <el-descriptions-item label="授课教师">{{ detailCourse.teacherName }}</el-descriptions-item>
                        <el-descriptions-item label="学分">{{ detailCourse.credit }}</el-descriptions-item>
                        <el-descriptions-item label="最大人数">{{ detailCourse.maxStudents }}</el-descriptions-item>
                        <el-descriptions-item label="已录取人数">
                            {{ detailDbCurrentStudents ?? detailCourse.currentStudents }}/{{ detailCourse.maxStudents }}
                        </el-descriptions-item>
                        <el-descriptions-item v-if="detailCourse.status === 'published'" label="竞价人数">
                            {{ detailStudentTotal }} 人竞价中
                        </el-descriptions-item>
                        <el-descriptions-item label="往年录取分">{{ detailCourse.lastYearScore || 0 }}</el-descriptions-item>
                        <el-descriptions-item label="课程ID">{{ detailCourse.id }}</el-descriptions-item>
                        <el-descriptions-item label="选课开始时间">
                            {{ detailCourse.startTime ? formatTime(detailCourse.startTime) : '未设置' }}
                        </el-descriptions-item>
                        <el-descriptions-item label="选课结束时间">
                            {{ detailCourse.endTime ? formatTime(detailCourse.endTime) : '未设置' }}
                        </el-descriptions-item>
                    </el-descriptions>
                </div>

                <!-- 往期已录取学生（竞价中且有往期录取时展示） -->
                <div class="student-section" v-if="detailCourse.status === 'published' && detailAdmittedList.length > 0">
                    <h4>
                        往期已录取学生
                        <span class="student-count">（共 {{ detailAdmittedList.length }} 人）</span>
                    </h4>
                    <el-table :data="detailAdmittedList"
                              border size="small" max-height="200" style="width: 100%">
                        <el-table-column label="学号" width="140" align="center">
                            <template #default="scope">{{ scope.row.student?.no || '-' }}</template>
                        </el-table-column>
                        <el-table-column label="姓名" align="center">
                            <template #default="scope">{{ scope.row.student?.name || '-' }}</template>
                        </el-table-column>
                        <el-table-column label="邮箱" align="center" show-overflow-tooltip>
                            <template #default="scope">{{ scope.row.student?.email || '-' }}</template>
                        </el-table-column>
                        <el-table-column label="录取积分" width="100" align="center">
                            <template #default="scope">{{ scope.row.pointsUsed || '-' }}</template>
                        </el-table-column>
                        <el-table-column label="录取时间" width="170" align="center">
                            <template #default="scope">{{ formatDateTime(scope.row.selectionTime) }}</template>
                        </el-table-column>
                    </el-table>
                </div>

                <!-- 当前学生列表（竞价中/已结束） -->
                <div class="student-section">
                    <h4>
                        {{ detailCourse.status === 'published' ? '当前竞价学生' : '录取学生' }}
                        <span class="student-count">（共 {{ detailStudentTotal }} 人）</span>
                    </h4>

                    <el-table :data="detailStudentList"
                              border
                              size="small"
                              v-loading="detailStudentLoading"
                              max-height="300"
                              style="width: 100%">
                        <el-table-column label="学号" width="140" align="center">
                            <template #default="scope">
                                {{ scope.row.studentNo || scope.row.student?.no || '-' }}
                            </template>
                        </el-table-column>
                        <el-table-column label="姓名" align="center">
                            <template #default="scope">
                                {{ scope.row.studentName || scope.row.student?.name || '-' }}
                            </template>
                        </el-table-column>
                        <el-table-column label="邮箱" align="center" show-overflow-tooltip>
                            <template #default="scope">
                                {{ scope.row.studentEmail || scope.row.student?.email || '-' }}
                            </template>
                        </el-table-column>
                        <el-table-column label="积分" width="100" align="center">
                            <template #default="scope">
                                {{ scope.row.pointsUsed || scope.row.bidPoints || '-' }}
                            </template>
                        </el-table-column>
                        <el-table-column label="时间" width="170" align="center">
                            <template #default="scope">
                                {{ formatDateTime(scope.row.selectionTime || scope.row.bidTime) }}
                            </template>
                        </el-table-column>
                    </el-table>

                    <div v-if="detailStudentList.length === 0 && !detailStudentLoading"
                         class="empty-tip">
                        暂无学生数据
                    </div>
                </div>
            </div>
        </el-dialog>

        <!-- ==================== 新增/编辑课程弹窗 ==================== -->
        <el-dialog :title="dialogTitle"
                   v-model="dialogVisible"
                   width="520px">
            <el-form :model="form"
                     ref="courseForm"
                     label-width="120px"
                     :rules="formRules">
                <el-form-item label="课程代码" prop="courseCode">
                    <el-input v-model="form.courseCode" placeholder="如 CS101" />
                </el-form-item>
                <el-form-item label="课程名称" prop="name">
                    <el-input v-model="form.name" placeholder="如 计算机基础" />
                </el-form-item>
                <el-form-item label="授课教师" prop="teacherName">
                    <el-input v-model="form.teacherName" placeholder="如 张教授" />
                </el-form-item>
                <el-form-item label="学分" prop="credit">
                    <el-input v-model.number="form.credit" type="number" min="1" />
                </el-form-item>
                <el-form-item label="最大选课人数" prop="maxStudents">
                    <el-input v-model.number="form.maxStudents" type="number" min="1" />
                </el-form-item>
                <el-form-item label="往年录取分" prop="lastYearScore">
                    <el-input v-model.number="form.lastYearScore" type="number" min="0" step="0.01" />
                </el-form-item>
                <el-form-item label="选课开始时间" prop="startTime">
                    <el-date-picker
                        v-model="form.startTime"
                        type="datetime"
                        placeholder="选择开始时间"
                        value-format="YYYY-MM-DDTHH:mm:ss"
                        style="width: 100%" />
                </el-form-item>
                <el-form-item label="选课结束时间" prop="endTime">
                    <el-date-picker
                        v-model="form.endTime"
                        type="datetime"
                        placeholder="选择结束时间"
                        value-format="YYYY-MM-DDTHH:mm:ss"
                        style="width: 100%" />
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitForm">确认</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<script setup>
    import { ref, reactive, onMounted, getCurrentInstance } from 'vue'
    import { Plus } from '@element-plus/icons-vue'
    import {
        addCourse,
        deleteCourse as deleteCourseApi,
        getAllCourses,
        startCourseSelection,
        endCourseSelection,
        batchStartCourseSelection,
        batchEndCourseSelection,
        getStudentsByCourseId,
        getCourseById
    } from '../../api/AdminCourseAPI'
    import { ElMessage, ElMessageBox } from 'element-plus'

    const { proxy } = getCurrentInstance()

    // ==================== 列表数据 ====================
    const courseList = ref([])
    const total = ref(0)
    const loading = ref(false)
    const currentPage = ref(1)
    const pageSize = ref(20)
    const searchKeyword = ref('')
    const selectedCourseIds = ref([])

    // ==================== 详情弹窗数据 ====================
    const detailVisible = ref(false)
    const detailCourse = ref(null)
    const detailStudentList = ref([])
    const detailStudentTotal = ref(0)
    const detailStudentLoading = ref(false)
    const detailDbCurrentStudents = ref(0)
    const detailAdmittedList = ref([])  // 往期已录取学生列表

    // ==================== 新增弹窗数据 ====================
    const dialogVisible = ref(false)
    const dialogTitle = ref('新增课程')
    const form = reactive({
        id: null,
        courseCode: '',
        name: '',
        teacherName: '',
        credit: 3,
        maxStudents: 50,
        lastYearScore: 0,
        startTime: null,
        endTime: null
    })

    const formRules = reactive({
        courseCode: [{ required: true, message: '请输入课程代码', trigger: 'blur' }],
        name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
        teacherName: [{ required: true, message: '请输入教师姓名', trigger: 'blur' }],
        credit: [{ required: true, message: '请输入学分', trigger: 'blur' }],
        maxStudents: [{ required: true, message: '请输入最大选课人数', trigger: 'blur' }],
        lastYearScore: [{ required: true, message: '请输入往年录取分', trigger: 'blur' }]
    })

    onMounted(() => {
        fetchCourses()
    })

    // ==================== 列表请求 ====================

    const fetchCourses = async () => {
        try {
            loading.value = true
            const params = {
                page: currentPage.value,
                size: pageSize.value,
                keyword: searchKeyword.value
            }
            const res = await getAllCourses(params)
            if (res.code === 0) {
                courseList.value = res.data.data.records || []
                total.value = res.data.data.total || 0
            } else {
                ElMessage.error(res.msg || '获取课程列表失败')
            }
        } catch (error) {
            ElMessage.error('获取课程列表失败，请稍后重试')
        } finally {
            loading.value = false
        }
    }

    // ==================== 详情弹窗 ====================

    const openDetailDialog = async (course) => {
        detailCourse.value = { ...course }
        detailStudentList.value = []
        detailStudentTotal.value = 0
        detailAdmittedList.value = []
        detailVisible.value = true
        await fetchDetailStudents(course.id)
    }

    const fetchDetailStudents = async (courseId) => {
        try {
            detailStudentLoading.value = true
            const res = await getStudentsByCourseId(courseId)
            if (res.code === 0) {
                detailStudentList.value = res.data.students || res.data.bidders || []
                detailStudentTotal.value = res.data.total || 0
                // 竞价中时后端额外返回DB已录取人数和往期已录取学生列表
                detailDbCurrentStudents.value = res.data.dbCurrentStudents ?? detailCourse.value?.currentStudents ?? 0
                detailAdmittedList.value = res.data.admittedStudents || []
            }
        } catch (error) {
            ElMessage.error('获取学生列表失败')
        } finally {
            detailStudentLoading.value = false
        }
    }

    // 刷新详情弹窗中的课程信息
    const refreshDetailCourse = async (courseId) => {
        try {
            const res = await getCourseById(courseId)
            if (res.code === 0) {
                detailCourse.value = res.data.course
            }
        } catch (error) {
            // 静默失败
        }
    }

    // ==================== 批量操作 ====================

    const handleSelectionChange = (selection) => {
        selectedCourseIds.value = selection.map(item => item.id)
    }

    const handleBatchStart = async () => {
        try {
            if (selectedCourseIds.value.length === 0) {
                ElMessage.warning('请先选择课程')
                return
            }
            await ElMessageBox.confirm(
                `确定要批量开始 ${selectedCourseIds.value.length} 门课程的选课吗？`,
                '批量开始选课',
                { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
            )
            const res = await batchStartCourseSelection(selectedCourseIds.value)
            if (res.code === 0) {
                ElMessage.success(res.msg)
                fetchCourses()
            } else {
                ElMessage.error(res.msg || '批量开始选课失败')
            }
        } catch (error) {
            if (error !== 'cancel') {
                ElMessage.error('批量开始选课失败')
            }
        }
    }

    const handleBatchEnd = async () => {
        try {
            if (selectedCourseIds.value.length === 0) {
                ElMessage.warning('请先选择课程')
                return
            }
            await ElMessageBox.confirm(
                `确定要批量结束 ${selectedCourseIds.value.length} 门课程的选课吗？`,
                '批量结束选课',
                { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
            )
            const res = await batchEndCourseSelection(selectedCourseIds.value)
            if (res.code === 0) {
                ElMessage.success(res.msg)
                fetchCourses()
            } else {
                ElMessage.error(res.msg || '批量结束选课失败')
            }
        } catch (error) {
            if (error !== 'cancel') {
                ElMessage.error('批量结束选课失败')
            }
        }
    }

    // ==================== 单个操作 ====================

    const handleStartSingle = async (course) => {
        try {
            const res = await startCourseSelection(course.id)
            if (res.code === 0) {
                ElMessage.success('课程已发布，开始选课')
                fetchCourses()
                await refreshDetailCourse(course.id)
                await fetchDetailStudents(course.id)
            } else {
                ElMessage.error(res.msg || '操作失败')
            }
        } catch (error) {
            ElMessage.error('操作失败，请稍后重试')
        }
    }

    const handleEndSingle = async (course) => {
        try {
            await ElMessageBox.confirm(
                '确定要结束该课程的选课吗？',
                '结束选课',
                { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
            )
            const res = await endCourseSelection(course.id)
            if (res.code === 0) {
                ElMessage.success(res.msg || '课程已结束选课，结算完成')
                fetchCourses()
                await refreshDetailCourse(course.id)
                await fetchDetailStudents(course.id)
            } else {
                ElMessage.error(res.msg || '操作失败')
            }
        } catch (error) {
            if (error !== 'cancel') {
                ElMessage.error('操作失败，请稍后重试')
            }
        }
    }

    // ==================== 新增/编辑 ====================

    const openAddDialog = () => {
        dialogTitle.value = '新增课程'
        Object.assign(form, {
            id: null,
            courseCode: '',
            name: '',
            teacherName: '',
            credit: 3,
            maxStudents: 50,
            lastYearScore: 0,
            startTime: null,
            endTime: null
        })
        dialogVisible.value = true
    }

    const submitForm = async () => {
        try {
            await proxy.$refs.courseForm.validate()
            if (form.id) {
                // 编辑课程（预留）
            } else {
                const res = await addCourse(form)
                if (res.code === 0) {
                    ElMessage.success('课程新增成功')
                    dialogVisible.value = false
                    fetchCourses()
                } else {
                    ElMessage.error(res.msg || '新增课程失败')
                }
            }
        } catch (error) {
            return false
        }
    }

    const handleDeleteCourse = async (id) => {
        try {
            await ElMessageBox.confirm('确定要删除该课程吗？', '确认删除', {
                confirmButtonText: '确定',
                cancelButtonText: '取消',
                type: 'warning'
            })
            const res = await deleteCourseApi(id)
            if (res.code === 0) {
                ElMessage.success('课程删除成功')
                detailVisible.value = false
                fetchCourses()
            } else {
                ElMessage.error(res.msg || '删除课程失败')
            }
        } catch (error) {
            if (error !== 'cancel') {
                ElMessage.error('删除失败，请稍后重试')
            }
        }
    }

    // ==================== 工具方法 ====================

    const formatTime = (timeStr) => {
        if (!timeStr) return '未设置'
        const d = new Date(timeStr)
        if (isNaN(d.getTime())) return timeStr
        const pad = (n) => String(n).padStart(2, '0')
        return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
    }

    const formatDateTime = (time) => {
        if (!time) return '-'
        return new Date(time).toLocaleString()
    }

    const getStatusText = (status) => {
        switch (status) {
            case 'published': return '竞价中'
            case 'draft': return '未发布'
            case 'closed': return '已结束'
            default: return status
        }
    }

    const getStatusTagType = (status) => {
        switch (status) {
            case 'published': return 'success'
            case 'draft': return 'info'
            case 'closed': return 'danger'
            default: return ''
        }
    }

    const handleSizeChange = (size) => {
        pageSize.value = size
        currentPage.value = 1
        fetchCourses()
    }

    const handleCurrentChange = (page) => {
        currentPage.value = page
        fetchCourses()
    }
</script>

<style scoped>
    .course-manage-container {
        padding: 20px;
    }

    .header-actions {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
    }

    .header-actions h2 {
        margin: 0;
    }

    .header-buttons {
        display: flex;
        gap: 10px;
    }

    /* ==================== 详情弹窗样式 ==================== */

    .detail-content {
        display: flex;
        flex-direction: column;
        gap: 20px;
    }

    .info-card {
        background: #f8fafc;
        border-radius: 8px;
        padding: 16px;
    }

    .info-header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 16px;
    }

    .info-title {
        display: flex;
        align-items: center;
        gap: 10px;
    }

    .course-code {
        font-size: 16px;
        font-weight: 700;
        color: #1e40af;
    }

    .info-actions {
        display: flex;
        gap: 8px;
    }

    .info-desc {
        margin-top: 8px;
    }

    .student-section h4 {
        margin: 0 0 12px;
        color: #374151;
        font-size: 15px;
    }

    .student-count {
        color: #9ca3af;
        font-weight: normal;
        font-size: 13px;
    }

    .empty-tip {
        text-align: center;
        color: #9ca3af;
        padding: 32px 0;
        font-size: 14px;
    }
</style>
