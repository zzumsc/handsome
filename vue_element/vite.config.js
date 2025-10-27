import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue' // 确保已安装该插件

export default defineConfig({
    plugins: [vue()], // 必须有这行，否则无法解析.vue文件
    server: {
        proxy: {
            '/api': { // 前端请求路径必须以/api开头，才会触发代理
                target: 'http://localhost:8081', 
                changeOrigin: true, // 解决跨域时的Origin校验问题
                rewrite: (path) => path.replace(/^\/api/, '') // 去除路径中的/api前缀
            }
        }
    }
})