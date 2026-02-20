import axios from 'axios'
import { useUserStore } from '../store/userStore'
import { ElMessage } from 'element-plus'
import { router } from '../router'  // 新增：导入路由

// 创建axios实例
const request = axios.create({
    baseURL: '/api',
    timeout: 5000,
    headers: {
        'Content-Type': 'application/json'
    }
})

// 请求拦截器
request.interceptors.request.use(
    (config) => {
        const userStore = useUserStore()
        if (userStore.token) {  // 现在 token 已在 userStore 中定义
            config.headers.Authorization = `Bearer ${userStore.token}`
        }
        return config
    },
    (error) => {
        return Promise.reject(error)
    }
)

// 响应拦截器
request.interceptors.response.use(
    response => {
        const res = response.data
        if (res.code !== 0) {
            ElMessage.error(res.msg || '请求失败')
            return Promise.reject(new Error(res.msg || '请求失败'))
        }
        return res
    },
    error => {
        if (error.response && error.response.status === 401) {
            const userStore = useUserStore()
            userStore.logout()
            router.push('/user/login')  // 已导入 router，可正常使用
            ElMessage.warning('登录已过期，请重新登录')
        }
        return Promise.reject(error)
    }
)

export default request