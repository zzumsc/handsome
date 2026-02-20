<template>
    <div class="admin-layout">
        <!-- 侧边栏 -->
        <aside class="sidebar">
            <div class="sidebar-header">
                <h1>小帅选课系统</h1>
                <p>管理员端</p>
            </div>
            <el-menu :default-active="$route.path"
                     class="sidebar-menu"
                     mode="vertical"
                     @select="handleMenuSelect">
                <!-- 侧边栏菜单 -->
                <el-menu-item :index="'/admin/user-manage'">
                    <template #icon>
                        <User />
                    </template>
                    <span>用户管理</span>
                </el-menu-item>
                <el-menu-item :index="'/admin/course-manage'">
                    <template #icon>
                        <Document />
                    </template>
                    <span>课程管理</span>
                </el-menu-item>
            </el-menu>
        </aside>

        <!-- 主内容区 -->
        <div class="main-container">
            <el-header class="header">
                <div class="header-right">
                    <span class="username">欢迎，{{ userStore.name || '管理员' }}</span>

                    <el-button type="text" @click="handleLogout">
                        <template #icon>
                            <ArrowRight />
                        </template>
                        退出登录
                    </el-button>
                </div>
            </el-header>

            <main class="content">
                <router-view />
            </main>
        </div>
    </div>
</template>

<script setup>
    import { useUserStore } from '../store/userStore'
    import { ElMessageBox } from 'element-plus'
    import { User, Document, ArrowRight } from '@element-plus/icons-vue'
    import { useRouter } from 'vue-router'

    const userStore = useUserStore()
    const router = useRouter()

    // 菜单选择事件：处理路由跳转
    const handleMenuSelect = (path) => {
        router.push(path)
    }

    // 退出登录逻辑
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
</script>

<style scoped>
    .admin-layout {
        display: flex;
        min-height: 100vh;
    }

    .sidebar {
        width: 220px;
        background: #1e293b;
        color: #fff;
        display: flex;
        flex-direction: column;
    }

    .sidebar-header {
        padding: 20px;
        text-align: center;
        border-bottom: 1px solid #334155;
    }

        .sidebar-header h1 {
            margin: 0 0 8px;
            font-size: 18px;
        }

        .sidebar-header p {
            margin: 0;
            font-size: 14px;
            color: #94a3b8;
        }

    ::v-deep .sidebar-menu {
        flex: 1;
        border-right: none;
        background: transparent;
        color: #e2e8f0;
    }

    ::v-deep .el-menu-item {
        color: #e2e8f0 !important;
        background: transparent !important;
        height: 50px;
        margin: 8px 0;
    }

        ::v-deep .el-menu-item.is-active {
            background: #334155 !important;
            color: #fff !important;
            font-weight: 500;
        }

    .main-container {
        flex: 1;
        display: flex;
        flex-direction: column;
    }

    .header {
        height: 60px;
        border-bottom: 1px solid #e5e7eb;
        display: flex;
        justify-content: flex-end;
        align-items: center;
        padding: 0 20px;
        background: #fff;
    }

    .header-right {
        display: flex;
        align-items: center;
        gap: 20px;
    }

    .username {
        color: #4b5563;
        font-size: 14px;
    }

    .content {
        flex: 1;
        padding: 20px;
        overflow-y: auto;
        background: #f8fafc;
    }
</style>
