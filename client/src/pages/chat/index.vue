<template>
  <view class="chat">
    <view class="header">
      <view class="back" @click="back">‹</view>
      <view class="title">{{ agentName || sessionTitle || '对话' }}</view>
      <view class="muted model-tag">模型：智能路由</view>
    </view>

    <scroll-view class="msgs" scroll-y="true" :scroll-top="scrollTop" :scroll-with-animation="true">
      <view v-for="m in messages" :key="m.id" :class="['msg', m.role === 'user' ? 'right' : 'left']">
        <view class="bubble">{{ m.content }}
          <view v-if="m.toolInfo" class="tool-info">{{ m.toolInfo }}</view>
          <view v-if="m.meta" class="meta-line">{{ m.meta }}</view>
          <view v-if="m.citations?.length" class="citations">
            <text v-for="(c, i) in m.citations" :key="i" class="cite">📎 来源：{{ c.title }}</text>
          </view>
          <view v-if="m.approve" class="approve-card">
            <text>⚠️ {{ m.approve.reason }}</text>
            <view class="approve-row">
              <button class="btn small" @click="submitApproval(m)">提交审批</button>
              <button class="btn ghost small" @click="cancelApproval(m)">取消</button>
            </view>
          </view>
        </view>
      </view>
      <view v-if="streaming" class="msg left">
        <view class="bubble typing">{{ streamText || '思考中…' }}<text class="cursor">▍</text></view>
      </view>
      <view v-if="quotaExhausted" class="msg left">
        <view class="bubble">
          今日免费额度已用完。你可以：
          ① 等待明日免费额度发放
          ② 购买词元包（首页入口）
          ③ 联系管理员开通企业套餐
        </view>
      </view>
    </scroll-view>

    <view v-if="recommendations.length && !streaming && messages.length <= 1" class="recs-bar">
      <text v-for="(r, i) in recommendations" :key="i" class="chip" @click="quickAsk(r)">{{ r }}</text>
    </view>

    <view class="composer">
      <view class="kb-chip" :class="{ off: !kbOn }" @click="toggleKB">📚 知识库 {{ kbOn ? '开' : '关' }}</view>
      <textarea
        v-model="input"
        class="input"
        placeholder="输入问题…"
        auto-height
        :maxlength="500"
        confirm-type="send"
        @confirm="send"
      />
      <button class="send" :disabled="!input || streaming" @click="send">发送</button>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue';
import { onLoad } from '@dcloudio/uni-app';
import { sessionApi, ledgerApi, streamChat } from '@/api';

interface Msg {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  meta?: string;
  citations?: { title: string; url?: string }[];
  approve?: { reason: string };
  toolInfo?: string;
}

const input = ref('');
const messages = reactive<Msg[]>([]);
const streaming = ref(false);
const streamText = ref('');
const scrollTop = ref(0);

const sessionId = ref<string>('');
const agentId = ref<string>('');
const agentName = ref('');
const sessionTitle = ref('');
const presetQuestion = ref('');
const kbOn = ref(true);
const quotaExhausted = ref(false);
const recommendations = ref<string[]>([]);

onLoad((opts: any) => {
  agentId.value = opts.agentId || '';
  agentName.value = opts.agentName ? decodeURIComponent(opts.agentName) : '';
  sessionId.value = opts.sessionId || '';
  sessionTitle.value = opts.title || '';
  presetQuestion.value = opts.q ? decodeURIComponent(opts.q) : '';
});

onMounted(async () => {
  if (!sessionId.value && agentId.value) {
    try {
      const r = await sessionApi.create({
        agentId: agentId.value ? Number(agentId.value) : undefined,
        title: agentName.value ? `${agentName.value}会话` : '新会话'
      });
      if (r.code === 0 && r.data) {
        sessionId.value = r.data.id;
        sessionTitle.value = r.data.title;
      }
    } catch { /* ignore */ }
  }
  // 欢迎气泡 + 推荐问题
  if (agentName.value) {
    recommendations.value = pickRecs(agentName.value);
    messages.push({
      id: Math.random().toString(36),
      role: 'assistant',
      content: `你好，我是${agentName.value}。${pickIntro(agentName.value)}`
    });
  }
  // 预填问题
  if (presetQuestion.value) {
    input.value = presetQuestion.value;
    setTimeout(() => send(), 300);
  }
});

