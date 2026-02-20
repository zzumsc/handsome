/**
 * 校验邮箱格式
 * @param {string} email - 邮箱地址
 * @returns {boolean} 是否有效
 */
export const isValidEmail = (email) => {
    const reg = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/
    return reg.test(email)
}

/**
 * 校验验证码格式（6位数字）
 * @param {string} code - 验证码
 * @returns {boolean} 是否有效
 */
export const isValidCode = (code) => {
    return /^\d{6}$/.test(code)
}

/**
 * 开始验证码倒计时
 * @param {Function} updateCountdown - 更新倒计时的函数
 * @returns {Function} 清除倒计时的函数
 */
export const startCodeCountdown = (updateCountdown) => {
    let seconds = 60
    updateCountdown(seconds)

    const timer = setInterval(() => {
        seconds--
        updateCountdown(seconds)
        if (seconds <= 0) {
            clearInterval(timer)
        }
    }, 1000)

    return () => clearInterval(timer)
}
