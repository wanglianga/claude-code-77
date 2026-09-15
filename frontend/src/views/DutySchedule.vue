<template>
  <el-card class="panel-card" shadow="never">
    <template #header>
      <div style="display: flex; justify-content: space-between; align-items: center">
        <span>物业值班表（{{ weekRange }}）</span>
        <el-button type="primary" @click="openCreate">排班</el-button>
      </div>
    </template>

    <el-table :data="schedules" v-loading="loading">
      <el-table-column label="日期" width="130">
        <template #default="{ row }">
          <span :style="{ fontWeight: isToday(row.dutyDate) ? 700 : 400, color: isToday(row.dutyDate) ? '#2563eb' : 'inherit' }">
            {{ row.dutyDate }} {{ isToday(row.dutyDate) ? '（今天）' : '' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="班次" width="90">
        <template #default="{ row }">
          <el-tag :type="row.shift === 'DAY' ? 'primary' : 'info'" size="small">{{ SHIFT_LABEL[row.shift] }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="值班人" width="110">
        <template #default="{ row }">{{ row.user?.realName }}</template>
      </el-table-column>
      <el-table-column label="角色" width="110">
        <template #default="{ row }">{{ ROLE_LABEL[row.user?.role] }}</template>
      </el-table-column>
      <el-table-column label="联系电话" width="130">
        <template #default="{ row }">{{ row.user?.phone }}</template>
      </el-table-column>
      <el-table-column prop="position" label="值班岗位" width="130" />
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" width="70">
        <template #default="{ row }">
          <el-button size="small" link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createDialog" title="值班排班" width="480px">
      <el-form label-width="90px">
        <el-form-item label="值班日期" required>
          <el-date-picker v-model="createForm.dutyDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="班次" required>
          <el-radio-group v-model="createForm.shift">
            <el-radio-button value="DAY">白班</el-radio-button>
            <el-radio-button value="NIGHT">夜班</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="值班人" required>
          <el-select v-model="createForm.userId" style="width: 100%">
            <el-option v-for="u in staffUsers" :key="u.id" :label="`${u.realName}（${ROLE_LABEL[u.role]}）`" :value="u.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="值班岗位">
          <el-input v-model="createForm.position" placeholder="如 监控中心 / 1 号门岗" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCreate">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import api from '../api'
import { ROLE_LABEL, SHIFT_LABEL } from '../utils/dict'

const loading = ref(false)
const schedules = ref([])
const staffUsers = ref([])
const createDialog = ref(false)
const createForm = reactive({ dutyDate: dayjs().format('YYYY-MM-DD'), shift: 'DAY', userId: null, position: '监控中心', remark: '' })

const weekRange = computed(() => {
  if (schedules.value.length === 0) return ''
  return `${schedules.value[0].dutyDate} ~ ${schedules.value[schedules.value.length - 1].dutyDate}`
})

function isToday(date) {
  return dayjs(date).isSame(dayjs(), 'day')
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/duty-schedules')
    schedules.value = res.data
  } finally {
    loading.value = false
  }
}

function openCreate() {
  createDialog.value = true
}

async function saveCreate() {
  if (!createForm.dutyDate || !createForm.userId) {
    ElMessage.warning('请选择日期和值班人')
    return
  }
  await api.post('/duty-schedules', {
    dutyDate: createForm.dutyDate,
    shift: createForm.shift,
    position: createForm.position,
    remark: createForm.remark,
    user: { id: createForm.userId }
  })
  ElMessage.success('排班成功')
  createDialog.value = false
  load()
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除 ${row.dutyDate} ${row.user?.realName} 的排班？`, '删除排班', { type: 'warning' })
  await api.delete(`/duty-schedules/${row.id}`)
  ElMessage.success('已删除')
  load()
}

onMounted(async () => {
  load()
  const res = await api.get('/users/contacts')
  staffUsers.value = res.data.filter((u) => ['DUTY', 'SECURITY', 'BUTLER'].includes(u.role))
})
</script>
