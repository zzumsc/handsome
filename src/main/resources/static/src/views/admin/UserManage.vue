<template>
    <div class="user-manage-container">
        <div class="header-actions">
            <h2>用户管理</h2>
            <el-button type="primary" @click="openAddDialog">新增用户</el-button>
        </div>

        <!-- 搜索与筛选 -->
        <div class="filter-bar">
            <el-input v-model="searchName"
                      placeholder="搜索用户名"
                      clearable
                      class="search-input"
                      @clear="handleSearch"
                      @keyup.enter="handleSearch" />
            <el-input v-model="searchEmail"
                      placeholder="搜索邮箱"
                      clearable
                      class="search-input"
                      @clear="handleSearch"
                      @keyup.enter="handleSearch" />
            <el-input v-model="searchNo"
                      placeholder="搜索学号"
                      clearable
                      class="search-input"
                      @clear="handleSearch"
                      @keyup.enter="handleSearch" />
            <el-button type="primary" @click="handleSearch">搜索</el-button>
            <el-select v-model="roleFilter"
                       placeholder="用户角色"
                       clearable
                       @change="handleSearch"
                       style="width: 240px">
                <el-option label="全部" value="" />
                <el-option label="管理员" value="admin" />
                <el-option label="学生" value="student" />
            </el-select>
            
            <el-button type="default" @click="resetFilter">重置</el-button>
        </div>

        <!-- 用户列表 -->
        <el-table :data="userList"
                  border
                  style="width: 100%; margin-top: 16px"
                  v-loading="loading"
                  @sort-change="handleSortChange">
            <!-- ID列默认排序 -->
            <el-table-column prop="id"
                             label="ID"
                             width="80"
                             align="center"
                             sortable="custom"
                             :sort-orders="['asc', 'desc']" />
            <el-table-column prop="no"
                             label="学号/工号"
                             width="120"
                             align="center"
                             sortable="custom"
                             :sort-orders="['asc', 'desc']" />
            <el-table-column prop="name"
                             label="姓名"
                             align="center"
                             sortable="custom"
                             :sort-orders="['asc', 'desc']" />
            <el-table-column prop="email"
                             label="邮箱"
                             align="center"
                             sortable="custom"
                             :sort-orders="['asc', 'desc']" />
            <el-table-column prop="role"
                             label="角色"
                             width="100"
                             align="center"
                             sortable="custom"
                             :sort-orders="['asc', 'desc']">
                <template #default="scope">
                    <el-tag :type="scope.row.role === 'admin' ? 'warning' : 'success'">
                        {{ scope.row.role === 'admin' ? '管理员' : '学生' }}
                    </el-tag>
                </template>
            </el-table-column>
            <!--<el-table :data="userList"
              border
              style="width: 100%; margin-top: 16px"
              v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="no" label="学号/工号" width="120" align="center" />
        <el-table-column prop="name" label="姓名" align="center" />
        <el-table-column prop="email" label="邮箱" align="center" />
        <el-table-column prop="role" label="角色" width="100" align="center">-->
            <el-table-column label="操作" width="240" align="center">
                <template #default="scope">
                    <!--<el-button type="primary"
                       size="small"
                       @click="viewUserDetail(scope.row.id)">
                查看
            </el-button>-->
                    <el-button type="success"
                               size="small"
                               @click="openEditDialog(scope.row)">
                        编辑
                    </el-button>
                    <el-button type="danger"
                               size="small"
                               @click="handleDeleteUser(scope.row.id)"
                               :disabled="scope.row.role === 'admin'">
                        删除
                    </el-button>
                </template>
            </el-table-column>
        </el-table>

        <!-- 分页组件（适配API的page/size参数） -->
        <el-pagination :total="total"
                       :page-size="pageSize"
                       :current-page="currentPage"
                       :page-sizes="[20, 50]"
                       layout="total, sizes, prev, pager, next, jumper"
                       @size-change="handleSizeChange"
                       @current-change="handleCurrentChange"
                       style="margin-top: 20px; text-align: right" />

        <!-- 新增/编辑用户弹窗 -->
        <el-dialog :title="dialogTitle"
                   v-model="dialogVisible"
                   width="500px">
            <el-form :model="form"
                     ref="userForm"
                     label-width="120px"
                     :rules="formRules">
                <el-form-item label="学号/工号" prop="no">
                    <el-input v-model="form.no" placeholder="请输入学号或工号" />
                </el-form-item>
                <el-form-item label="姓名" prop="name">
                    <el-input v-model="form.name" placeholder="请输入姓名" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="form.email" type="email" placeholder="请输入邮箱" />
                </el-form-item>
                <el-form-item label="角色" prop="role">
                    <el-select v-model="form.role" placeholder="请选择角色">
                        <el-option label="学生" value="student" />
                        <el-option label="管理员" value="admin" />
                    </el-select>
                </el-form-item>
                <!--<el-form-item label="密码"
                              prop="password"
                              v-if="!form.id">
                    <el-input v-model="form.password" type="password" placeholder="请输入密码" />
                </el-form-item>-->
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
                <el-button type="primary" @click="submitForm">确认</el-button>
            </template>
        </el-dialog>

        <!-- 用户详情弹窗（调用getUserById接口） -->
        <!--<el-dialog title="用户详情"
                   v-model="detailVisible"
                   width="500px">
            <el-descriptions column="1" border v-loading="detailLoading">
                <el-descriptions-item label="ID">{{ userDetail.id || '-' }}</el-descriptions-item>
                <el-descriptions-item label="学号/工号">{{ userDetail.no || '-' }}</el-descriptions-item>
                <el-descriptions-item label="姓名">{{ userDetail.name || '-' }}</el-descriptions-item>
                <el-descriptions-item label="邮箱">{{ userDetail.email || '-' }}</el-descriptions-item>
                <el-descriptions-item label="角色">{{ userDetail.role === 'admin' ? '管理员' : '学生' || '-' }}</el-descriptions-item>
                <el-descriptions-item label="创建时间">{{ userDetail.createTime ? formatDateTime(userDetail.createTime) : '-' }}</el-descriptions-item>
                <el-descriptions-item label="最后登录时间">{{ userDetail.lastLoginTime ? formatDateTime(userDetail.lastLoginTime) : '未登录' }}</el-descriptions-item>
            </el-descriptions>
            <template #footer>
                <el-button @click="detailVisible = false">关闭</el-button>
            </template>
        </el-dialog>-->
    </div>
