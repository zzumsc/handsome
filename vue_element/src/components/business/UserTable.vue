<template>
    <el-table :data="userList"
              border
              style="width: 100%"
              @selection-change="handleSelectionChange">
        <el-table-column type="selection"
                         width="55"></el-table-column>
        <el-table-column prop="id"
                         label="ID"
                         width="80"></el-table-column>
        <el-table-column prop="username"
                         label="用户名"></el-table-column>
        <el-table-column prop="email"
                         label="邮箱"></el-table-column>
        <el-table-column prop="role"
                         label="角色"
                         width="100">
            <template #default="scope">
                <el-tag :type="scope.row.role === 'admin' ? 'warning' : 'success'">
                    {{ scope.row.role === 'admin' ? '管理员' : '学生' }}
                </el-tag>
            </template>
        </el-table-column>
        <el-table-column prop="createTime"
                         label="创建时间"
                         width="180">
            <template #default="scope">
                {{ formatDate(scope.row.createTime) }}
            </template>
        </el-table-column>
        <el-table-column label="操作"
                         width="200">
            <template #default="scope">
                <el-button size="small"
                           type="primary"
                           @click="handleEdit(scope.row)">
                    编辑
                </el-button>
                <el-button size="small"
                           type="danger"
                           @click="handleDelete(scope.row.id)">
                    删除
                </el-button>
            </template>
        </el-table-column>
    </el-table>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'
import { formatDate } from '../../utils/format'

// 组件参数
const props = defineProps({
  // 用户列表数据
  userList: {
    type: Array,
    default: () => []
  }
})

// 组件事件
const emit = defineEmits(['edit', 'delete', 'selectionChange'])

// 编辑用户
const handleEdit = (row) => {
  emit('edit', row)
}

// 删除用户
const handleDelete = (id) => {
  emit('delete', id)
}

// 选择用户变化
const handleSelectionChange = (selection) => {
  emit('selectionChange', selection)
}
</script>
