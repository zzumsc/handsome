import request from '../utils/request'

/**
 * 1. 获取可选课程列表（含竞价人数、往年录取分）
 */
export const listAvailableCourses = () => {
    return request({
        url: '/student/courses',
        method: 'GET'
    })
}

/**
 * 2. 竞价预选课程
 * @param {number} courseId - 课程ID
 * @param {number} bidPoints - 投入积分
 */
export const bidCourse = (courseId, bidPoints) => {
    return request({
        url: `/student/courses/${courseId}/bid`,
        method: 'POST',
        data: { bidPoints }
    })
}

/**
 * 3. 修改竞价积分
 * @param {number} courseId - 课程ID
 * @param {number} bidPoints - 新投入积分
 */
export const updateBid = (courseId, bidPoints) => {
    return request({
        url: `/student/courses/${courseId}/bid`,
        method: 'PUT',
        data: { bidPoints }
    })
}

/**
 * 4. 取消预选（全额退还积分）
 * @param {number} courseId - 课程ID
 */
export const cancelBid = (courseId) => {
    return request({
        url: `/student/courses/${courseId}/bid`,
        method: 'DELETE'
    })
}

/**
 * 5. 查询我在某课程的竞价信息
 * @param {number} courseId - 课程ID
 */
export const getMyBid = (courseId) => {
    return request({
        url: `/student/courses/${courseId}/bid`,
        method: 'GET'
    })
}

/**
 * 6. 获取学生已录取课程（结算后的选课记录）
 */
export const getMySelections = () => {
    return request({
        url: '/student/selections',
        method: 'GET'
    })
}
