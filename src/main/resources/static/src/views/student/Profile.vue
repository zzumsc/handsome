<template>
    <div class="profile-container">
        <PageHeader title="个人信息" />

        <el-card class="profile-card">
            <el-form :model="userInfo"
                     ref="userForm"
                     label-width="120px"
                     :rules="formRules"
                     style="margin-top: 20px">
                <el-form-item label="学号" prop="no">
                    <el-input v-model="userInfo.no" disabled />
                </el-form-item>
                <el-form-item label="姓名" prop="name">
                    <el-input v-model="userInfo.name" />
                </el-form-item>
                <el-form-item label="邮箱" prop="email">
                    <el-input v-model="userInfo.email" type="email" />
                </el-form-item>
                <el-form-item label="角色">
                    <el-input v-model="userRole" disabled />
                </el-form-item>
                <!-- 后端未返回时间字段，暂时隐藏或注释 -->
                <!-- <el-form-item label="注册时间">
                    <el-input :value="formatDateTime(userInfo.createTime)" disabled />
                </el-form-item>
                <el-form-item label="最后登录">
                    <el-input :value="formatDateTime(userInfo.lastLoginTime)" disabled />
                </el-form-item> -->
                <el-form-item>
                    <el-button type="primary" @click="submitForm">保存修改</el-button>
                </el-form-item>
            </el-form>
        </el-card>

        <!--<el-card class="password-card" style="margin-top: 20px">
            <h3>修改密码</h3>
            <el-form :model="passwordForm"
                     ref="passwordFormRef"
                     label-width="120px"
                     :rules="passwordRules"
                     style="margin-top: 20px">
                <el-form-item label="原密码" prop="oldPassword">
                    <el-input v-model="passwordForm.oldPassword" type="password" />
                </el-form-item>
                <el-form-item label="新密码" prop="newPassword">
                    <el-input v-model="passwordForm.newPassword" type="password" />
                </el-form-item>
                <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input v-model="passwordForm.confirmPassword" type="password" />
                </el-form-item>
                <el-form-item>
                    <el-button type="primary" @click="submitPassword">修改密码</el-button>
                </el-form-item>
            </el-form>
        </el-card>-->
    </div>
</template>

<script setup>
    import { ref, reactive, onMounted, getCurrentInstance, computed } from 'vue'
    import { getMyInfo, updateMyInfo } from '../../api/studentAPI'
    import PageHeader from '../../components/common/PageHeader.vue'
    import { ElMessage } from 'element-plus'

    const { proxy } = getCurrentInstance()

    // 数据
    const userInfo = reactive({
        id: null,
        no: '',
        name: '',
        email: '',
        role: '',
        // 后端未返回时间字段，保留定义但可能为空
        createTime: '',
        lastLoginTime: ''
    })

    const passwordForm = reactive({
        oldPassword: '',
        newPassword: '',
        confirmPassword: ''
    })

    const loading = ref(false)
    const userRole = computed(() => {
        return userInfo.role === 'admin' ? '管理员' : '学生'
    })

    // 表单验证规则
    const formRules = reactive({
        name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
        email: [
            { required: true, message: '请输入邮箱', trigger: 'blur' },
            { type: 'email', message: '请输入正确的邮箱格式', trigger: ['blur', 'change'] }
        ]
    })

    const passwordRules = reactive({
        oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
        newPassword: [
            { required: true, message: '请输入新密码', trigger: 'blur' },
            { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
        ],
        confirmPassword: [
            { required: true, message: '请输入确认密码', trigger: 'blur' },
            {
                validator: (rule, value, callback) => {
                    if (value !== passwordForm.newPassword) {
                        callback(new Error('两次输入的密码不一致'))
                    } else {
                        callback()
                    }
                },
                trigger: 'blur'
            }
        ]
    })

    // 页面加载时获取个人信息
    onMounted(() => {
        fetchUserInfo()
    })

    // 获取个人信息 - 核心修改：从 data.user 中取数据
    const fetchUserInfo = async () => {
        try {
            loading.value = true
            const res = await getMyInfo()
            console.log('个人信息接口返回:', res) // 调试用
            if (res.code === 0) {
                // 后端数据在 data.user 中，需要深层取值
                const userData = res.data?.user || {}
                Object.assign(userInfo, userData)
            } else {
                ElMessage.error(res.msg || '获取个人信息失败')
            }
        } catch (error) {
            console.error('获取个人信息错误:', error)
            ElMessage.error('获取个人信息失败，请稍后重试')
        } finally {
            loading.value = false
        }
    }

    // 提交个人信息修改
    const submitForm = async () => {
        try {
            await proxy.$refs.userForm.validate()
            const res = await updateMyInfo(userInfo)
            if (res.code === 0) {
                ElMessage.success('个人信息更新成功')
            } else {
                ElMessage.error(res.msg || '更新个人信息失败')
            }
        } catch (error) {
            return false
        }
    }

    // 提交密码修改
    const submitPassword = async () => {
        try {
            await proxy.$refs.passwordFormRef.validate()
            // 密码修改需要用户ID和密码信息
            const res = await updateMyInfo({
                id: userInfo.id,
                oldPassword: passwordForm.oldPassword,
                newPassword: passwordForm.newPassword
            })
            if (res.code === 0) {
                ElMessage.success('密码修改成功，请重新登录')
                // 清空密码表单
                passwordForm.oldPassword = ''
                passwordForm.newPassword = ''
                passwordForm.confirmPassword = ''
                // 实际项目中这里应该跳转到登录页
            } else {
                ElMessage.error(res.msg || '修改密码失败')
            }
        } catch (error) {
            return false
        }
    }

    // 格式化日期时间（兼容后端可能返回的时间字段）
    const formatDateTime = (time) => {
        if (!time) return '暂无数据'
        return new Date(time).toLocaleString()
    }
</script>

<style scoped>
    .profile-container {
        padding: 20px;
        max-width: 800px;
        margin: 0 auto;
    }

    .profile-card, .password-card {
        padding: 20px;
    }

        .password-card h3 {
            margin-top: 0;
            color: #1e40af;
        }
</style>
