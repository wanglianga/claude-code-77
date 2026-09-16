<template>
  <el-card class="panel-card" shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>楼栋公告（停梯 / 备用梯 / 老人协助 / 复检通知）</span>
        <div>
          <el-radio-group v-if="canManage" v-model="statusFilter" size="small" style="margin-right: 12px" @change="load">
            <el-radio-button value="">全部（含已撤回）</el-radio-button>
            <el-radio-button value="PUBLISHED">仅有效</el-radio-button>
          </el-radio-group>
          <el-button v-if="canManage" type="primary" @click="openCreate">发布公告</el-button>
        </div>
      </div>
    </template>

    <el-alert v-if="!canManage" type="info" :closable="false" show-icon style="margin-bottom: 12px"
      title="居民端仅显示当前有效的公告指引；已撤回的停梯公告不再展示，历史公告由物业存档审计" />

    <el-row :gutter="14">
      <el-col v-for="notice in notices" :key="notice.id" :span="8" style="margin-bottom: 14px">
        <el-card shadow="hover" :style="{ borderTop: `3px solid ${notice.status === 'REVOKED' ? '#c0c4cc' : '#2563eb'}` }">
          <div style="display: flex; justify-content: space-between; align-items: flex-start">
            <el-tag :type="NOTICE_TYPE[notice.type]?.type" size="small">{{ NOTICE_TYPE[notice.type]?.label }}</el-tag>
            <el-tag v-if="notice.status === 'REVOKED'" type="info" size="small">已撤回</el-tag>
            <el-tag v-else type="success" size="small" effect="plain">有效</el-tag>
          </div>
          <div style="font-weight: 600; margin: 8px 0 4px">{{ notice.title }}</div>
          <div style="font-size: 12px; color: #5a6478; min-height: 54px; white-space: pre-wrap">{{ notice.content }}</div>
          <div v-if="notice.status === 'REVOKED'" style="font-size: 12px; color: #a0a8b8; margin-top: 6px">
            撤回于 {{ fmtTime(notice.revokedAt, 'MM-DD HH:mm') }}<span v-if="notice.revokeReason">：{{ notice.revokeReason }}</span>
          </div>
          <div style="font-size: 12px; color: #8a94a8; margin-top: 8px; display: flex; justify-content: space-between; align-items: center">
            <span>{{ notice.building?.name }} ｜ {{ notice.publisherName }} ｜ {{ fmtTime(notice.publishedAt, 'MM-DD HH:mm') }}</span>
            <el-button v-if="notice.status === 'PUBLISHED' && canManage" size="small" link type="danger" @click="revoke(notice)">撤回</el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-empty v-if="notices.length === 0" description="暂无公告" />

    <el-dialog v-model="createDialog" title="发布楼栋公告" width="560px">
      <el-form label-width="90px">
        <el-form-item label="楼栋" required>
          <el-select v-model="createForm.buildingId" style="width: 100%">
            <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="公告类型" required>
          <el-select v-model="createForm.type" style="width: 100%">
            <el-option v-for="(v, k) in NOTICE_TYPE" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="createForm.title" />
        </el-form-item>
        <el-form-item label="内容" required>
          <el-input v-model="createForm.content" type="textarea" :rows="4"
            placeholder="向业主说明：是否停用电梯、是否开放备用梯、老人上下楼临时协助安排等" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCreate">发布</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useAuthStore } from '../store/auth'
import { NOTICE_TYPE } from '../utils/dict'
import { fmtTime } from '../utils/format'

const auth = useAuthStore()
const notices = ref([])
const buildings = ref([])
const statusFilter = ref('')
const createDialog = ref(false)
const createForm = reactive({ buildingId: null, type: 'GENERAL', title: '', content: '' })

const canManage = computed(() => ['ADMIN', 'DUTY', 'BUTLER'].includes(auth.user?.role))

async function load() {
  const res = await api.get('/notices', { params: statusFilter.value ? { status: statusFilter.value } : {} })
  notices.value = res.data
}

function openCreate() {
  Object.assign(createForm, { buildingId: null, type: 'GENERAL', title: '', content: '' })
  createDialog.value = true
}

async function saveCreate() {
  if (!createForm.buildingId || !createForm.title || !createForm.content) {
    ElMessage.warning('请完整填写公告信息')
    return
  }
  await api.post('/notices', {
    building: { id: createForm.buildingId },
    type: createForm.type,
    title: createForm.title,
    content: createForm.content
  })
  ElMessage.success('公告已发布')
  createDialog.value = false
  load()
}

async function revoke(notice) {
  await ElMessageBox.confirm(`确认撤回公告「${notice.title}」？`, '撤回公告', { type: 'warning' })
  try {
    await api.put(`/notices/${notice.id}/revoke`)
    ElMessage.success('已撤回')
    load()
  } catch {
    /* 拦截器已提示（如：关联整改单的停梯公告须复检通过后由系统统一结束） */
  }
}

onMounted(async () => {
  load()
  const res = await api.get('/buildings')
  buildings.value = res.data
})
</script>
