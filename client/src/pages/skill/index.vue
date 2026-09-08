<template>
  <view class="skill">
    <view class="page-title">{{ skillName }}</view>

    <view class="card">
      <view v-if="skillName === '公文写作'" class="form">
        <view class="form-item">
          <text class="label">文种</text>
          <picker mode="selector" :range="docTypes" :value="docTypeIdx" @change="onDocType">
            <view class="picker">{{ docTypes[docTypeIdx] }}</view>
          </picker>
        </view>
        <view class="form-item">
          <text class="label">主题与要点</text>
          <textarea v-model="topic" class="textarea" rows="4" placeholder="请输入主题与要点" />
        </view>
        <view class="form-item">
          <text class="label">字数要求</text>
          <input v-model="wordCount" class="input" placeholder="如 800 字左右" />
        </view>
        <view class="btn-row">
          <button class="btn" @click="genDoc">生成文稿</button>
          <text class="muted cost">预计消耗约 1,500 词元</text>
        </view>
      </view>

      <view v-else class="form">
        <view class="form-item">
          <text class="label">需求描述</text>
          <textarea v-model="topic" class="textarea" rows="4" placeholder="请描述你的需求" />
        </view>
        <view class="btn-row">
          <button class="btn" @click="genDoc">生成</button>
          <text class="muted cost">预计消耗约 1,500 词元</text>
        </view>
      </view>
    </view>

    <view v-if="result" class="card">
      <view class="sec-title">生成结果（可复制 / 存资料 / 转会话修改）</view>
      <view class="result-text">{{ result }}</view>
      <view class="btn-row">
        <button class="btn ghost small" @click="saveAsMaterial">存为资料</button>
        <button class="btn small" @click="goChat">转会话修改</button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { onLoad } from '@dcloudio/uni-app';

const skillName = ref('公文写作');
const topic = ref('关于组织企业参加全市人工智能应用供需对接会的通知');
const wordCount = ref('800 字左右');
const docTypes = ['通知', '请示', '工作报告', '函'];
const docTypeIdx = ref(0);
const result = ref('');

onLoad((opts: any) => {
  if (opts.name) skillName.value = decodeURIComponent(opts.name);
});

function onDocType(e: any) {
  docTypeIdx.value = e.detail.value;
}

function genDoc() {
  const t = topic.value || '相关工作事项';
  const docType = docTypes[docTypeIdx.value];
  let body = '';
  if (skillName.value === '公文写作') {
    body = `关于${t.replace(/^关于组织?/, '').replace(/^关于/, '')}的${docType}\n\n各有关企业：\n  为深入推进人工智能与实体经济融合，搭建供需对接平台，经研究，定于近期组织全市人工智能应用供需对接会。现将有关事项${docType === '通知' ? '如下' : '请示如下'}：\n  一、会议时间与地点（另行通知）。\n  二、参会范围：全市有智能化改造需求的企业及人工智能服务供应商。\n  三、报名方式：请于规定时间前通过线上平台报名。\n\n（以上为结构化生成示例，正式使用前请人工复核）`;
  } else if (skillName.value === '合同初审') {
    body = `【合同初审报告】\n\n针对你描述的合同需求，初步识别以下风险点：\n1. 付款条款建议明确分期与违约责任\n2. 知识产权归属条款需明确\n3. 争议解决建议约定管辖法院\n\n（以上为结构化生成示例，正式使用前请人工复核）`;
  } else if (skillName.value === '数据分析') {
    body = `【数据分析报告】\n\n基于你提供的数据描述，建议分析维度：\n1. 趋势分析：按月/季度对比\n2. 结构分析：分类占比\n3. 异常分析：离群点识别\n\n（以上为结构化生成示例，正式使用前请人工复核）`;
  } else {
    body = `【办事指引】\n\n针对「${t}」的办事流程：\n1. 准备材料：营业执照、身份证、申请表\n2. 提交渠道：线上平台或线下窗口\n3. 办理时限：5 个工作日\n\n（以上为结构化生成示例，正式使用前请人工复核）`;
  }
  result.value = body;
  uni.showToast({ title: '生成完成，消耗 1,500 词元已入账', icon: 'none' });
}

function saveAsMaterial() {
  uni.showToast({ title: '已存入个人资料（演示）', icon: 'none' });
}
function goChat() {
  uni.navigateTo({ url: '/pages/chat/index?agentName=' + encodeURIComponent(skillName.value) });
  uni.showToast({ title: '已转入会话，可继续修改（演示）', icon: 'none' });
}
</script>

<style scoped>
.skill { padding: 14px; background: #f4f6fa; min-height: 100vh; }
.page-title { font-size: 18px; font-weight: 700; margin-bottom: 12px; }
.card { background: #fff; border-radius: 14px; padding: 14px; margin-bottom: 12px; border: 1px solid #e6eaf2; }
.form-item { margin-bottom: 12px; }
.label { font-size: 12px; color: #6b7486; display: block; margin-bottom: 4px; }
.textarea, .input, .picker {
  width: 100%; border: 1px solid #e6eaf2; border-radius: 8px;
  padding: 8px; font-size: 13px; font-family: inherit; background: #f6f8fc; min-height: 36px;
}
.textarea { min-height: 80px; }
.picker { line-height: 20px; }
.btn-row { margin-top: 8px; display: flex; align-items: center; gap: 8px; }
.btn { background: #2f6fed; color: #fff; border: none; border-radius: 20px; padding: 8px 18px; font-size: 13px; }
.btn.ghost { background: #fff; color: #2f6fed; border: 1px solid #2f6fed; }
.btn.small { padding: 5px 12px; font-size: 12px; }
.muted { color: #6b7486; font-size: 12px; }
.cost { margin-left: 8px; }
.sec-title { font-size: 14px; font-weight: 700; margin-bottom: 10px; }
.result-text { line-height: 1.7; font-size: 13px; color: #1a2233; white-space: pre-wrap; }
</style>
