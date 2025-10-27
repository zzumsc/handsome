// src/api/studentAPI.js
import request from '../utils/request'

/**
 * 查询学生列表（对应后端StudentController的分页查询接口）
 * @param {Object} params - { pageNum, pageSize, keyword }
 * @returns {Promise}
 */
export const getStudentList = (params) => {
    return request({
        url: '/admin/students',  // 后端学生管理接口路径
        method: 'GET',
        params  // GET请求参数放在params
    })
}

/**
 * 新增学生（对应后端POST接口）
 * @param {Object} data - 学生信息 { studentNo, name, email, role, password }
 * @returns {Promise}
 */
export const createStudent = (data) => {
    return request({
        url: '/admin/students',
        method: 'POST',
        data  // POST请求数据放在data
    })
}

/**
 * 更新学生（对应后端PUT接口）
 * @param {number} id - 学生ID
 * @param {Object} data - 要更新的字段
 * @returns {Promise}
 */
export const updateStudent = (id, data) => {
    return request({
        url: `/admin/students/${id}`,  // 路径参数传递ID
        method: 'PUT',
        data
    })
}

/**
 * 删除学生（对应后端DELETE接口）
 * @param {number} id - 学生ID
 * @returns {Promise}
 */
export const deleteStudent = (id) => {
    return request({
        url: `/admin/students/${id}`,
        method: 'DELETE'
    })
}

export const getMyInfo = () => {
    return request({
        url: '/student/info',  // 后端获取当前用户信息的接口路径
        method: 'GET'
    })
}

export const updateMyInfo = (data) => {
    return request({
        url: '/student/info',  // 对应后端更新用户信息的接口路径
        method: 'PUT',      // 通常更新用PUT方法
        data                // 要更新的用户信息数据
    })
}

export function getMyPoints() {
    return request({
        url: '/student/points',
        method: 'get'
    })
}