function pickIntro(name: string): string {
  if (name.includes('政策') || name.includes('咨询')) return '熟悉国省市三级产业扶持政策，可基于你上传的政策文件精准解答。';
  if (name.includes('法律') || name.includes('合同')) return '可对上传合同做初审并标注风险条款，输出修改建议。';
  if (name.includes('开办') || name.includes('企业')) return '从核名到税务登记的全流程指引，自动匹配适用惠企政策。';
  if (name.includes('公文') || name.includes('办文')) return '按党政机关公文格式国标辅助起草与排版。';
  if (name.includes('差旅')) return '差旅查询与申请一站式助手。';
  if (name.includes('审批')) return '部门内审批流转，含人工审批节点。';
  return '日常问答与基础协作。';
}
function pickRecs(name: string): string[] {
  if (name.includes('政策') || name.includes('咨询')) return ['我市对企业上云有什么补贴？', 'OPC 设备联网改造怎么申报？', '算力券如何申领？'];
  if (name.includes('法律') || name.includes('合同')) return ['帮我审查这份采购合同的风险', '试用期辞退的合规要点？'];
  if (name.includes('开办') || name.includes('企业')) return ['注册一家科技公司需要哪些材料？'];
  if (name.includes('公文') || name.includes('办文')) return ['把这段话改成规范的请示语气'];
  if (name.includes('差旅')) return ['查下周北京到上海的机票', '帮我提交一份差旅申请'];
  if (name.includes('审批')) return ['查询我的待审批', '发起一份请假审批'];
  return [];
}

async function send() {
  const text = input.value.trim();
  if (!text || streaming.value) return;
  if (!sessionId.value) {
    uni.showToast({ title: '会话未创建', icon: 'none' });
    return;
  }

  // 检查额度
  try {
    const q = await ledgerApi.quota();
    if (q.code === 0 && q.data && q.data.remainingQuota <= 0) {
      quotaExhausted.value = true;
      return;
    }
  } catch { /* ignore */ }

  messages.push({ id: Math.random().toString(36), role: 'user', content: text });
  input.value = '';
  streaming.value = true;
  streamText.value = '';
  recommendations.value = [];
  await scrollBottom();

  let toolInfo = '';

  try {
    await streamChat(sessionId.value, text, (event) => {
      switch (event.type) {
        case 'text':
          streamText.value += event.content || '';
          scrollTop.value += 1;
          break;
        case 'tool_call':
          toolInfo = `🔧 调用工具：${event.tool_name || ''}`;
          break;
        case 'tool_executing':
          streamText.value = `正在执行：${event.tool_name || ''}…`;
          break;
        case 'tool_result':
          toolInfo = `🔧 ${event.tool_name} 执行完成`;
          break;
        case 'done':
          const tokens = event.tokens_used || 0;
          const meta = `本次消耗 ${tokens} 词元 · 模型：DeepSeek · 已入账本并留痕`;
          messages.push({
            id: Math.random().toString(36),
            role: 'assistant',
            content: streamText.value || event.content || '',
            meta,
            toolInfo: toolInfo || undefined,
          });
          streamText.value = '';
          streaming.value = false;
          scrollBottom();
          break;
        case 'error':
          messages.push({
            id: Math.random().toString(36),
            role: 'assistant',
            content: `⚠️ ${event.message || '服务异常'}`,
          });
          streamText.value = '';
          streaming.value = false;
          scrollBottom();
          break;
      }
    });
  } catch (e: any) {
    messages.push({
      id: Math.random().toString(36),
      role: 'assistant',
      content: `⚠️ 请求失败：${e.message || '网络错误'}`,
    });
    streamText.value = '';
    streaming.value = false;
    scrollBottom();
  }
}

