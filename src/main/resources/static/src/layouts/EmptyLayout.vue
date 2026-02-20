<template>
    <div class="empty-layout">
        <router-view />
    </div>
</template>

<script setup>
// 空布局：仅作为路由出口，无导航栏等元素
// 用于登录页面等不需要布局框架的页面
</script>

<style scoped>
    .empty-layout {
        min-height: 100vh;
        display: flex;
        flex-direction: column;
    }
</style>
