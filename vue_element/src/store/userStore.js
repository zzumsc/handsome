// src/store/userStore.js
import { defineStore } from 'pinia'
import { sendEmailCode, loginByEmail } from '../api/userAPI'
//  引入“获取积分”接口（之前定义在 userApi.js 中）
import { getMyPoints } from '../api/studentAPI'
//  引入后端退出接口（需确保后端已实现）
import { logoutAPI } from '../api/userAPI'
import { ElMessage } from 'element-plus'
import { getMyInfo } from '../api/studentAPI'

export const useUserStore = defineStore('user', {
    state: () => ({
        role: localStorage.getItem('role') || '',
        //  1. 初始化 userInfo 时添加 points 字段，默认值 0（避免 undefined）
        userInfo: JSON.parse(localStorage.getItem('userInfo')) || {
            points: 0 // 积分字段：默认 0，后续会被接口数据覆盖
        },
        codeCountdown: 0,
        isCodeSending: false
    }),

    actions: {
        // 发送验证码逻辑不变
        async sendEmailCode(email) {
            if (this.isCodeSending) return
            this.isCodeSending = true
            try {
                await sendEmailCode(email)
                this.codeCountdown = 60
                const timer = setInterval(() => {
                    this.codeCountdown--
                    if (this.codeCountdown <= 0) clearInterval(timer)
                }, 1000)
            } finally {
                this.isCodeSending = false
            }
        },

        //  2. 登录逻辑：学生角色登录后，调用接口获取积分并更新
        async loginByEmail(email, code) {
            try {
                const res = await loginByEmail(email, code)
                const user = res.data.user

                // 基础角色和用户信息存储
                this.role = user.role
                this.userInfo = {
                    ...user,
                    points: user.points || 0 // 兼容后端返回：若有积分则用，没有则默认 0
                }
                localStorage.setItem('role', user.role)
                localStorage.setItem('userInfo', JSON.stringify(this.userInfo))

                //  关键：学生角色登录后，主动调用接口获取最新积分（覆盖初始值）
                if (this.role === 'student') {
                    try {
                        const pointsRes = await getMyPoints()
                        if (pointsRes.code === 0) {
                            // 更新积分到 userInfo 和本地存储
                            this.userInfo.points = pointsRes.data.point || 0
                            localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
                            ElMessage.success(`积分同步成功：${this.userInfo.points}`)
                        }
                    } catch (pointsError) {
                        // 积分获取失败不影响登录，但给提示
                        ElMessage.warning('积分同步失败，将显示默认值')
                        console.error('积分同步异常：', pointsError)
                    }
                }

            } catch (error) {
                ElMessage.error(error.message || '登录失败')
                throw error
            }
        },

        // 3. 退出逻辑：清空积分（随 userInfo 一起清空）
        async logout() {
            try {
                await logoutAPI() // 调用后端退出接口，清除 Session
            } catch (logoutError) {
                ElMessage.warning('后端退出接口调用失败，已强制清除本地状态')
                console.error('退出接口异常：', logoutError)
            } finally {
                // 清空所有状态（含积分）
                this.role = ''
                this.userInfo = { points: 0 } // 重置为默认值
                this.codeCountdown = 0
                // 清空本地存储
                localStorage.removeItem('role')
                localStorage.removeItem('userInfo')
            }
        }
    }
})