function quickAsk(q: string) {
  input.value = q;
  send();
}
function toggleKB() {
  kbOn.value = !kbOn.value;
  uni.showToast({ title: kbOn.value ? '将优先检索你的知识库' : '仅用模型通用知识回答', icon: 'none' });
}
function submitApproval(m: Msg) {
  m.approve = undefined;
  uni.showToast({ title: '已提交审批，进入工作流（演示）', icon: 'none' });
}
function cancelApproval(m: Msg) {
  m.approve = undefined;
  uni.showToast({ title: '已取消提交（演示）', icon: 'none' });
}

async function scrollBottom() {
  await nextTick();
  scrollTop.value = 99999;
}
function back() {
  uni.navigateBack({ delta: 1 });
}
</script>

<style scoped>
.chat { display: flex; flex-direction: column; height: 100vh; background: #f4f6fa; }
.header {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; background: #fff; border-bottom: 1px solid #e6eaf2;
}
.back { font-size: 22px; color: #2f6fed; }
.title { font-size: 15px; font-weight: 700; flex: 1; }
.model-tag { font-size: 11px; }
.msgs { flex: 1; padding: 12px; overflow-y: auto; }
.msg { display: flex; margin-bottom: 12px; gap: 8px; }
.msg.right { justify-content: flex-end; }
.bubble {
  max-width: 82%; padding: 10px 12px; border-radius: 4px 14px 14px 14px;
  font-size: 13px; line-height: 1.6; word-break: break-word;
  background: #fff; border: 1px solid #e6eaf2;
}
.msg.right .bubble { background: #2f6fed; color: #fff; border: none; border-radius: 14px 4px 14px 14px; }
.meta-line { font-size: 10.5px; color: #6b7486; margin-top: 6px; }
.tool-info { font-size: 11px; color: #2f6fed; background: #eaf1ff; border-radius: 6px; padding: 4px 8px; margin-top: 6px; }
.citations { margin-top: 6px; }
.cite { display: block; font-size: 11px; color: #2f6fed; background: #eaf1ff; border-radius: 6px; padding: 4px 8px; }
.approve-card { border: 1px solid #2f6fed; background: #f4f8ff; border-radius: 12px; padding: 10px; margin-top: 8px; font-size: 12px; }
.approve-row { display: flex; gap: 8px; margin-top: 8px; }
.btn { background: #2f6fed; color: #fff; border: none; border-radius: 20px; padding: 8px 18px; font-size: 13px; }
.btn.ghost { background: #fff; color: #2f6fed; border: 1px solid #2f6fed; }
.btn.small { padding: 5px 12px; font-size: 12px; }
.typing .cursor { display: inline-block; animation: blink 1s steps(2) infinite; }
@keyframes blink { 0%,50% { opacity: 1 } 51%,100% { opacity: 0 } }
.recs-bar { display: flex; gap: 6px; flex-wrap: wrap; padding: 6px 12px; background: #fff; border-top: 1px solid #e6eaf2; }
.chip { font-size: 11px; background: #fff; border: 1px solid #e6eaf2; border-radius: 12px; padding: 5px 10px; color: #6b7486; }
.composer {
  display: flex; gap: 8px; align-items: flex-end;
  padding: 8px 10px; background: #fff; border-top: 1px solid #e6eaf2;
}
.kb-chip { font-size: 11px; color: #2f6fed; background: #eaf1ff; border-radius: 12px; padding: 6px 10px; white-space: nowrap; }
.kb-chip.off { color: #6b7486; background: #eef0f5; }
.input {
  flex: 1; min-height: 36px; max-height: 120px;
  border: 1px solid #e6eaf2; border-radius: 18px;
  padding: 9px 14px; font-size: 13px; background: #f6f8fc;
}
.send {
  height: 36px; padding: 0 16px;
  background: #2f6fed; color: #fff; border: none; border-radius: 18px;
  font-size: 13px;
}
.send[disabled] { opacity: 0.5; }
</style>
