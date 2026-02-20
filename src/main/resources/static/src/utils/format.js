// src/utils/format.js
/**
 * 格式化积分：保留3位小数（匹配后端DECIMAL类型）
 * @param {number|string} point - 原始积分
 * @returns {string} 格式化后的积分
 */
export const formatPoint = (point) => {
    // 新增空值和无效值处理
    if (point === null || point === undefined || isNaN(Number(point))) {
        return '0.000'
    }
    return Number(point).toFixed(3)
}

/**
 * 格式化日期：YYYY-MM-DD HH:MM
 * @param {string|Date} date - 原始日期
 * @returns {string} 格式化后的日期
 */
export const formatDate = (date) => {
    // 新增空值和无效值处理
    if (!date || date === 'null' || date === 'undefined') return ''

    // 处理时间戳格式
    if (typeof date === 'number') {
        return formatDate(new Date(date))
    }

    const d = new Date(date)
    // 验证日期有效性
    if (isNaN(d.getTime())) return ''

    return [
        d.getFullYear(),
        String(d.getMonth() + 1).padStart(2, '0'),
        String(d.getDate()).padStart(2, '0')
    ].join('-') + ' ' + [
        String(d.getHours()).padStart(2, '0'),
        String(d.getMinutes()).padStart(2, '0')
    ].join(':')
}

/**
 * 格式化课程状态（匹配后端状态值）
 * @param {string} status - 后端返回的状态（published/closed/draft）
 * @returns {Object} { text: 显示文本, type: ElementPlus标签类型 }
 */
export const formatCourseStatus = (status) => {
    // 新增状态为空的处理
    if (!status) return { text: '未知状态', type: 'warning' }

    const statusMap = {
        published: { text: '竞价中', type: 'success' },
        closed: { text: '已结束', type: 'danger' },
        draft: { text: '未发布', type: 'info' }
    }
    return statusMap[status] || { text: '未知状态', type: 'warning' }
}