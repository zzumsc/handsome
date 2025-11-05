<template>
    <div class="course-manage-container">
        <div class="header-actions">
            <h2>课程管理</h2>
            <el-button type="primary" @click="openAddDialog">新增课程</el-button>
        </div>

        <!-- 搜索和筛选 -->
        <!--<div class="filter-bar">
            <el-input v-model="searchKeyword"
                      placeholder="搜索课程名称或教师"
                      clearable
                      class="search-input"
                      @clear="handleSearch"
                      @keyup.enter="handleSearch" />
            <el-select v-model="statusFilter"
                       placeholder="课程状态"
                       clearable
                       @change="handleSearch">
                <el-option label="全部" value="" />
                <el-option label="未发布" value="draft" />
                <el-option label="选课中" value="published" />
                <el-option label="已结束" value="closed" />
            </el-select>
            <el-button type="default" @click="resetFilter">重置</el-button>
        </div>-->

        <!-- 课程列表 -->
        <el-table :data="courseList"
                  border
                  style="width: 100%; margin-top: 16px"
                  v-loading="loading">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="courseCode" label="课程代码" width="150" />
            <el-table-column prop="name" label="课程名称" width='300'/>
            <el-table-column prop="teacherName" label="授课教师" width="150" />
            <el-table-column prop="credit" label="学分" width="80" />
            <el-table-column prop="currentStudents" label="已选人数" width="120">
                <template #default="scope">
                    {{ scope.row.currentStudents }}/{{ scope.row.maxStudents }}
                </template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="120">
                <template #default="scope">
                    <el-tag :type="getStatusTagType(scope.row.status)">
                        {{ scope.row.status === 'published' ? '选课中' : scope.row.status === 'draft' ? '未发布' : '已结束' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column label="操作" width="360">
                <template #default="scope">
                    <!-- 1. 删除按钮 -->
                    <el-button type="danger"
                               size="small"
                               @click="handleDeleteCourse(scope.row.id)">
                        删除
                    </el-button>
                    <!-- 2. 开始选课按钮 -->
                    <el-button type="success"
                               size="small"
                               @click="toggleSelectionStatus(scope.row)"
                               :disabled="scope.row.status === 'published'">
                        开始选课
                    </el-button>
                    <!-- 3. 结束选课按钮 -->
                    <el-button type="warning"
                               size="small"
                               @click="toggleSelectionStatus(scope.row)"
                               :disabled="scope.row.status === 'closed' || scope.row.status === 'draft'">
                        结束选课
                    </el-button>
                    <!-- 查看学生按钮 -->
                    <el-button type="info"
                               size="small"
                               @click="viewStudents(scope.row.id)">
                        查看学生
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

        <!-- 新增/编辑课程弹窗 -->
        <el-dialog :title="dialogTitle"
                   v-model="dialogVisible"
                   width="500px">
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
                <el-form-item label="初始积分" prop="initialPoints">
                    <el-input v-model.number="form.initialPoints" type="number" min="0" step="0.01" />
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
    import {
        addCourse,
        deleteCourse as deleteCourseApi,  // 重命名导入的API函数
        getAllCourses,
        startCourseSelection,
        endCourseSelection,
        getStudentsByCourseId
    } from '../../api/AdminCourseAPI'
    import { useRouter } from 'vue-router'
    import { ElMessage, ElMessageBox } from 'element-plus'

    const router = useRouter()
    const { proxy } = getCurrentInstance()

    // 数据
    const courseList = ref([])
    const total = ref(0)
    const loading = ref(false)

    // 分页参数
    const currentPage = ref(1)
    const pageSize = ref(20)

    // 搜索筛选
    const searchKeyword = ref('')
    const statusFilter = ref('')

    // 弹窗状态
    const dialogVisible = ref(false)
    const dialogTitle = ref('新增课程')
    const form = reactive({
        id: null,
        courseCode: '',
        name: '',
        teacherName: '',
        credit: 3,
        maxStudents: 50,
        initialPoints: 10.00
    })

    // 表单验证规则
    const formRules = reactive({
        courseCode: [{ required: true, message: '请输入课程代码', trigger: 'blur' }],
        name: [{ required: true, message: '请输入课程名称', trigger: 'blur' }],
        teacherName: [{ required: true, message: '请输入教师姓名', trigger: 'blur' }],
        credit: [{ required: true, message: '请输入学分', trigger: 'blur' }],
        maxStudents: [{ required: true, message: '请输入最大选课人数', trigger: 'blur' }],
        initialPoints: [{ required: true, message: '请输入初始积分', trigger: 'blur' }]
    })

    // 页面加载时获取课程
    onMounted(() => {
        fetchCourses()
    })

    // 获取课程列表
    const fetchCourses = async () => {
        try {
            loading.value = true
            const params = {
                page: currentPage.value,
                size: pageSize.value,
                keyword: searchKeyword.value,
                status: statusFilter.value
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

    // 打开新增课程弹窗
    const openAddDialog = () => {
        dialogTitle.value = '新增课程'
        Object.assign(form, {
            id: null,
            courseCode: '',
            name: '',
            teacherName: '',
            credit: 3,
            maxStudents: 50,
            initialPoints: 10.00
        })
        dialogVisible.value = true
    }

    // 提交表单
    const submitForm = async () => {
        try {
            await proxy.$refs.courseForm.validate()
            if (form.id) {
                // 编辑课程（如果需要）
            } else {
                // 新增课程
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

    // 删除课程 - 重命名自定义函数
    const handleDeleteCourse = async (id) => {
        try {
            await ElMessageBox.confirm(
                '确定要删除该课程吗？',
                '确认删除',
                {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                }
            )
            // 调用重命名后的API函数
            const res = await deleteCourseApi(id)
            if (res.code === 0) {
                ElMessage.success('课程删除成功')
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

    // 切换选课状态
    const toggleSelectionStatus = async (course) => {
        try {
            if (course.status !== 'published') {
                const res = await startCourseSelection(course.id)
                if (res.code === 0) {
                    ElMessage.success('课程已发布，开始选课')
                    fetchCourses()
                } else {
                    ElMessage.error(res.msg || '操作失败')
                }
            } else if (course.status === 'published') {
                const res = await endCourseSelection(course.id)
                if (res.code === 0) {
                    ElMessage.success('课程已结束选课')
                    fetchCourses()
                } else {
                    ElMessage.error(res.msg || '操作失败')
                }
            }
        } catch (error) {
            ElMessage.error('操作失败，请稍后重试')
        }
    }

    // 查看选课学生
    const viewStudents = (courseId) => {
        router.push(`/admin/course-students/${courseId}`)
    }

    // 状态标签样式
    const getStatusTagType = (status) => {
        switch (status) {
            case 'published':
                return 'success'
            case 'draft':
                return 'info'
            case 'closed':
                return 'danger'
            default:
                return ''
        }
    }

    // 搜索处理
    const handleSearch = () => {
        currentPage.value = 1
        fetchCourses()
    }

    // 重置筛选
    const resetFilter = () => {
        searchKeyword.value = ''
        statusFilter.value = ''
        currentPage.value = 1
        fetchCourses()
    }

    // 分页事件
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
        margin-bottom: 20px;
    }

    .filter-bar {
        display: flex;
        gap: 16px;
        margin-bottom: 20px;
        align-items: center;
    }

    .search-input {
        width: 300px;
    }
</style>