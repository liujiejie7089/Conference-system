<template>
  <el-card style="width:380px;">
    <template #header>
      <div style="text-align:center;font-weight:700;font-size:16px;">AIOA 管理端登录</div>
    </template>
    <el-form :model="form" label-width="60px">
      <el-form-item label="账号"><el-input v-model="form.username" placeholder="admin" /></el-form-item>
      <el-form-item label="密码"><el-input v-model="form.password" type="password" placeholder="admin123" show-password /></el-form-item>
      <el-form-item label="租户"><el-input v-model="form.tenantCode" placeholder="default" /></el-form-item>
      <el-button type="primary" style="width:100%;" @click="submit" :loading="loading">登 录</el-button>
      <div class="muted" style="margin-top:10px;text-align:center;">默认 admin / admin123 / default</div>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { AuthAPI } from '../api'

const emit = defineEmits<{ (e:'ok'): void }>()
const form = ref({ username: 'admin', password: 'admin123', tenantCode: 'default' })
const loading = ref(false)

async function submit() {
  loading.value = true
  try {
    const r: any = await AuthAPI.sso(form.value.username, form.value.password, form.value.tenantCode)
    if (r.code === 0) {
      localStorage.setItem('aioa_admin_token', r.data.token)
      localStorage.setItem('aioa_admin_user', JSON.stringify(r.data.user))
      localStorage.setItem('aioa_admin_tenant', form.value.tenantCode)
      ElMessage.success('登录成功')
      emit('ok')
    }
  } finally { loading.value = false }
}
</script>