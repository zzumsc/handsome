import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '../store/userStore'
import { ElMessage } from 'element-plus'

// 布局组件
import EmptyLayout from '../layouts/EmptyLayout.vue'
import AdminLayout from '../layouts/AdminLayout.vue'
import StudentLayout from '../layouts/StudentLayout.vue'

// 公共页面
const Login = () => import('../views/Login.vue')

// 管理员页面
const AdminUserManage = () => import('../views/admin/UserManage.vue')
const AdminCourseManage = () => import('../views/admin/CourseManage.vue')
const AdminCourseStudents = () => import('../views/admin/CourseStudents.vue')

// 学生页面
const StudentProfile = () => import('../views/student/Profile.vue')
const StudentCourseList = () => import('../views/student/CourseList.vue')
const StudentMyCourses = () => import('../views/student/MyCourses.vue')

// 404页面
const NotFound = () => import('../views/NotFound.vue')

// 路由规则（严格对齐后端Controller接口路径）
export const router = createRouter({
    history: createWebHistory(),
    routes: [
        // 公共路由（无需登录）
        {
            path: '/user',
            component: EmptyLayout,
            children: [
                { path: 'login', name: 'Login', component: Login }
            ]
        },

        // 管理员路由（需admin权限）
        {
            path: '/admin',
            component: AdminLayout,
            meta: { requiresAuth: true, role: 'admin' },
            children: [
                { path: 'user-manage', name: 'AdminUserManage', component: AdminUserManage }, // 对应CrudController
                { path: 'course-manage', name: 'AdminCourseManage', component: AdminCourseManage }, // 对应AdminCourseController
                { path: 'course-students/:id', name: 'AdminCourseStudents', component: AdminCourseStudents } // 对应AdminCourseController的/students接口
            ]
        },

        // 学生路由（需student权限）
        {
            path: '/student',
            component: StudentLayout,
            meta: { requiresAuth: true, role: 'student' },
            children: [
                { path: 'profile', name: 'StudentProfile', component: StudentProfile }, // 对应StudentController
                { path: 'course-list', name: 'StudentCourseList', component: StudentCourseList }, // 对应StudentCourseController
                { path: 'my-courses', name: 'StudentMyCourses', component: StudentMyCourses } // 对应StudentController的/selections接口
            ]
        },

        // 路由重定向
        { path: '/', redirect: '/user/login' },
        { path: '/admin', redirect: '/admin/course-manage' },
        { path: '/student', redirect: '/student/course-list' },

        // 404页面
        { path: '/:pathMatch(.*)*', name: 'NotFound', component: NotFound }
    ]
})

// router/index.js 中的路由守卫
router.beforeEach((to, from, next) => {
    const userStore = useUserStore()
    const { requiresAuth, role: requiredRole } = to.meta

    // 1. 公共路由直接放行（如登录页）
    if (!requiresAuth) {
        next()
        return
    }

    // 2. 需要登录的路由：不检查 Token，仅检查前端存储的 role（用于快速判断）
    // 实际登录状态由后端通过 Session 验证，若未登录后端会返回 401/302
    if (!userStore.role) {
        ElMessage.warning('请先登录')
        next('/user/login')
        return
    }

    // 3. 角色权限校验（和之前一致）
    if (requiredRole && userStore.role !== requiredRole) {
        ElMessage.error('无权限访问')
        const redirectPath = userStore.role === 'admin' ? '/admin' : '/student'
        next(redirectPath)
        return
    }

    // 4. 放行（实际登录状态由后端保证）
    next()
})

