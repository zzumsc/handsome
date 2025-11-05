<template>
    <div class="course-students-container">
        <PageHeader title="选课学生列表"
                    :show-back="true"
                    @back="handleBack" />

        <div class="course-info-card" v-if="courseInfo">
            <h3>{{ courseInfo.name }}（{{ courseInfo.courseCode }}）</h3>
            <p>
                教师：{{ courseInfo.teacherName }} |
                学分：{{ courseInfo.credit }} 
                <!--状态：<el-tag :type="getStatusType">{{ getStatusText }}</el-tag>-->
            </p>
            <p>
                选课人数：{{ total }}/{{ courseInfo.maxStudents }} |
                初始积分：{{ courseInfo.initialPoints }} |
                当前积分：{{ courseInfo.currentPoints }}
            </p>
        </div>

        <el-table :data="studentList"
                  border
                  style="width: 100%; margin-top: 20px"
                  v-loading="loading">
            <el-table-column prop="student.no" label="学生学号" width="120" align="center" />
            <el-table-column prop="student.name" label="姓名" align="center" />
            <el-table-column prop="student.email" label="邮箱" align="center" />
            <el-table-column prop="selectionTime" label="选课时间" align="center">
                <template #default="scope">
                    {{ formatDateTime(scope.row.selectionTime) }}
                </template>
            </el-table-column>
            <el-table-column prop="pointsUsed" label="消耗积分" align="center" />
        </el-table>

        <el-pagination :total="total"
                       :page-size="pageSize"
                       :current-page="currentPage"
                       :page-sizes="[10, 20, 50]"
                       layout="total, sizes, prev, pager, next, jumper"
                       @size-change="handleSizeChange"
                       @current-change="handleCurrentChange"
                       style="margin-top: 20px; text-align: right" />
    </div>
</template>

<script setup>
    import { ref, onMounted } from 'vue'
    import { useRoute, useRouter } from 'vue-router'
    import { getStudentsByCourseId, getCourseById } from '../../api/AdminCourseAPI'
    import PageHeader from '../../components/common/PageHeader.vue'
    import { ElMessage } from 'element-plus'

    // 路由
    const route = useRoute()
    const router = useRouter()
    const courseId = ref(route.params.id)

    // 数据
    const studentList = ref([])
    const courseInfo = ref(null)
    const total = ref(0)
    const loading = ref(false)

    // 分页参数
    const currentPage = ref(1)
    const pageSize = ref(10)

    // 页面加载时获取数据
    onMounted(async () => {
        await Promise.all([
            fetchCourseInfo(),
            fetchStudentList()
        ])
    })

    // 获取课程信息
    const fetchCourseInfo = async () => {
        try {
            const res = await getCourseById(courseId.value)
            if (res.code === 0) {
                courseInfo.value = res.data.course
            } else {
                ElMessage.error(res.msg || '获取课程信息失败')
            }
        } catch (error) {
            ElMessage.error('获取课程信息失败，请稍后重试')
        }
    }

    // 获取选课学生列表
    const fetchStudentList = async () => {
        try {
            loading.value = true
            const query = {
                page: currentPage.value,
                size: pageSize.value
            }
            const res = await getStudentsByCourseId(courseId.value, query)
            if (res.code === 0) {
                studentList.value = res.data.students || []
                total.value = res.data.total || 0
            } else {
                ElMessage.error(res.msg || '获取学生列表失败')
            }
        } catch (error) {
            ElMessage.error('获取学生列表失败，请稍后重试')
        } finally {
            loading.value = false
        }
    }

    // 格式化日期时间
    const formatDateTime = (time) => {
        if (!time) return ''
        return new Date(time).toLocaleString()
    }

    // 课程状态显示
    const getStatusText = () => {
        if (!courseInfo.value) return ''
        switch (courseInfo.value.status) {
            case 'published':
                return '选课中'
            case 'draft':
                return '未发布'
            case 'closed':
                return '已结束'
            default:
                return courseInfo.value.status
        }
    }

    const getStatusType = () => {
        if (!courseInfo.value) return ''
        switch (courseInfo.value.status) {
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

    // 分页事件
    const handleSizeChange = (size) => {
        pageSize.value = size
        currentPage.value = 1
        fetchStudentList()
    }

    const handleCurrentChange = (page) => {
        currentPage.value = page
        fetchStudentList()
    }

    // 返回上一页
    const handleBack = () => {
        router.push('/admin/course-manage')
    }
</script>

<style scoped>
    .course-students-container {
        padding: 20px;
    }

    .course-info-card {
        padding: 16px;
        background-color: #f8fafc;
        border-radius: 8px;
        margin-top: 20px;
    }

        .course-info-card h3 {
            margin: 0 0 12px;
            color: #1e40af;
        }

        .course-info-card p {
            margin: 8px 0;
            color: #4b5563;
        }
</style>