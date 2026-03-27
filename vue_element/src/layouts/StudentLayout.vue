<template>
    <div class="student-layout">
        <!-- 顶部导航 -->
        <header class="header">
            <div class="logo">小帅选课系统 - 学生端</div>
            <el-menu mode="horizontal"
                     :default-active="activeMenu"
                     class="header-menu"
                     @select="handleMenuSelect">
                <el-menu-item index="/student/course-list">
                    <Document icon="Document" />
                    <span>课程列表</span>
                </el-menu-item>
                <el-menu-item index="/student/my-courses">
                    <Check icon="Check" />
                    <span>已选课程</span>
                </el-menu-item>
                <el-menu-item index="/student/profile">
                    <User icon="User" />
                    <span>个人中心</span>
                </el-menu-item>
            </el-menu>
            <div class="user-info">
                <span>积分：<span class="point">{{ formatPoint(userStore.userInfo.points) }}</span></span>
                <el-button type="text" @click="handleLogout">
                    <template #icon>
                        <ArrowRight />
                    </template>
                    退出登录
                </el-button>
            </div>
        </header>

        <!-- 页面内容 -->
        <main class="content">
            <router-view />
        </main>
    </div>
</template>

<script setup>
    import { ref, watch, onMounted } from 'vue';
    import { useRouter } from 'vue-router'
    import { useUserStore } from '../store/userStore'
    import { useRoute } from 'vue-router'
    import { ElMessageBox } from 'element-plus'
    import { Document, Check, User, ArrowRight } from '@element-plus/icons-vue'
    import { formatPoint } from '../utils/format'
    import { getMyPoints } from '../api/studentAPI'


    // 用户状态和路由
    const userStore = useUserStore()
    const route = useRoute()
    const activeMenu = ref(route.path)

    // 监听路由变化，更新选中的菜单
    watch(
      () => route.path,
      (val) => {
        activeMenu.value = val
      }
    )

    // 菜单选择事件
        const router = useRouter()
        const handleMenuSelect = (path) => {
            router.push(path) 
        }

    // 退出登录
    const handleLogout = () => {
      ElMessageBox.confirm('确定要退出登录吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info'
      }).then(() => {
        userStore.logout()
        window.location.href = '/user/login'
      })
        }

    onMounted(async () => {
        try {
            const res = await getMyPoints()
            if (res.code === 0) {
                // 将接口返回的积分更新到 userStore
                userStore.userInfo = {
                    ...userStore.userInfo,
                    points: res.data.point
                }
            } else {
                ElMessage.error(res.msg || '获取积分失败')
            }
        } catch (error) {
            ElMessage.error('网络错误，获取积分失败')
            console.error('获取积分异常：', error)
        }
    })
</script>

<style scoped>
    .student-layout {
        display: flex;
        flex-direction: column;
        min-height: 100vh;
    }

    .header {
        height: 60px;
        border-bottom: 1px solid #e5e7eb;
        display: flex;
        align-items: center;
        padding: 0 20px;
        background: #fff;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
    }

    .logo {
        font-size: 18px;
        font-weight: 500;
        color: #2563eb;
        width: 200px;
    }

    .header-menu {
        flex: 1;
        border-bottom: none;
    }

    .user-info {
        display: flex;
        align-items: center;
        gap: 20px;
        width: 250px;
        justify-content: flex-end;
    }

    .point {
        color: #2563eb;
        font-weight: 500;
    }

    .content {
        flex: 1;
        padding: 20px;
        overflow-y: auto;
        background: #f8fafc;
    }
</style>
