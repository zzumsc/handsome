<template>
    <el-form :model="form"
             ref="formRef"
             label-width="100px"
             :rules="rules">
        <el-form-item label="用户名"
                      prop="username">
            <el-input v-model="form.username"
                      placeholder="请输入用户名"></el-input>
        </el-form-item>
        <el-form-item label="邮箱"
                      prop="email">
            <el-input v-model="form.email"
                      placeholder="请输入邮箱"
                      type="email"></el-input>
        </el-form-item>
        <el-form-item label="角色"
                      prop="role">
            <el-select v-model="form.role"
                       placeholder="请选择角色">
                <el-option label="学生"
                           value="student"></el-option>
                <el-option label="管理员"
                           value="admin"></el-option>
            </el-select>
        </el-form-item>
        <el-form-item v-if="!isEdit"
                      label="初始密码"
                      prop="password">
            <el-input v-model="form.password"
                      placeholder="请输入初始密码"
                      type="password"></el-input>
        </el-form-item>
        <el-form-item>
            <el-button type="primary"
                       @click="handleSubmit">
                提交
            </el-button>
            <el-button @click="handleCancel">
                取消
            </el-button>
        </el-form-item>
    </el-form>
</template>

<script setup>
import { defineProps, defineEmits, ref, watch, onMounted } from 'vue'
import { isValidEmail } from '../../utils/codeUtils'

// 组件参数
const props = defineProps({
  // 是否为编辑模式
  isEdit: {
    type: Boolean,
    default: false
  },
  // 编辑时的用户数据
  initialData: {
    type: Object,
    default: () => ({})
  }
})

// 组件事件
const emit = defineEmits(['submit', 'cancel'])

// 表单引用
const formRef = ref(null)

// 表单数据
const form = ref({
  id: '',
  username: '',
  email: '',
  role: 'student',
  password: ''
})

// 表单规则
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在2-20之间', trigger: 'blur' }
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { validator: validateEmail, message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ],
  password: [
    { required: true, message: '请输入初始密码', trigger: 'blur' },
    { min: 6, message: '密码长度至少6位', trigger: 'blur' }
  ]
}

// 邮箱验证函数
const validateEmail = (rule, value, callback) => {
  if (isValidEmail(value)) {
    callback()
  } else {
    callback(new Error('请输入有效的邮箱地址'))
  }
}

// 编辑模式下初始化表单数据
watch(
  () => props.initialData,
  (val) => {
    if (val.id) {
      form.value = { ...val }
    }
  },
  { immediate: true }
)

// 提交表单
const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    emit('submit', { ...form.value })
  } catch (error) {
    // 表单验证失败
  }
}

// 取消
const handleCancel = () => {
  emit('cancel')
  formRef.value?.resetFields()
}

// 暴露重置方法
defineExpose({
  reset: () => formRef.value?.resetFields()
})
</script>
