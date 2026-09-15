<template>
  <div>
    <!-- 反复故障电梯预警 -->
    <el-card class="panel-card" shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>⚠️ 反复故障电梯（一周内 ≥2 次困人）</span>
          <el-button size="small" @click="loadAll">刷新</el-button>
        </div>
      </template>
      <el-empty v-if="repeatFaults.length === 0" description="暂无反复故障电梯" :image-size="60" />
      <el-row v-else :gutter="14">
        <el-col v-for="item in repeatFaults" :key="item.elevator.id" :span="8">
          <el-card shadow="hover" style="border-top: 3px solid #e64545">
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span class="mono" style="font-weight: 700">{{ item.elevator.code }}</span>
              <el-tag type="danger" size="small">7 天 {{ item.weekCount }} 次困人</el-tag>
            </div>
            <div style="font-size: 12px; color: #5a6478; margin: 8px 0">
              {{ item.elevator.building?.name }} ｜ {{ item.elevator.company?.name }}
            </div>
            <div style="margin-bottom: 8px">
              <el-tag v-for="code in item.faultCodes" :key="code" size="small" effect="plain" class="flag-tag">{{ code }}</el-tag>
            </div>
            <div style="font-size: 12px; margin-bottom: 10px">
              <el-tag :type="item.elevator.status === 'STOPPED' ? 'danger' : 'success'" size="small">
                {{ item.elevator.status === 'STOPPED' ? `停梯中 ${fmtMinutes(item.stoppedMinutes)}` : '运行中' }}
              </el-tag>
              <el-tag v-if="item.activePlan" type="warning" size="small" style="margin-left: 6px">
                整改中：{{ PLAN_STATUS[item.activePlan.status]?.label }}
              </el-tag>
            </div>
            <div>
              <el-button size="small" @click="openSummary(item.elevator)">故障汇总</el-button>
              <el-button v-if="!item.activePlan && canRequest" size="small" type="danger" plain
                @click="openRequest(item.elevator)">要求整改</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </el-card>

    <!-- 整改方案 -->
    <el-card class="panel-card" shadow="never" style="margin-bottom: 16px">
      <template #header>
        <span>整改方案（未复检通过前电梯保持停用）</span>
      </template>
      <el-table :data="plans" v-loading="loading">
        <el-table-column label="电梯" width="100">
          <template #default="{ row }"><span class="mono">{{ row.elevator?.code }}</span></template>
        </el-table-column>
        <el-table-column label="楼栋" width="120">
          <template #default="{ row }">{{ row.elevator?.building?.name }}</template>
        </el-table-column>
        <el-table-column prop="companyName" label="维保单位" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="PLAN_STATUS[row.status]?.type" size="small">{{ PLAN_STATUS[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="parts" label="配件" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.parts || '—' }}</template>
        </el-table-column>
        <el-table-column prop="expectedArrival" label="预计到货" width="100">
          <template #default="{ row }">{{ row.expectedArrival || '—' }}</template>
        </el-table-column>
        <el-table-column prop="recheckInspector" label="复检人" width="150">
          <template #default="{ row }">{{ row.recheckInspector || '—' }}</template>
        </el-table-column>
        <el-table-column label="公告发布" width="130">
          <template #default="{ row }">{{ row.noticePublishTime ? fmtTime(row.noticePublishTime) : '—' }}</template>
        </el-table-column>
        <el-table-column label="要求/提交" width="160">
          <template #default="{ row }">
            <div style="font-size: 12px">{{ row.requestedBy }} ｜ {{ fmtTime(row.requestedAt, 'MM-DD HH:mm') }}</div>
            <div v-if="row.submittedBy" style="font-size: 12px; color: #8a94a8">{{ row.submittedBy }} ｜ {{ fmtTime(row.submittedAt, 'MM-DD HH:mm') }}</div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="['REQUESTED', 'RECHECK_FAILED'].includes(row.status) && canSubmit"
              size="small" type="primary" link @click="openSubmit(row)">提交方案</el-button>
            <el-button v-if="row.status === 'SUBMITTED' && canRecheck"
              size="small" type="success" link @click="openRecheck(row)">复检登记</el-button>
            <el-button size="small" link @click="openSummary(row.elevator)">汇总</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 老人帮扶 -->
    <el-card class="panel-card" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>停梯期间老人上下楼帮扶</span>
          <div>
            <el-checkbox v-model="activeOnly" label="仅看帮扶中" style="margin-right: 12px" @change="loadAssistances" />
            <el-button v-if="canAssist" type="primary" size="small" @click="openAssist">登记帮扶需求</el-button>
          </div>
        </div>
      </template>
      <el-table :data="assistances" v-loading="loading">
        <el-table-column prop="residentName" label="老人/住户" width="100" />
        <el-table-column prop="roomNo" label="房号" width="100" />
        <el-table-column label="楼栋" width="130">
          <template #default="{ row }">{{ row.building?.name }}</template>
        </el-table-column>
        <el-table-column label="关联停梯" width="100">
          <template #default="{ row }"><span class="mono">{{ row.elevator?.code || '—' }}</span></template>
        </el-table-column>
        <el-table-column prop="needDescription" label="上下楼需求" min-width="220" show-overflow-tooltip />
        <el-table-column label="临时帮扶人员" width="140">
          <template #default="{ row }">{{ row.helperName }}<br /><span style="font-size: 12px; color: #8a94a8">{{ row.helperPhone }}</span></template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ACTIVE' ? 'warning' : 'success'" size="small">
              {{ row.status === 'ACTIVE' ? '帮扶中' : '已办结' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="登记时间" width="140">
          <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ACTIVE' && canAssist" size="small" link type="success"
              @click="resolve(row)">办结</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 故障汇总对话框 -->
    <el-dialog v-model="summaryDialog" :title="`故障汇总：${summary.elevator?.code || ''}`" width="720px">
      <template v-if="summary.elevator">
        <el-descriptions :column="3" size="small" border style="margin-bottom: 12px">
          <el-descriptions-item label="7 天困人">{{ summary.weekEventCount }} 次</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="summary.elevator.status === 'STOPPED' ? 'danger' : 'success'" size="small">
              {{ summary.elevator.status === 'STOPPED' ? '停梯中' : '运行中' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="本次停梯时长">{{ fmtMinutes(summary.stoppedMinutes) }}</el-descriptions-item>
          <el-descriptions-item label="故障代码" :span="3">
            <el-tag v-for="c in summary.faultCodes" :key="c" size="small" effect="plain" class="flag-tag">{{ c }}</el-tag>
            <span v-if="(summary.faultCodes || []).length === 0">—</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left" style="margin: 10px 0">一周内困人事件</el-divider>
        <el-table :data="summary.weekEvents || []" size="small" max-height="180">
          <el-table-column prop="eventNo" label="编号" width="140">
            <template #default="{ row }"><span class="mono">{{ row.eventNo }}</span></template>
          </el-table-column>
          <el-table-column label="报警时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.alarmTime) }}</template>
          </el-table-column>
          <el-table-column prop="faultCode" label="故障代码" min-width="150">
            <template #default="{ row }">{{ row.faultCode || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="EVENT_STATUS[row.status]?.type" size="small">{{ EVENT_STATUS[row.status]?.label }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-divider content-position="left" style="margin: 10px 0">维保记录（配件更换）</el-divider>
        <el-table :data="summary.parts || []" size="small" max-height="150">
          <el-table-column prop="partName" label="配件" width="140" />
          <el-table-column prop="replaceDate" label="更换日期" width="110" />
          <el-table-column prop="reason" label="原因" min-width="180" show-overflow-tooltip />
          <el-table-column prop="replacedBy" label="更换方" width="130" />
        </el-table>
        <el-divider content-position="left" style="margin: 10px 0">业主投诉（{{ (summary.complaints || []).length }}）</el-divider>
        <el-table :data="summary.complaints || []" size="small" max-height="150">
          <el-table-column prop="ownerName" label="投诉人" width="90" />
          <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="COMPLAINT_STATUS[row.status]?.type" size="small">{{ COMPLAINT_STATUS[row.status]?.label }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- 要求整改对话框 -->
    <el-dialog v-model="requestDialog" :title="`要求提交整改方案：${requestElevator?.code || ''}`" width="520px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        title="创建后电梯将保持停用，并自动发布楼栋停梯公告；复检通过后方可恢复运行" />
      <el-form label-width="90px">
        <el-form-item label="整改要求">
          <el-input v-model="requestNote" type="textarea" :rows="3"
            placeholder="如：一周内 3 次困人，要求提交彻底整改方案，写明配件、到货时间、复检人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="requestDialog = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="doRequest">确认要求整改</el-button>
      </template>
    </el-dialog>

    <!-- 提交方案对话框 -->
    <el-dialog v-model="submitDialog" :title="`提交整改方案：${submitPlan?.elevator?.code || ''}`" width="560px">
      <el-form label-width="110px">
        <el-form-item label="需更换配件" required>
          <el-input v-model="submitForm.parts" placeholder="如：门锁触点组件 ×2、门机控制板 ×1" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="预计到货" required>
              <el-date-picker v-model="submitForm.expectedArrival" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="复检人" required>
              <el-input v-model="submitForm.recheckInspector" placeholder="如：特检院 李工" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="业主公告发布时间" required>
          <el-date-picker v-model="submitForm.noticePublishTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="方案详情">
          <el-input v-model="submitForm.planDetail" type="textarea" :rows="3" placeholder="整改内容、施工安排、复检流程" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doSubmit">提交方案</el-button>
      </template>
    </el-dialog>

    <!-- 复检对话框 -->
    <el-dialog v-model="recheckDialog" :title="`复检登记：${recheckPlan?.elevator?.code || ''}`" width="520px">
      <el-descriptions :column="1" size="small" border style="margin-bottom: 12px">
        <el-descriptions-item label="配件">{{ recheckPlan?.parts }}</el-descriptions-item>
        <el-descriptions-item label="复检人">{{ recheckPlan?.recheckInspector }}</el-descriptions-item>
      </el-descriptions>
      <el-form label-width="90px">
        <el-form-item label="复检结论" required>
          <el-radio-group v-model="recheckForm.pass">
            <el-radio-button :value="true">通过（恢复运行）</el-radio-button>
            <el-radio-button :value="false">未通过（继续停梯）</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="复检结果" required>
          <el-input v-model="recheckForm.result" type="textarea" :rows="3" placeholder="复检情况说明" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recheckDialog = false">取消</el-button>
        <el-button :type="recheckForm.pass ? 'success' : 'danger'" :loading="acting" @click="doRecheck">
          确认复检{{ recheckForm.pass ? '通过' : '未通过' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 帮扶登记对话框 -->
    <el-dialog v-model="assistDialog" title="登记老人上下楼帮扶" width="560px">
      <el-form label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="楼栋" required>
              <el-select v-model="assistForm.buildingId" style="width: 100%" @change="assistForm.elevatorId = null">
                <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联停梯">
              <el-select v-model="assistForm.elevatorId" clearable style="width: 100%">
                <el-option v-for="e in stoppedElevatorsOf(assistForm.buildingId)" :key="e.id" :label="e.code" :value="e.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="老人/住户" required><el-input v-model="assistForm.residentName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="房号"><el-input v-model="assistForm.roomNo" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="联系电话"><el-input v-model="assistForm.phone" /></el-form-item>
        <el-form-item label="上下楼需求" required>
          <el-input v-model="assistForm.needDescription" type="textarea" :rows="2" placeholder="如：每周二上午去医院，需协助上下楼" />
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="帮扶人员" required><el-input v-model="assistForm.helperName" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="帮扶电话"><el-input v-model="assistForm.helperPhone" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="assistDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doAssist">登记</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useAuthStore } from '../store/auth'
import { EVENT_STATUS, COMPLAINT_STATUS } from '../utils/dict'
import { fmtTime } from '../utils/format'

const PLAN_STATUS = {
  REQUESTED: { label: '待维保提交', type: 'warning' },
  SUBMITTED: { label: '待复检', type: 'primary' },
  RECHECK_PASSED: { label: '复检通过', type: 'success' },
  RECHECK_FAILED: { label: '复检未通过', type: 'danger' }
}

const auth = useAuthStore()
const loading = ref(false)
const acting = ref(false)
const repeatFaults = ref([])
const plans = ref([])
const assistances = ref([])
const buildings = ref([])
const elevators = ref([])
const activeOnly = ref(false)

const summaryDialog = ref(false)
const requestDialog = ref(false)
const submitDialog = ref(false)
const recheckDialog = ref(false)
const assistDialog = ref(false)

const summary = ref({})
const requestElevator = ref(null)
const requestNote = ref('')
const submitPlan = ref(null)
const recheckPlan = ref(null)

const submitForm = reactive({ parts: '', expectedArrival: '', recheckInspector: '', noticePublishTime: '', planDetail: '' })
const recheckForm = reactive({ pass: true, result: '' })
const assistForm = reactive({ buildingId: null, elevatorId: null, residentName: '', roomNo: '', phone: '', needDescription: '', helperName: '', helperPhone: '' })

const canRequest = computed(() => ['ADMIN', 'DUTY'].includes(auth.user?.role))
const canSubmit = computed(() => ['ADMIN', 'DUTY', 'MAINTENANCE'].includes(auth.user?.role))
const canRecheck = computed(() => ['ADMIN', 'DUTY', 'MAINTENANCE'].includes(auth.user?.role))
const canAssist = computed(() => ['ADMIN', 'DUTY', 'BUTLER'].includes(auth.user?.role))

function fmtMinutes(minutes) {
  if (minutes == null) return '—'
  if (minutes < 60) return `${minutes} 分钟`
  const h = Math.floor(minutes / 60)
  if (h < 24) return `${h} 小时 ${minutes % 60} 分`
  return `${Math.floor(h / 24)} 天 ${h % 24} 小时`
}

function stoppedElevatorsOf(buildingId) {
  return elevators.value.filter((e) => e.building?.id === buildingId && e.status === 'STOPPED')
}

async function loadAll() {
  loading.value = true
  try {
    const [rf, pl, b, e] = await Promise.all([
      api.get('/elevators/repeat-faults'),
      api.get('/rectification-plans'),
      api.get('/buildings'),
      api.get('/elevators')
    ])
    repeatFaults.value = rf.data
    plans.value = pl.data
    buildings.value = b.data
    elevators.value = e.data
    await loadAssistances()
  } finally {
    loading.value = false
  }
}

async function loadAssistances() {
  const res = await api.get('/assistances', { params: activeOnly.value ? { activeOnly: true } : {} })
  assistances.value = res.data
}

async function openSummary(elevator) {
  const res = await api.get(`/elevators/${elevator.id}/fault-summary`)
  summary.value = res.data
  summaryDialog.value = true
}

function openRequest(elevator) {
  requestElevator.value = elevator
  requestNote.value = ''
  requestDialog.value = true
}

async function doRequest() {
  acting.value = true
  try {
    await api.post(`/elevators/${requestElevator.value.id}/rectification-plans`, { requestNote: requestNote.value })
    ElMessage.success('已要求维保单位提交整改方案，电梯保持停用并已发布停梯公告')
    requestDialog.value = false
    loadAll()
  } finally {
    acting.value = false
  }
}

function openSubmit(plan) {
  submitPlan.value = plan
  Object.assign(submitForm, {
    parts: plan.parts || '', expectedArrival: plan.expectedArrival || '',
    recheckInspector: plan.recheckInspector || '', noticePublishTime: '', planDetail: plan.planDetail || ''
  })
  submitDialog.value = true
}

async function doSubmit() {
  if (!submitForm.parts || !submitForm.expectedArrival || !submitForm.recheckInspector || !submitForm.noticePublishTime) {
    ElMessage.warning('配件、预计到货、复检人、业主公告发布时间均为必填')
    return
  }
  acting.value = true
  try {
    await api.put(`/rectification-plans/${submitPlan.value.id}/submit`, submitForm)
    ElMessage.success('整改方案已提交，等待复检')
    submitDialog.value = false
    loadAll()
  } finally {
    acting.value = false
  }
}

function openRecheck(plan) {
  recheckPlan.value = plan
  recheckForm.pass = true
  recheckForm.result = ''
  recheckDialog.value = true
}

async function doRecheck() {
  if (!recheckForm.result) {
    ElMessage.warning('请填写复检结果')
    return
  }
  acting.value = true
  try {
    await api.put(`/rectification-plans/${recheckPlan.value.id}/recheck`, recheckForm)
    ElMessage.success(recheckForm.pass ? '复检通过，电梯已恢复运行并发布复检公告' : '已登记复检未通过，电梯保持停梯')
    recheckDialog.value = false
    loadAll()
  } finally {
    acting.value = false
  }
}

function openAssist() {
  Object.assign(assistForm, { buildingId: null, elevatorId: null, residentName: '', roomNo: '', phone: '', needDescription: '', helperName: '', helperPhone: '' })
  assistDialog.value = true
}

async function doAssist() {
  if (!assistForm.buildingId || !assistForm.residentName || !assistForm.needDescription || !assistForm.helperName) {
    ElMessage.warning('请完整填写楼栋、住户、需求与帮扶人员')
    return
  }
  acting.value = true
  try {
    await api.post('/assistances', assistForm)
    ElMessage.success('帮扶需求已登记')
    assistDialog.value = false
    loadAssistances()
  } finally {
    acting.value = false
  }
}

async function resolve(row) {
  await api.put(`/assistances/${row.id}/resolve`)
  ElMessage.success('已办结')
  loadAssistances()
}

onMounted(loadAll)
</script>
