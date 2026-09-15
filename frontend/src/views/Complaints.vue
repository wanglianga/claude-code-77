<template>
  <el-card class="panel-card" shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>业主投诉</span>
        <el-button type="primary" @click="openCreate">登记投诉</el-button>
      </div>
    </template>

    <el-table :data="complaints" v-loading="loading">
      <el-table-column label="投诉时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="ownerName" label="投诉人" width="90" />
      <el-table-column prop="ownerPhone" label="联系电话" width="120" />
      <el-table-column prop="buildingName" label="楼栋" width="130" />
      <el-table-column label="关联电梯" width="100">
        <template #default="{ row }"><span class="mono">{{ row.elevator?.code || '—' }}</span></template>
      </el-table-column>
      <el-table-column prop="content" label="投诉内容" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="COMPLAINT_STATUS[row.status]?.type" size="small">{{ COMPLAINT_STATUS[row.status]?.label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="处理结果" min-width="200" show-overflow-tooltip />
      <el-table-column label="操作" width="90" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'RESOLVED'" size="small" link type="primary" @click="openHandle(row)">处理</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 登记投诉 -->
    <el-dialog v-model="createDialog" title="登记业主投诉" width="560px">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="投诉人" required><el-input v-model="createForm.ownerName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="createForm.ownerPhone" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="楼栋">
              <el-select v-model="createForm.buildingName" style="width: 100%">
                <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联电梯">
              <el-select v-model="createForm.elevatorId" clearable filterable style="width: 100%">
                <el-option v-for="e in elevators" :key="e.id" :label="`${e.code}（${e.building?.name}）`" :value="e.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="投诉内容" required>
          <el-input v-model="createForm.content" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCreate">提交</el-button>
      </template>
    </el-dialog>

    <!-- 处理投诉 -->
    <el-dialog v-model="handleDialog" title="处理投诉" width="520px">
      <el-alert type="info" :closable="false" style="margin-bottom: 12px">
        <template #title>{{ handling?.ownerName }}：{{ handling?.content }}</template>
      </el-alert>
      <el-form label-width="90px">
        <el-form-item label="处理状态">
          <el-radio-group v-model="handleForm.status">
            <el-radio-button value="PROCESSING">处理中</el-radio-button>
            <el-radio-button value="RESOLVED">已办结</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理结果">
          <el-input v-model="handleForm.result" type="textarea" :rows="3" placeholder="处理措施与答复内容" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="handleDialog = false">取消</el-button>
        <el-button type="primary" @click="saveHandle">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { COMPLAINT_STATUS } from '../utils/dict'
import { fmtTime } from '../utils/format'

const loading = ref(false)
const complaints = ref([])
const buildings = ref([])
const elevators = ref([])
const createDialog = ref(false)
const handleDialog = ref(false)
const handling = ref(null)
const createForm = reactive({ ownerName: '', ownerPhone: '', buildingName: '', elevatorId: null, content: '' })
const handleForm = reactive({ status: 'RESOLVED', result: '' })

async function load() {
  loading.value = true
  try {
    const res = await api.get('/complaints')
    complaints.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(createForm, { ownerName: '', ownerPhone: '', buildingName: '', elevatorId: null, content: '' })
  createDialog.value = true
}

async function saveCreate() {
  if (!createForm.ownerName || !createForm.content) {
    ElMessage.warning('请填写投诉人和投诉内容')
    return
  }
  await api.post('/complaints', {
    ownerName: createForm.ownerName,
    ownerPhone: createForm.ownerPhone,
    buildingName: createForm.buildingName,
    content: createForm.content,
    elevator: createForm.elevatorId ? { id: createForm.elevatorId } : null
  })
  ElMessage.success('投诉已登记')
  createDialog.value = false
  load()
}

function openHandle(row) {
  handling.value = row
  handleForm.status = 'RESOLVED'
  handleForm.result = row.result || ''
  handleDialog.value = true
}

async function saveHandle() {
  await api.put(`/complaints/${handling.value.id}/handle`, handleForm)
  ElMessage.success('已保存')
  handleDialog.value = false
  load()
}

onMounted(async () => {
  load()
  const [b, e] = await Promise.all([api.get('/buildings'), api.get('/elevators')])
  buildings.value = b.data
  elevators.value = e.data
})
</script>
