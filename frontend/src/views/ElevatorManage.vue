<template>
  <el-card class="panel-card" shadow="never">
    <el-tabs v-model="tab">
      <!-- ============ 电梯档案 ============ -->
      <el-tab-pane label="电梯档案" name="elevators">
        <div style="margin-bottom: 12px; display: flex; justify-content: space-between">
          <el-select v-model="elevatorFilter" placeholder="全部楼栋" clearable style="width: 180px">
            <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
          <el-button type="primary" @click="openElevatorDialog()">新增电梯</el-button>
        </div>
        <el-table :data="filteredElevators" v-loading="loading">
          <el-table-column prop="code" label="电梯编号" width="130">
            <template #default="{ row }">
              <span class="mono">{{ row.code }}</span>
              <el-tooltip v-if="repeatFaultIds.includes(row.id)" content="一周内多次困人，请前往「停梯整改与帮扶」处理" placement="top">
                <el-tag type="danger" size="small" style="margin-left: 4px">反复故障</el-tag>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="楼栋 / 位置" width="180">
            <template #default="{ row }">{{ row.building?.name }} {{ row.position }}</template>
          </el-table-column>
          <el-table-column label="品牌型号" width="160">
            <template #default="{ row }">{{ row.brand }} {{ row.model }}</template>
          </el-table-column>
          <el-table-column prop="floors" label="层站" width="70" align="center" />
          <el-table-column label="门区位置" min-width="140">
            <template #default="{ row }">{{ row.doorZone }}</template>
          </el-table-column>
          <el-table-column label="维保单位" width="170">
            <template #default="{ row }">{{ row.company?.name }}</template>
          </el-table-column>
          <el-table-column label="下次年检" width="110">
            <template #default="{ row }">
              <span :style="{ color: isInspectionDue(row.nextInspectionDate) ? '#e64545' : 'inherit' }">
                {{ row.nextInspectionDate }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="ELEVATOR_STATUS[row.status]?.type" size="small">{{ ELEVATOR_STATUS[row.status]?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openElevatorDetail(row)">档案</el-button>
              <el-button size="small" link type="primary" @click="openElevatorDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 维保单位 ============ -->
      <el-tab-pane label="维保单位" name="companies">
        <div style="margin-bottom: 12px; text-align: right">
          <el-button type="primary" @click="openCompanyDialog()">新增维保单位</el-button>
        </div>
        <el-table :data="companies" v-loading="loading">
          <el-table-column prop="name" label="单位名称" min-width="180" />
          <el-table-column prop="contactPerson" label="联系人" width="90" />
          <el-table-column prop="contactPhone" label="联系电话" width="130" />
          <el-table-column prop="emergencyPhone" label="24h 应急电话" width="140" />
          <el-table-column label="信用分" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.creditScore >= 90 ? 'success' : row.creditScore >= 80 ? 'warning' : 'danger'">
                {{ row.creditScore }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="penaltyCount" label="处罚次数" width="90" align="center">
            <template #default="{ row }">
              <el-tag v-if="row.penaltyCount > 0" type="danger" effect="plain">{{ row.penaltyCount }}</el-tag>
              <span v-else>0</span>
            </template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="openCompanyDialog(row)">编辑</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ============ 维保合同 ============ -->
      <el-tab-pane label="维保合同" name="contracts">
        <div style="margin-bottom: 12px; text-align: right">
          <el-button type="primary" @click="openContractDialog()">新增合同</el-button>
        </div>
        <el-table :data="contracts" v-loading="loading">
          <el-table-column prop="contractNo" label="合同编号" width="130">
            <template #default="{ row }"><span class="mono">{{ row.contractNo }}</span></template>
          </el-table-column>
          <el-table-column label="电梯" width="100">
            <template #default="{ row }"><span class="mono">{{ row.elevator?.code }}</span></template>
          </el-table-column>
          <el-table-column label="维保单位" min-width="170">
            <template #default="{ row }">{{ row.company?.name }}</template>
          </el-table-column>
          <el-table-column prop="startDate" label="开始日期" width="110" />
          <el-table-column prop="endDate" label="到期日期" width="110">
            <template #default="{ row }">
              <span :style="{ color: isInspectionDue(row.endDate) ? '#e64545' : 'inherit' }">{{ row.endDate }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="annualFee" label="年费(元)" width="100" align="right" />
          <el-table-column prop="content" label="合同要点" min-width="220" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <!-- ============ 年检记录 ============ -->
      <el-tab-pane label="年检记录" name="inspections">
        <div style="margin-bottom: 12px; text-align: right">
          <el-button type="primary" @click="openInspectionDialog()">登记年检</el-button>
        </div>
        <el-table :data="inspections" v-loading="loading">
          <el-table-column label="电梯" width="100">
            <template #default="{ row }"><span class="mono">{{ row.elevator?.code }}</span></template>
          </el-table-column>
          <el-table-column prop="inspectionDate" label="检验日期" width="110" />
          <el-table-column prop="nextInspectionDate" label="下次检验" width="110" />
          <el-table-column label="结论" width="110">
            <template #default="{ row }">
              <el-tag :type="INSPECTION_RESULT[row.result]?.type" size="small">{{ INSPECTION_RESULT[row.result]?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="inspector" label="检验机构" min-width="160" />
          <el-table-column prop="reportNo" label="报告编号" width="130">
            <template #default="{ row }"><span class="mono">{{ row.reportNo }}</span></template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip />
        </el-table>
      </el-tab-pane>

      <!-- ============ 配件更换 ============ -->
      <el-tab-pane label="配件更换" name="parts">
        <div style="margin-bottom: 12px; text-align: right">
          <el-button type="primary" @click="openPartDialog()">登记配件更换</el-button>
        </div>
        <el-table :data="parts" v-loading="loading">
          <el-table-column label="电梯" width="100">
            <template #default="{ row }"><span class="mono">{{ row.elevator?.code }}</span></template>
          </el-table-column>
          <el-table-column prop="partName" label="配件名称" width="150" />
          <el-table-column prop="replaceDate" label="更换日期" width="110" />
          <el-table-column prop="reason" label="更换原因" min-width="200" show-overflow-tooltip />
          <el-table-column prop="cost" label="费用(元)" width="100" align="right" />
          <el-table-column prop="replacedBy" label="更换方" width="140" />
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 电梯编辑对话框 -->
    <el-dialog v-model="elevatorDialog" :title="elevatorForm.id ? '编辑电梯' : '新增电梯'" width="560px">
      <el-form label-width="90px">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="电梯编号" required><el-input v-model="elevatorForm.code" :disabled="!!elevatorForm.id" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="所属楼栋" required>
              <el-select v-model="elevatorForm.buildingId" style="width: 100%">
                <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="品牌"><el-input v-model="elevatorForm.brand" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="型号"><el-input v-model="elevatorForm.model" /></el-form-item></el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="层站数"><el-input-number v-model="elevatorForm.floors" :min="1" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="位置"><el-input v-model="elevatorForm.position" placeholder="如 1 单元" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="门区位置"><el-input v-model="elevatorForm.doorZone" placeholder="如 1 单元大堂东侧" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="维保单位">
              <el-select v-model="elevatorForm.companyId" style="width: 100%">
                <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="elevatorForm.status" style="width: 100%">
                <el-option v-for="(v, k) in ELEVATOR_STATUS" :key="k" :label="v.label" :value="k" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="安装日期">
              <el-date-picker v-model="elevatorForm.installDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次年检">
              <el-date-picker v-model="elevatorForm.nextInspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="elevatorDialog = false">取消</el-button>
        <el-button type="primary" @click="saveElevator">保存</el-button>
      </template>
    </el-dialog>

    <!-- 电梯档案抽屉 -->
    <el-drawer v-model="elevatorDrawer" :title="`电梯档案：${drawerData.elevator?.code || ''}`" size="620px">
      <template v-if="drawerData.elevator">
        <el-descriptions :column="2" size="small" border>
          <el-descriptions-item label="楼栋">{{ drawerData.elevator.building?.name }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ drawerData.elevator.position }}</el-descriptions-item>
          <el-descriptions-item label="品牌型号">{{ drawerData.elevator.brand }} {{ drawerData.elevator.model }}</el-descriptions-item>
          <el-descriptions-item label="维保单位">{{ drawerData.elevator.company?.name }}</el-descriptions-item>
          <el-descriptions-item label="安装日期">{{ drawerData.elevator.installDate }}</el-descriptions-item>
          <el-descriptions-item label="下次年检">{{ drawerData.elevator.nextInspectionDate }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">困人事件（{{ drawerData.events?.length || 0 }}）</el-divider>
        <el-table :data="drawerData.events || []" size="small" max-height="220">
          <el-table-column prop="eventNo" label="编号" width="130">
            <template #default="{ row }"><span class="mono">{{ row.eventNo }}</span></template>
          </el-table-column>
          <el-table-column label="时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.alarmTime) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="EVENT_STATUS[row.status]?.type" size="small">{{ EVENT_STATUS[row.status]?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button size="small" link type="primary" @click="$router.push(`/events/${row.id}`)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">年检记录</el-divider>
        <el-table :data="drawerData.inspections || []" size="small" max-height="180">
          <el-table-column prop="inspectionDate" label="检验日期" width="110" />
          <el-table-column label="结论" width="100">
            <template #default="{ row }">
              <el-tag :type="INSPECTION_RESULT[row.result]?.type" size="small">{{ INSPECTION_RESULT[row.result]?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="reportNo" label="报告编号" width="120" />
          <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        </el-table>

        <el-divider content-position="left">配件更换</el-divider>
        <el-table :data="drawerData.parts || []" size="small" max-height="180">
          <el-table-column prop="partName" label="配件" width="130" />
          <el-table-column prop="replaceDate" label="日期" width="110" />
          <el-table-column prop="reason" label="原因" show-overflow-tooltip />
          <el-table-column prop="cost" label="费用" width="80" align="right" />
        </el-table>

        <el-divider content-position="left">维保合同</el-divider>
        <el-table :data="drawerData.contracts || []" size="small" max-height="160">
          <el-table-column prop="contractNo" label="合同编号" width="120" />
          <el-table-column prop="startDate" label="开始" width="100" />
          <el-table-column prop="endDate" label="到期" width="100" />
          <el-table-column prop="annualFee" label="年费" align="right" />
        </el-table>
      </template>
    </el-drawer>

    <!-- 维保单位对话框 -->
    <el-dialog v-model="companyDialog" :title="companyForm.id ? '编辑维保单位' : '新增维保单位'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="单位名称" required><el-input v-model="companyForm.name" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="联系人"><el-input v-model="companyForm.contactPerson" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="联系电话"><el-input v-model="companyForm.contactPhone" /></el-form-item></el-col>
        </el-row>
        <el-form-item label="应急电话"><el-input v-model="companyForm.emergencyPhone" placeholder="24 小时应急救援电话" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="companyForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="companyDialog = false">取消</el-button>
        <el-button type="primary" @click="saveCompany">保存</el-button>
      </template>
    </el-dialog>

    <!-- 合同对话框 -->
    <el-dialog v-model="contractDialog" title="新增维保合同" width="520px">
      <el-form label-width="90px">
        <el-form-item label="合同编号" required><el-input v-model="contractForm.contractNo" /></el-form-item>
        <el-form-item label="电梯" required>
          <el-select v-model="contractForm.elevatorId" style="width: 100%">
            <el-option v-for="e in elevators" :key="e.id" :label="`${e.code}（${e.building?.name}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="维保单位" required>
          <el-select v-model="contractForm.companyId" style="width: 100%">
            <el-option v-for="c in companies" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="开始日期">
              <el-date-picker v-model="contractForm.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="到期日期">
              <el-date-picker v-model="contractForm.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="年费(元)"><el-input-number v-model="contractForm.annualFee" :min="0" style="width: 100%" /></el-form-item>
        <el-form-item label="合同要点"><el-input v-model="contractForm.content" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contractDialog = false">取消</el-button>
        <el-button type="primary" @click="saveContract">保存</el-button>
      </template>
    </el-dialog>

    <!-- 年检对话框 -->
    <el-dialog v-model="inspectionDialog" title="登记年检" width="520px">
      <el-form label-width="90px">
        <el-form-item label="电梯" required>
          <el-select v-model="inspectionForm.elevatorId" style="width: 100%">
            <el-option v-for="e in elevators" :key="e.id" :label="`${e.code}（${e.building?.name}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="检验日期">
              <el-date-picker v-model="inspectionForm.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次检验">
              <el-date-picker v-model="inspectionForm.nextInspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="检验结论">
          <el-radio-group v-model="inspectionForm.result">
            <el-radio-button value="PASS">合格</el-radio-button>
            <el-radio-button value="RECTIFY">整改后合格</el-radio-button>
            <el-radio-button value="FAIL">不合格</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检验机构"><el-input v-model="inspectionForm.inspector" /></el-form-item>
        <el-form-item label="报告编号"><el-input v-model="inspectionForm.reportNo" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="inspectionForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="inspectionDialog = false">取消</el-button>
        <el-button type="primary" @click="saveInspection">保存</el-button>
      </template>
    </el-dialog>

    <!-- 配件对话框 -->
    <el-dialog v-model="partDialog" title="登记配件更换" width="520px">
      <el-form label-width="90px">
        <el-form-item label="电梯" required>
          <el-select v-model="partForm.elevatorId" style="width: 100%">
            <el-option v-for="e in elevators" :key="e.id" :label="`${e.code}（${e.building?.name}）`" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="配件名称" required><el-input v-model="partForm.partName" /></el-form-item></el-col>
          <el-col :span="12">
            <el-form-item label="更换日期">
              <el-date-picker v-model="partForm.replaceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="更换原因"><el-input v-model="partForm.reason" type="textarea" :rows="2" /></el-form-item>
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item label="费用(元)"><el-input-number v-model="partForm.cost" :min="0" style="width: 100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item label="更换方"><el-input v-model="partForm.replacedBy" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="partDialog = false">取消</el-button>
        <el-button type="primary" @click="savePart">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import api from '../api'
import { ELEVATOR_STATUS, EVENT_STATUS, INSPECTION_RESULT } from '../utils/dict'
import { fmtTime } from '../utils/format'

const tab = ref('elevators')
const loading = ref(false)
const buildings = ref([])
const elevators = ref([])
const companies = ref([])
const contracts = ref([])
const inspections = ref([])
const parts = ref([])
const elevatorFilter = ref(null)
const repeatFaultIds = ref([])

const elevatorDialog = ref(false)
const elevatorDrawer = ref(false)
const companyDialog = ref(false)
const contractDialog = ref(false)
const inspectionDialog = ref(false)
const partDialog = ref(false)
const drawerData = ref({})

const elevatorForm = reactive({})
const companyForm = reactive({})
const contractForm = reactive({})
const inspectionForm = reactive({ result: 'PASS' })
const partForm = reactive({})

const filteredElevators = computed(() =>
  elevatorFilter.value ? elevators.value.filter((e) => e.building?.id === elevatorFilter.value) : elevators.value
)

function isInspectionDue(date) {
  return date && dayjs(date).isBefore(dayjs().add(30, 'day'))
}

async function loadAll() {
  loading.value = true
  try {
    const [b, e, c, ct, ins, p] = await Promise.all([
      api.get('/buildings'), api.get('/elevators'), api.get('/companies'),
      api.get('/contracts'), api.get('/inspections'), api.get('/parts')
    ])
    buildings.value = b.data
    elevators.value = e.data
    companies.value = c.data
    contracts.value = ct.data
    inspections.value = ins.data
    parts.value = p.data
    // 反复故障电梯标记（一周内 ≥2 次困人）
    try {
      const rf = await api.get('/elevators/repeat-faults')
      repeatFaultIds.value = rf.data.map((item) => item.elevator.id)
    } catch {
      repeatFaultIds.value = []
    }
  } finally {
    loading.value = false
  }
}

// ---------- 电梯 ----------
function openElevatorDialog(row) {
  Object.keys(elevatorForm).forEach((k) => delete elevatorForm[k])
  if (row) {
    Object.assign(elevatorForm, {
      id: row.id, code: row.code, buildingId: row.building?.id, companyId: row.company?.id,
      floors: row.floors, doorZone: row.doorZone, brand: row.brand, model: row.model,
      position: row.position, installDate: row.installDate, nextInspectionDate: row.nextInspectionDate,
      status: row.status
    })
  } else {
    Object.assign(elevatorForm, { status: 'RUNNING', floors: 18 })
  }
  elevatorDialog.value = true
}

async function saveElevator() {
  if (!elevatorForm.code || !elevatorForm.buildingId) {
    ElMessage.warning('请填写电梯编号并选择楼栋')
    return
  }
  const payload = {
    code: elevatorForm.code,
    building: { id: elevatorForm.buildingId },
    company: elevatorForm.companyId ? { id: elevatorForm.companyId } : null,
    floors: elevatorForm.floors, doorZone: elevatorForm.doorZone, brand: elevatorForm.brand,
    model: elevatorForm.model, position: elevatorForm.position,
    installDate: elevatorForm.installDate, nextInspectionDate: elevatorForm.nextInspectionDate,
    status: elevatorForm.status
  }
  if (elevatorForm.id) {
    await api.put(`/elevators/${elevatorForm.id}`, payload)
  } else {
    await api.post('/elevators', payload)
  }
  ElMessage.success('已保存')
  elevatorDialog.value = false
  loadAll()
}

async function openElevatorDetail(row) {
  const res = await api.get(`/elevators/${row.id}`)
  drawerData.value = res.data
  elevatorDrawer.value = true
}

// ---------- 维保单位 ----------
function openCompanyDialog(row) {
  Object.keys(companyForm).forEach((k) => delete companyForm[k])
  if (row) {
    Object.assign(companyForm, row)
  }
  companyDialog.value = true
}

async function saveCompany() {
  if (!companyForm.name) {
    ElMessage.warning('请填写单位名称')
    return
  }
  if (companyForm.id) {
    await api.put(`/companies/${companyForm.id}`, companyForm)
  } else {
    await api.post('/companies', companyForm)
  }
  ElMessage.success('已保存')
  companyDialog.value = false
  loadAll()
}

// ---------- 合同 ----------
function openContractDialog() {
  Object.keys(contractForm).forEach((k) => delete contractForm[k])
  contractDialog.value = true
}

async function saveContract() {
  if (!contractForm.contractNo || !contractForm.elevatorId || !contractForm.companyId) {
    ElMessage.warning('请填写合同编号并选择电梯和维保单位')
    return
  }
  await api.post('/contracts', {
    contractNo: contractForm.contractNo,
    elevator: { id: contractForm.elevatorId },
    company: { id: contractForm.companyId },
    startDate: contractForm.startDate, endDate: contractForm.endDate,
    annualFee: contractForm.annualFee, content: contractForm.content
  })
  ElMessage.success('已保存')
  contractDialog.value = false
  loadAll()
}

// ---------- 年检 ----------
function openInspectionDialog() {
  Object.keys(inspectionForm).forEach((k) => delete inspectionForm[k])
  inspectionForm.result = 'PASS'
  inspectionDialog.value = true
}

async function saveInspection() {
  if (!inspectionForm.elevatorId) {
    ElMessage.warning('请选择电梯')
    return
  }
  await api.post('/inspections', {
    elevator: { id: inspectionForm.elevatorId },
    inspectionDate: inspectionForm.inspectionDate,
    nextInspectionDate: inspectionForm.nextInspectionDate,
    result: inspectionForm.result,
    inspector: inspectionForm.inspector,
    reportNo: inspectionForm.reportNo,
    remark: inspectionForm.remark
  })
  ElMessage.success('已登记')
  inspectionDialog.value = false
  loadAll()
}

// ---------- 配件 ----------
function openPartDialog() {
  Object.keys(partForm).forEach((k) => delete partForm[k])
  partDialog.value = true
}

async function savePart() {
  if (!partForm.elevatorId || !partForm.partName) {
    ElMessage.warning('请选择电梯并填写配件名称')
    return
  }
  await api.post('/parts', {
    elevator: { id: partForm.elevatorId },
    partName: partForm.partName,
    replaceDate: partForm.replaceDate,
    reason: partForm.reason,
    cost: partForm.cost,
    replacedBy: partForm.replacedBy
  })
  ElMessage.success('已登记')
  partDialog.value = false
  loadAll()
}

onMounted(loadAll)
</script>
