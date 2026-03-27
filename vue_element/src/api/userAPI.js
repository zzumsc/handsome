// src/api/userAPI.js
import request from '../utils/request'

// 发送验证码（对应 LoginController 的 /user/send 接口）
export const sendEmailCode = (email) => {
    return request({
        url: '/user/send',
        method: 'GET',
        params: { email }
    })
}

// 邮箱登录（对应 LoginController 的 /user/login 接口）
export const loginByEmail = (email, code) => {
    return request({
        url: '/user/login',
        method: 'POST',
        params: { email, code }
    })
}

// 新增退出登录接口
export const logoutAPI = () => {
  return request({
    url: '/user/logout',
    method: 'POST'
  })
}