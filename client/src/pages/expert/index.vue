<template>
  <view class="expert">
    <view class="page-title">专家服务</view>
    <view class="muted">每位专家 = 人设 + 技能组合 + 专属知识库</view>

    <view v-if="loading" class="loading">加载中…</view>
    <view v-else>
      <view v-for="(a, i) in agents" :key="a.id" class="expert-row" @click="go(a)">
        <view class="avatar">{{ a.icon || a.name?.[0] || '?' }}</view>
        <view class="expert-info">
          <view class="expert-name">{{ a.name }}</view>
          <view class="muted desc">{{ a.description || '—' }}</view>
          <view class="tags">
            <text v-for="(t, ti) in a.tags" :key="ti" class="tag">{{ t }}</text>
          </view>
          <view v-if="a.intro" class="intro muted">{{ a.intro }}</view>
          <view v-if="a.recs?.length" class="recs">
            <text v-for="(r, ri) in a.recs" :key="ri" class="chip" @click.stop="quickAsk(a, r)">{{ r }}</text>
          </view>
        </view>
        <text class="muted arrow">›</text>
        <view v-if="i < agents.length - 1" class="divider"></view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { agentApi, type AgentVo } from '@/api';

interface AgentExt extends AgentVo {
  icon?: string;
  tags?: string[];
  intro?: string;
  recs?: string[];
}

const agents = ref<AgentExt[]>([]);
const loading = ref(true);

async function load() {
  loading.value = true;
  try {
    const r = await agentApi.list();
    if (r.code === 0) agents.value = (r.data || []).map(decorate);
  } finally {
    loading.value = false;
  }
}
onMounted(load);

function decorate(a: AgentVo): AgentExt {
  const name = a.name || '';
  let icon = '🧑‍💼', tags: string[] = [], intro = '', recs: string[] = [];
  if (name.includes('政策') || name.includes('咨询')) {
    icon = '🏛️';
    tags = ['政策问答', '申报指引'];
    intro = '熟悉国省市三级产业扶持政策，可基于你上传的政策文件精准解答。';
    recs = ['我市对企业上云有什么补贴？', 'OPC 设备联网改造怎么申报？', '算力券如何申领？'];
  } else if (name.includes('法律') || name.includes('合同')) {
    icon = '⚖️';
    tags = ['合同', '用工'];
    intro = '可对上传合同做初审并标注风险条款，输出修改建议。';
    recs = ['帮我审查这份采购合同的风险', '试用期辞退的合规要点？'];
  } else if (name.includes('开办') || name.includes('企业')) {
    icon = '🏢';
    tags = ['开办', '资质'];
    intro = '从核名到税务登记的全流程指引，自动匹配适用惠企政策。';
    recs = ['注册一家科技公司需要哪些材料？'];
  } else if (name.includes('公文') || name.includes('办文')) {
    icon = '📝';
    tags = ['公文', '润色'];
    intro = '按党政机关公文格式国标辅助起草与排版。';
    recs = ['把这段话改成规范的请示语气'];
  } else if (name.includes('差旅') || name.toLowerCase().includes('travel')) {
    icon = '✈️';
    tags = ['差旅', '查询'];
    intro = '差旅查询与申请一站式助手。';
    recs = ['查下周北京到上海的机票', '帮我提交一份差旅申请'];
  } else if (name.includes('审批')) {
    icon = '📋';
    tags = ['审批', '流转'];
    intro = '部门内审批流转，含人工审批节点。';
    recs = ['查询我的待审批', '发起一份请假审批'];
  } else if (name.includes('通用') || name.includes('助手')) {
    icon = '🧑‍💼';
    tags = ['问答', '通用'];
    intro = '日常问答与基础协作。';
    recs = ['今天天气怎么样？', '帮我写一段会议开头'];
  }
  return { ...a, icon, tags, intro, recs };
}

function go(a: AgentVo) {
  uni.navigateTo({ url: `/pages/chat/index?agentId=${a.id}&agentName=${encodeURIComponent(a.name)}` });
}
function quickAsk(a: AgentVo, q: string) {
  uni.navigateTo({ url: `/pages/chat/index?agentId=${a.id}&agentName=${encodeURIComponent(a.name)}&q=${encodeURIComponent(q)}` });
}
</script>

<style scoped>
.expert { padding: 14px; background: #f4f6fa; min-height: 100vh; }
.page-title { font-size: 18px; font-weight: 700; margin-bottom: 2px; }
.muted { color: #6b7486; font-size: 12px; margin-bottom: 12px; }
.loading { text-align: center; color: #6b7486; padding: 32px; }
.expert-row { display: flex; align-items: flex-start; gap: 10px; padding: 14px 0; background: #fff; border-radius: 14px; padding: 14px; margin-bottom: 12px; border: 1px solid #e6eaf2; cursor: pointer; }
.avatar { width: 42px; height: 42px; border-radius: 12px; background: linear-gradient(135deg, #eaf1ff, #d8e6ff); display: flex; align-items: center; justify-content: center; font-size: 22px; flex-shrink: 0; }
.expert-info { flex: 1; }
.expert-name { font-weight: 700; font-size: 14px; }
.desc { margin-top: 4px; min-height: 16px; }
.tags { margin-top: 6px; display: flex; gap: 4px; flex-wrap: wrap; }
.tag { display: inline-block; font-size: 10px; color: #2f6fed; background: #eaf1ff; border-radius: 4px; padding: 2px 6px; }
.intro { margin-top: 6px; line-height: 1.5; }
.recs { margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap; }
.chip { font-size: 11px; background: #fff; border: 1px solid #e6eaf2; border-radius: 12px; padding: 5px 10px; color: #6b7486; }
.arrow { font-size: 16px; align-self: center; }
.divider { height: 1px; background: #e6eaf2; margin-top: 10px; }
</style>
