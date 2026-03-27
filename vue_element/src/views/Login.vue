<template>
    <div class="login-page">
        <div class="login-card">
            <h2 class="login-title">小帅选课系统登录</h2>

            <el-form ref="loginFormRef"
                     :model="loginForm"
                     :rules="loginRules"
                     class="login-form">
                <el-form-item prop="email">
                    <el-input v-model="loginForm.email"
                              placeholder="请输入邮箱"
                              prefix-icon="Message"></el-input>
                </el-form-item>

                <!--<el-form-item prop="code">
                    <el-row :gutter="10">
                        <el-col :span="16">
                            <el-input v-model="loginForm.code"
                                      placeholder="请输入验证码"
                                      prefix-icon="Key"
                                      maxlength="6"></el-input>
                        </el-col>
                        <el-col :span="8">
                            <CodeInput v-model="loginForm.code"
                                       :email="loginForm.email"
                                       @send="handleCodeSend"></CodeInput>
                        </el-col>
                    </el-row>
                </el-form-item>
-->
                <el-form-item prop="code">
                    <!-- 绑定CodeInput组件ref、禁用状态，删除冗余el-input -->
                    <CodeInput ref="codeInputRef"
                               v-model="loginForm.code"
                               :email="loginForm.email"
                               :disabled="loading"
                               @send="handleCodeSend"
                               class="code-input-wrapper" />
                </el-form-item>

                <el-form-item>
                    <el-button type="primary"
                               class="login-btn"
                               @click="handleLogin"
                               :loading="loading">
                        登录
                    </el-button>
                </el-form-item>
            </el-form>
        </div>
    </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '../store/userStore'
import CodeInput from '../components/common/CodeInput.vue'
import { ElMessage } from 'element-plus'

// 表单数据
const loginForm = reactive({
  email: '',
  code: ''
})

// 表单规则
const loginRules = reactive({
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' }
  ],
  code: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { min: 6, max: 6, message: '验证码必须为6位', trigger: 'blur' }
  ]
})

// 状态与实例
const loginFormRef = ref(null)
const loading = ref(false)
const router = useRouter()
const userStore = useUserStore()

// 验证码发送成功处理
/*const handleCodeSend = async (email) => {
    await userStore.sendEmailCode(email)
    ElMessage.success('验证码发送成功，请注意查收')
}*/

    const codeInputRef = ref(null)

    // 2. 修改：handleCodeSend方法新增“启动倒计时”逻辑（原有发送验证码逻辑保留）
    const handleCodeSend = async (email) => {
        try {
            await userStore.sendEmailCode(email)
            ElMessage.success('验证码发送成功，请注意查收')
            // 新增：调用CodeInput组件的startCountdown方法，触发60秒倒计时
            if (codeInputRef.value) {
                codeInputRef.value.startCountdown()
            }
        } catch (error) {
            ElMessage.error('验证码发送失败，请重试')
        }
    }

// 登录处理
const handleLogin = async () => {
  // 表单验证
  if (!loginFormRef.value) return
  const valid = await loginFormRef.value.validate()
  if (!valid) return

  try {
    loading.value = true
    // 调用登录接口
    await userStore.loginByEmail(loginForm.email, loginForm.code)

    // 登录成功后跳转
    ElMessage.success('登录成功')
    const redirectPath = userStore.role === 'admin'
      ? '/admin/course-manage'
      : '/student/course-list'
      setTimeout(() => {
          router.push(redirectPath)
      }, 100)
  } catch (error) {
    // 错误已在request.js中处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
    .login-page {
        display: flex;
        justify-content: center;
        align-items: center;
        min-height: 100vh;
        background-color: #f5f7fa;
    }

    .login-card {
        width: 400px;
        padding: 30px;
        background-color: #fff;
        border-radius: 8px;
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
    }

    .login-title {
        text-align: center;
        margin-bottom: 20px;
        color: #1f2937;
    }

    .login-form {
        margin-top: 20px;
    }

    .login-btn {
        width: 100%;
    }

    .code-input-wrapper {
        width: 100%;
    }
</style>