</template>

<script setup>
    import { ref, reactive, onMounted, getCurrentInstance } from 'vue'
    // 1. 导入最新API函数（与提供的AdminUserAPI完全对应）
    import {
        addUser,
        deleteUser,
        updateUser,
        getUserById,
        getUserList
    } from '../../api/AdminCrudAPI'
    import { ElMessage, ElMessageBox } from 'element-plus'

    const { proxy } = getCurrentInstance()

    // 2. 基础数据与分页参数（适配API的page/size）
    const userList = ref([])
    const total = ref(0)
    const loading = ref(false)
    const currentPage = ref(1) // API的page参数
    const pageSize = ref(20)   // API的size参数
    const sortField = ref('id') // 默认排序字段
    const sortDir = ref('asc')  // 默认排序方向

    // 搜索与筛选
    const searchKeyword = ref('')
    const roleFilter = ref('') // 若后端支持role筛选，可直接传递；不支持则前端过滤

    // 弹窗相关
    const dialogVisible = ref(false)
    const detailVisible = ref(false)
    const detailLoading = ref(false)
    const dialogTitle = ref('新增用户')
    const form = reactive({
        id: null,
        no: '',
        name: '',
        email: '',
        role: 'student',
        password: ''
    })
    const userDetail = ref({})

    // 表单验证规则
    const formRules = reactive({
        no: [{ required: true, message: '请输入学号或工号', trigger: 'blur' }],
        name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
        email: [
            { required: true, message: '请输入邮箱', trigger: 'blur' },
            { type: 'email', message: '请输入正确的邮箱格式', trigger: ['blur', 'change'] }
        ],
        role: [{ required: true, message: '请选择角色', trigger: 'change' }],
        password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
    })

    // 3. 页面加载时获取用户列表（调用getUserList）
    onMounted(() => {
        fetchUsers()
    })

    /**
     * 4. 获取用户列表（适配API参数：page、size、keyword）
     */
    // 定义三个独立的搜索变量
    const searchName = ref('')
    const searchEmail = ref('')
    const searchNo = ref('')

    // 搜索方法调整
    const fetchUsers = async () => {
        try {
            loading.value = true
            // 分别传递三个搜索参数
            const params = {
                page: currentPage.value,
                size: pageSize.value,
                name: searchName.value.trim(),
                email: searchEmail.value.trim(),
                no: searchNo.value.trim(),
                role: roleFilter.value,  // 角色参数直接传递，无需前端二次过滤
                sortField: sortField.value,  // 新增排序字段参数
                sortDir: sortDir.value       // 新增排序方向参数
            }
            const res = await getUserList(params)
            if (res.code === 0) {
                // 后端已处理所有过滤逻辑，直接使用返回结果
                userList.value = res.data.users || []
                total.value = res.data.total || 0
            } else {
                ElMessage.error(res.msg || '获取用户列表失败')
            }
        } catch (error) {
            console.error('获取用户列表异常：', error)
            ElMessage.error('获取用户列表失败，请稍后重试')
        } finally {
            loading.value = false
        }
    }

    // 重置方法
    const resetFilter = () => {
        searchName.value = ''
        searchEmail.value = ''
        searchNo.value = ''
        roleFilter.value = ''
        currentPage.value = 1
        handleSearch()
    }

    /**
     * 6. 新增/编辑用户提交（调用addUser/updateUser）
     */
    const submitForm = async () => {
        try {
            // 表单验证
            await proxy.$refs.userForm.validate()

            if (form.id) {
                // 编辑用户：调用updateUser（API要求传含ID的user对象）
                const res = await updateUser(form)
                if (res.code === 0) {
                    ElMessage.success('用户更新成功')
                    dialogVisible.value = false
                    fetchUsers() // 重新加载列表
                } else {
                    ElMessage.error(res.msg || '更新用户失败')
                }
            } else {
                // 新增用户：调用addUser
                const res = await addUser(form)
                if (res.code === 0) {
                    ElMessage.success('用户新增成功')
                    dialogVisible.value = false
                    fetchUsers() // 重新加载列表
                } else {
                    ElMessage.error(res.msg || '新增用户失败')
                }
            }
        } catch (error) {
            // 表单验证失败不处理
            return false
        }
    }

    /**
     * 7. 查看用户详情（调用getUserById）
     */
    const viewUserDetail = async (id) => {
        try {
            detailLoading.value = true
            detailVisible.value = true

            // 调用API的getUserById
            const res = await getUserById(id)
            if (res.code === 0) {
                userDetail.value = res.data || {}
            } else {
                ElMessage.error(res.msg || '获取用户详情失败')
                userDetail.value = {}
            }
        } catch (error) {
            console.error('获取用户详情异常：', error)
            ElMessage.error('获取用户详情失败，请稍后重试')
            userDetail.value = {}
        } finally {
            detailLoading.value = false
        }
    }

    /**
     * 8. 删除用户（调用deleteUser，避免命名冲突）
     */
    const handleDeleteUser = async (id) => {
        try {
            // 确认弹窗
            await ElMessageBox.confirm(
                '确定要删除该用户吗？删除后不可恢复',
                '确认删除',
                {
                    confirmButtonText: '确定',
                    cancelButtonText: '取消',
                    type: 'warning'
                }
            )

            // 调用API的deleteUser
            const res = await deleteUser(id)
            if (res.code === 0) {
                ElMessage.success('用户删除成功')
                fetchUsers() // 重新加载列表
            } else {
                ElMessage.error(res.msg || '删除用户失败')
            }
        } catch (error) {
            // 取消删除不提示
            if (error !== 'cancel') {
                ElMessage.error('删除失败，请稍后重试')
            }
        }
    }

    // 打开新增弹窗
    const openAddDialog = () => {
        dialogTitle.value = '新增用户'
        // 重置表单
        Object.assign(form, {
            id: null,
            no: '',
            name: '',
            email: '',
            role: 'student',
            password: ''
        })
        // 重置验证状态
        proxy.$refs.userForm?.resetFields()
        dialogVisible.value = true
    }

    // 打开编辑弹窗
    const openEditDialog = (user) => {
        dialogTitle.value = '编辑用户'
        // 填充表单数据
        Object.assign(form, {
            id: user.id,
            no: user.no,
            name: user.name,
            email: user.email,
            role: user.role,
            password: '' // 编辑不显示密码
        })
        // 重置验证状态
        proxy.$refs.userForm?.resetFields()
        dialogVisible.value = true
    }

    // 格式化日期时间
    const formatDateTime = (time) => {
        if (!time) return ''
        return new Date(time).toLocaleString('zh-CN', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit'
        })
    }

    // 搜索处理
    const handleSearch = () => {
        currentPage.value = 1 // 搜索重置到第一页
        fetchUsers()
    }


    // 分页：每页条数变化
    const handleSizeChange = (size) => {
        pageSize.value = size
        currentPage.value = 1 // 条数变化重置到第一页
        fetchUsers()
    }

    // 分页：页码变化
    const handleCurrentChange = (page) => {
        currentPage.value = page
        fetchUsers()
    }

    const handleSortChange = ({ prop, order }) => {
        if (prop === sortField.value) {
            // 当前是升序则切换为降序，反之亦然
            sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc';
        } else {
            // 点击新字段时，更新排序字段并默认升序
            sortField.value = prop;
            sortDir.value = 'asc';
        }
        fetchUsers() // 重新获取数据
    }
</script>

<style scoped>
    .user-manage-container {
        padding: 20px;
        max-width: 1400px;
        margin: 0 auto;
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
        flex-wrap: wrap;
    }

    .search-input {
        width: 300px;
    }
</style>