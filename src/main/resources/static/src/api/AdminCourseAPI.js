import request from '../utils/request'


export const addCourse = (course) => {
    return request({
        url: '/admin/courses',
        method: 'POST',
        data: course
    })
}


export const deleteCourse = (id) => {
    return request({
        url: `/admin/courses/${id}`,
        method: 'DELETE'
    })
}


export const getCourseById = (id) => {
    return request({
        url: `/admin/courses/${id}`,
        method: 'GET'
    })
}


export const updateCourse = (id, course) => {
    return request({
        url: `/admin/courses/${id}`,
        method: 'PUT',
        data: course
    })
}


export const startCourseSelection = (id) => {
    return request({
        url: `/admin/courses/${id}/start`,
        method: 'PUT'
    })
}


export const endCourseSelection = (id) => {
    return request({
        url: `/admin/courses/${id}/end`,
        method: 'PUT'
    })
}


export const getStudentsByCourseId = (id) => {
    return request({
        url: `/admin/courses/${id}/students`,
        method: 'GET'
    })
}


export const batchStartCourseSelection = (courseIds) => {
    return request({
        url: '/admin/courses/batch/start',
        method: 'PUT',
        data: courseIds
    })
}


export const batchEndCourseSelection = (courseIds) => {
    return request({
        url: '/admin/courses/batch/end',
        method: 'PUT',
        data: courseIds
    })
}


export const getAllCourses = (params) => {
    return request({
        url: '/admin/courses',
        method: 'GET',
        params
    })
}
