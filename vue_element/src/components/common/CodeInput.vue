<template>
    <div class="code-input-container">
        <el-input v-model="code"
                  placeholder="请输入6位验证码"
                  maxlength="6"
                  :disabled="disabled"
                  @input="handleCodeInput"></el-input>
        <el-button type="text"
                   :disabled="countdown > 0 || isSending"
                   @click="onSendCode"
                   class="send-button">
            {{ countdown > 0 ? `${countdown}s后重发` : '发送验证码' }}
        </el-button>
    </div>
</template>

<script setup>
import { defineProps, defineEmits, ref, watch } from 'vue'
import { isValidCode } from '../../utils/codeUtils'

// 组件参数
const props = defineProps({
  // 绑定的验证码值
  modelValue: {
    type: String,
    default: ''
  },
  // 是否禁用
  disabled: {
    type: Boolean,
    default: false
  },
  // 邮箱地址（用于发送验证码）
  email: {
    type: String,
    default: ''
  }
})

// 组件事件
const emit = defineEmits(['update:modelValue', 'send', 'valid'])

// 内部状态
const code = ref(props.modelValue)
const countdown = ref(0)
const isSending = ref(false)

// 监听外部值变化
watch(
  () => props.modelValue,
  (val) => {
    code.value = val
  }
)

// 监听验证码输入
const handleCodeInput = (val) => {
  // 只保留数字
  const filtered = val.replace(/[^\d]/g, '')
  code.value = filtered
  emit('update:modelValue', filtered)
  // 验证是否为6位数字
  emit('valid', isValidCode(filtered))
}

// 发送验证码
const onSendCode = async () => {
    isSending.value = true
    try {
        await emit('send', props.email) // 等待父组件异步操作完成
    } finally {
        isSending.value = false
    }
}

// 提供外部控制倒计时的方法
const startCountdown = () => {
  countdown.value = 60
  const timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
    }
  }, 1000)
}

// 暴露方法给父组件
defineExpose({ startCountdown })
</script>

<style scoped>
    .code-input-container {
        display: flex;
        gap: 12px;
        align-items: center;
    }

    .send-button {
        white-space: nowrap;
    }
</style>
