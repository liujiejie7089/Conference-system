<template>
  <div>
    <div class="page-card">
      <div class="page-title">🔔 审批工单 <span class="muted">(approval_tickets · status=1)</span>
        <el-button type="primary" link style="float:right" @click="load">刷新</el-button>
      </div>
      <el-table :data="tickets" stripe size="small" empty-text="无待审批工单">
        <el-table-column prop="idStr" label="工单号" width="180"/>
        <el-table-column label="会话" width="100"><template #default="{row}">#{{ row.sessionId }}</template></el-table-column>
        <el-table-column prop="inputPayload" label="内容"/>
        <el-table-column prop="createdAt" label="发起时间" width="170"/>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{row}">
            <el-button type="success" size="small" @click="act(row, 'approve')">通过</el-button>
            <el-button type="danger"  size="small" @click="act(row, 'reject')">驳回</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="page-card">
      <div class="page-title">📥 待处理技能表单 <span class="muted">(skill_forms · status=1)</span></div>
      <el-table :data="forms" stripe size="small" empty-text="无待处理表单">
        <el-table-column prop="icon" label="" width="40"><template #default="{row}">{{ row.icon }}</template></el-table-column>
        <el-table-column prop="title" label="标题" min-width="220"/>
        <el-table-column prop="applicantName" label="发起人" width="100"/>
        <el-table-column prop="sourceLabel" label="来源" width="120"/>
        <el-table-column prop="summary" label="摘要"/>
        <el-table-column prop="submittedAt" label="提交时间" width="170"/>
      </el-table>
      <div class="muted" style="margin-top:8px;">技能表单由用户端提交,管理端可在此查阅详情;审批动作会通过 inject 接口将结果回写到用户端会话,FR-D6 闭环可查。</div>
    </div>

    <el-dialog v-model="dlg.show" :title="dlg.type==='approve'?'确认通过':'确认驳回'" width="420px">
      <p>工单号 <b>{{ dlg.ticket?.idStr }}</b></p>
      <p class="muted">{{ dlg.ticket?.inputPayload }}</p>
      <el-input v-model="dlg.opinion" type="textarea" :rows="3" placeholder="审批意见(可选)"/>
      <template #footer>
        <el-button @click="dlg.show=false">取消</el-button>
        <el-button :type="dlg.type==='approve'?'success':'danger'" @click="confirm" :loading="dlg.loading">确认{{ dlg.type==='approve'?'通过':'驳回' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ApprovalAPI, TodoAPI } from '../api'

const props = defineProps<{ refreshKey: number }>()
const emit = defineEmits<{ (e:'changed'): void }>()

const tickets = ref<any[]>([])
const forms = ref<any[]>([])

const dlg = reactive({ show: false, type: 'approve', ticket: null as any, opinion: '', loading: false })

async function load() {
  const [a, b]: any = await Promise.all([ApprovalAPI.listPending(), TodoAPI.pending()])
  if (a.code === 0) tickets.value = a.data || []
  if (b.code === 0) forms.value = (b.data || []).filter((x: any) => x.source === 'skill_form')
}

function act(row: any, type: 'approve'|'reject') {
  dlg.ticket = row; dlg.type = type; dlg.opinion = ''; dlg.show = true
}

async function confirm() {
  dlg.loading = true
  try {
    const id = Number(dlg.ticket.idStr) || dlg.ticket.id
    const fn = dlg.type === 'approve' ? ApprovalAPI.approve : ApprovalAPI.reject
    const r: any = await fn(id, dlg.opinion)
    if (r.code === 0) {
      ElMessage.success(dlg.type === 'approve' ? '已通过,结果已回注会话' : '已驳回,结果已回注会话')
      dlg.show = false
      await load()
      emit('changed')
    }
  } finally { dlg.loading = false }
}

watch(() => props.refreshKey, load, { immediate: true })
</script>