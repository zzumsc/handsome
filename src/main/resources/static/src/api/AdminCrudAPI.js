import request from '../utils/request'


export const addUser = (user) => {
    return request({
        url: '/admin/crud',
        method: 'POST',
        data: user
    })
}


export const deleteUser = (id) => {
    return request({
        url: `/admin/crud/${id}`,
        method: 'DELETE'
    })
}


export const updateUser = (user) => {
    return request({
        url: '/admin/crud',
        method: 'PUT',
        data: user
    })
}


export const getUserById = (id) => {
    return request({
        url: `/admin/crud/${id}`,
        method: 'GET'
    })
}

export const getUserList = (query) => {
    const params = {}
    if (query.page !== undefined) params.page = query.page
    if (query.size !== undefined) params.size = query.size
    if (query.name !== undefined) params.name = query.name
    if (query.email !== undefined) params.email = query.email
    if (query.no !== undefined) params.no = query.no
    if (query.role !== undefined) params.role = query.role
    if (query.sortField !== undefined) params.sortField = query.sortField
    if (query.sortDir !== undefined) params.sortDir = query.sortDir

    return request({
        url: '/admin/crud',
        method: 'GET',
        params
    })
}


export const getSelections = (studentId) => {
    return request({
        url: '/admin/selections',
        method: 'GET',
        params: { studentId }
    })
}
