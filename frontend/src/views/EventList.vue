<template>
  <div>
    <el-card class="panel-card" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px">
          <span>困人事件管理</span>
          <el-button type="danger" @click="$router.push('/events/new')">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>接警登记
          </el-button>
        </div>
      </template>

      <!-- 筛选 -->
      <el-form inline style="margin-bottom: 4px">
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable style="width: 130px" @change="load">
            <el-option label="进行中" value="ACTIVE" />
            <el-option v-for="(v, k) in EVENT_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="楼栋">
          <el-select v-model="filters.buildingId" placeholder="全部" clearable style="width: 160px" @change="load">
            <el-option v-for="b in buildings" :key="b.id" :label="b.name" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="电梯">
          <el-select v-model="filters.elevatorId" placeholder="全部" clearable filterable style="width: 140px" @change="load">
            <el-option v-for="e in elevators" :key="e.id" :label="e.code" :value="e.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button @click="resetFilters">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table :data="events" v-loading="loading" @row-click="(row) => $router.push(`/events/${row.id}`)" style="cursor: pointer">
        <el-table-column prop="eventNo" label="事件编号" width="140" fixed>
          <template #default="{ row }"><span class="mono">{{ row.eventNo }}</span></template>
        </el-table-column>
        <el-table-column label="电梯" width="100">
          <template #default="{ row }"><span class="mono">{{ row.elevator?.code }}</span></template>
        </el-table-column>
        <el-table-column label="楼栋" width="130">
          <template #default="{ row }">{{ row.building?.name }}</template>
        </el-table-column>
        <el-table-column label="报警来源" width="100">
          <template #default="{ row }">{{ ALARM_SOURCE[row.alarmSource] }}</template>
        </el-table-column>
        <el-table-column label="被困楼层" width="90">
          <template #default="{ row }">{{ row.trappedFloor || '—' }}</template>
        </el-table-column>
        <el-table-column label="被困人员" width="110">
          <template #default="{ row }">
            {{ row.passengerCount }} 人
            <el-tag v-if="row.elderlyCount > 0" size="small" type="warning" style="margin-left: 2px">老{{ row.elderlyCount }}</el-tag>
            <el-tag v-if="row.childrenCount > 0" size="small" type="warning" style="margin-left: 2px">幼{{ row.childrenCount }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="EVENT_STATUS[row.status]?.type" size="small">{{ EVENT_STATUS[row.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="救援时长" width="90">
          <template #default="{ row }">
            <span v-if="rescueMinutes(row) != null">{{ rescueMinutes(row) }} 分钟</span>
            <span v-else-if="row.status !== 'CLOSED'" style="color: #e64545">已困 {{ elapsedMinutes(row) }}′</span>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="异常标记" min-width="180">
          <template #default="{ row }">
            <template v-for="flag in activeFlags(row)" :key="flag.key">
              <el-tag class="flag-tag" type="danger" size="small" effect="plain">{{ flag.label }}</el-tag>
            </template>
            <span v-if="activeFlags(row).length === 0" style="color: #b0b7c3">—</span>
          </template>
        </el-table-column>
        <el-table-column label="报警时间" width="150">
          <template #default="{ row }">{{ fmtTime(row.alarmTime) }}</template>
        </el-table-column>
        <el-table-column label="责任判定" width="110">
          <template #default="{ row }">{{ row.responsibility ? RESPONSIBILITY[row.responsibility] : '—' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api'
import { ALARM_SOURCE, EVENT_STATUS, EVENT_FLAGS, RESPONSIBILITY } from '../utils/dict'
import { fmtTime, rescueMinutes, elapsedMinutes } from '../utils/format'

const route = useRoute()
const loading = ref(false)
const events = ref([])
const buildings = ref([])
const elevators = ref([])

const filters = reactive({
  status: route.query.status || '',
  buildingId: null,
  elevatorId: null
})

function activeFlags(row) {
  return EVENT_FLAGS.filter((f) => row[f.key])
}

async function load() {
  loading.value = true
  try {
    const params = {}
    if (filters.status) params.status = filters.status
    if (filters.buildingId) params.buildingId = filters.buildingId
    if (filters.elevatorId) params.elevatorId = filters.elevatorId
    const res = await api.get('/events', { params })
    events.value = res.data
  } finally {
    loading.value = false
  }
}

function resetFilters() {
  filters.status = ''
  filters.buildingId = null
  filters.elevatorId = null
  load()
}

onMounted(async () => {
  load()
  const [b, e] = await Promise.all([api.get('/buildings'), api.get('/elevators')])
  buildings.value = b.data
  elevators.value = e.data
})
</script>
