<template>
  <div v-if="!token" style="display:flex;align-items:center;justify-content:center;height:100vh;">
    <Login @ok="onLoginOk" />
  </div>
  <el-container v-else style="height:100vh;">
    <el-header style="background:#1f2329;color:#fff;display:flex;align-items:center;justify-content:space-between;padding:0 18px;">
      <div style="display:flex;align-items:center;gap:14px;">
        <span style="font-size:16px;font-weight:700;">🛠 AIOA 管理端</span>
        <span class="muted" style="color:#a8aeba;font-size:12px;">租户: {{ tenantCode }} · {{ user?.displayName || user?.username }}</span>
      </div>
      <div>
        <el-button type="primary" link @click="reloadAll" :icon="Refresh">刷新</el-button>
        <el-button type="danger" link @click="logout">退出</el-button>
      </div>
    </el-header>
    <el-container>
      <el-aside width="170px" style="background:#fff;border-right:1px solid #eef0f5;">
        <el-menu :default-active="tab" @select="(i:any)=>tab=i" style="border:none;">
          <el-menu-item index="dashboard"><el-icon><DataAnalysis/></el-icon>仪表盘</el-menu-item>
          <el-menu-item index="pending"><el-icon><Bell/></el-icon>待审批 <el-badge v-if="pendingCount" :value="pendingCount" :max="99" class="menu-badge"/></el-menu-item>
          <el-menu-item index="sessions"><el-icon><ChatDotRound/></el-icon>会话</el-menu-item>
          <el-menu-item index="bills"><el-icon><Money/></el-icon>账单</el-menu-item>
          <el-menu-item index="logs"><el-icon><Document/></el-icon>操作记录</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main style="background:#f4f6fa;padding:18px;overflow:auto;">
        <Dashboard v-if="tab==='dashboard'" :refresh-key="refreshKey"/>
        <Pending   v-if="tab==='pending'"   :refresh-key="refreshKey" @changed="onPendingChanged"/>
        <Sessions  v-if="tab==='sessions'"  :refresh-key="refreshKey"/>
        <Bills     v-if="tab==='bills'"     :refresh-key="refreshKey"/>
        <Logs      v-if="tab==='logs'"      :refresh-key="refreshKey"/>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
import { ref, onMounted, provide } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, DataAnalysis, Bell, ChatDotRound, Money, Document } from '@element-plus/icons-vue'
import { AuthAPI, TodoAPI } from './api'
import Login from './views/Login.vue'
import Dashboard from './views/Dashboard.vue'
import Pending from './views/Pending.vue'
import Sessions from './views/Sessions.vue'
import Bills from './views/Bills.vue'
import Logs from './views/Logs.vue'

const token = ref(localStorage.getItem('aioa_admin_token') || '')
const user = ref<any>(JSON.parse(localStorage.getItem('aioa_admin_user') || 'null'))
const tenantCode = ref(localStorage.getItem('aioa_admin_tenant') || 'default')
const tab = ref('dashboard')
const pendingCount = ref(0)
const refreshKey = ref(0)

async function reloadAll() {
  refreshKey.value++
  try {
    const r: any = await TodoAPI.count()
    if (r.code === 0) pendingCount.value = r.data?.pending || 0
  } catch {}
}
provide('reloadAll', reloadAll)

async function onLoginOk() {
  token.value = localStorage.getItem('aioa_admin_token') || ''
  user.value = JSON.parse(localStorage.getItem('aioa_admin_user') || 'null')
  await reloadAll()
}

function onPendingChanged() {
  reloadAll()
}

function logout() {
  localStorage.clear()
  token.value = ''
  location.reload()
}

onMounted(reloadAll)
</script>

<style>
.menu-badge { margin-left: 6px; }
</style>