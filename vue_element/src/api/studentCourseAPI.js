import request from '../utils/request'

/**
 * 1. 获取可选课程列表（对应后端 GET /student/courses）
 */
export const listAvailableCourses = () => {
    return request({
        url: '/student/courses',
        method: 'GET'
    })
}

/**
 * 2. 选课操作（对应后端 POST /student/courses/{courseId}/select）
 * @param {number} courseId - 课程ID
 */
export const selectCourse = (courseId) => {
    return request({
        url: `/student/courses/${courseId}/select`,
        method: 'POST'
    })
}

/**
 * 3. 退课操作（对应后端 POST /student/courses/{courseId}/drop）
 * @param {number} courseId - 课程ID
 */
export const dropCourse = (courseId) => {
    return request({
        url: `/student/courses/${courseId}/drop`,
        method: 'POST'
    })
}

/**
 * 4. 获取学生已选课程（对应后端 GET /student/selections）
 */
export const getMySelections = () => {
    return request({
        url: '/student/selections',
        method: 'GET'
    })
}
