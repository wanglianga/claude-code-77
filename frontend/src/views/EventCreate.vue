<template>
  <div>
    <el-card class="panel-card" shadow="never" style="max-width: 860px; margin: 0 auto">
      <template #header>
        <div style="display: flex; align-items: center; gap: 10px">
          <el-icon color="#e64545"><AlarmClock /></el-icon>
          <span>困人接警登记</span>
          <el-tag type="danger" size="small" effect="plain">接警后请立即调度通知各方</el-tag>
        </div>
      </template>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="故障电梯" prop="elevatorId">
              <el-select v-model="form.elevatorId" placeholder="选择电梯" filterable style="width: 100%" @change="onElevatorChange">
                <el-option-group v-for="b in buildings" :key="b.id" :label="b.name">
                  <el-option v-for="e in elevatorsOf(b.id)" :key="e.id" :label="`${e.code}（${e.position || ''}）`" :value="e.id" />
                </el-option-group>
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="报警来源" prop="alarmSource">
              <el-radio-group v-model="form.alarmSource">
                <el-radio-button value="IOT">物联网报警</el-radio-button>
                <el-radio-button value="PHONE">电话求助</el-radio-button>
                <el-radio-button value="PATROL">巡查发现</el-radio-button>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="报警时间" prop="alarmTime">
              <el-date-picker v-model="form.alarmTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                style="width: 100%" placeholder="默认为当前时间" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="被困楼层" prop="trappedFloor">
              <el-input v-model="form.trappedFloor" placeholder="如：12 层 / 7-8 层之间" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="6">
            <el-form-item label="轿厢人数" prop="passengerCount">
              <el-input-number v-model="form.passengerCount" :min="1" :max="30" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="其中老人" label-width="90px">
              <el-input-number v-model="form.elderlyCount" :min="0" :max="30" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="其中儿童" label-width="90px">
              <el-input-number v-model="form.childrenCount" :min="0" :max="30" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="通话状态" label-width="90px">
              <el-select v-model="form.callStatus" style="width: 100%">
                <el-option label="通话顺畅" value="SMOOTH" />
                <el-option label="时断时续" value="INTERMITTENT" />
                <el-option label="无法接通" value="LOST" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="门区位置">
              <el-input v-model="form.doorZone" placeholder="默认取电梯档案门区位置" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="求助人" label-width="90px">
              <el-input v-model="form.reporterName" placeholder="姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="联系电话" label-width="90px">
              <el-input v-model="form.reporterPhone" placeholder="手机" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="报警详情">
          <el-input v-model="form.reportDetail" type="textarea" :rows="3"
            placeholder="报警内容、轿厢内情况、乘客情绪、是否有老人儿童孕妇或身体不适人员等" />
        </el-form-item>

        <el-form-item>
          <el-button type="danger" size="large" :loading="submitting" @click="submit">
            确认接警并生成事件
          </el-button>
          <el-button size="large" @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import api from '../api'

const router = useRouter()
const formRef = ref(null)
const submitting = ref(false)
const buildings = ref([])
const elevators = ref([])

const form = reactive({
  elevatorId: null,
  alarmSource: 'IOT',
  alarmTime: dayjs().format('YYYY-MM-DDTHH:mm:ss'),
  trappedFloor: '',
  passengerCount: 1,
  elderlyCount: 0,
  childrenCount: 0,
  callStatus: 'SMOOTH',
  doorZone: '',
  reporterName: '',
  reporterPhone: '',
  reportDetail: ''
})

const rules = {
  elevatorId: [{ required: true, message: '请选择电梯', trigger: 'change' }],
  alarmSource: [{ required: true, message: '请选择报警来源', trigger: 'change' }],
  passengerCount: [{ required: true, message: '请填写轿厢人数', trigger: 'blur' }]
}

function elevatorsOf(buildingId) {
  return elevators.value.filter((e) => e.building?.id === buildingId)
}

function onElevatorChange(id) {
  const elevator = elevators.value.find((e) => e.id === id)
  if (elevator && !form.doorZone) {
    form.doorZone = elevator.doorZone || ''
  }
}

async function submit() {
  await formRef.value.validate()
  submitting.value = true
  try {
    const res = await api.post('/events', { ...form })
    ElMessage.success(`接警成功，事件编号 ${res.data.eventNo}，请立即调度通知`)
    router.push(`/events/${res.data.id}`)
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  const [b, e] = await Promise.all([api.get('/buildings'), api.get('/elevators')])
  buildings.value = b.data
  elevators.value = e.data
})
</script>
