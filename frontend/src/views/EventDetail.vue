<template>
  <div v-loading="loading">
    <template v-if="event">
      <!-- 头部 -->
      <div class="event-header">
        <div style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px">
          <div>
            <span class="event-no mono">{{ event.eventNo }}</span>
            <el-tag :type="EVENT_STATUS[event.status]?.type" effect="dark" style="margin-left: 10px">
              {{ EVENT_STATUS[event.status]?.label }}
            </el-tag>
            <el-tag v-for="flag in activeFlags" :key="flag.key" type="danger" effect="plain" style="margin-left: 6px">
              {{ flag.label }}
            </el-tag>
          </div>
          <div style="font-size: 13px">
            <span v-if="event.status !== 'CLOSED'" style="color: #fca5a5; font-weight: 600">
              ⏱ 已被困 {{ elapsedMinutes(event) }} 分钟
            </span>
            <span v-else>救援历时 {{ rescueMinutes(event) }} 分钟 ｜ 关闭于 {{ fmtTime(event.closedAt) }}</span>
          </div>
        </div>
        <div class="event-meta">
          <span>🛗 {{ event.elevator?.code }}（{{ event.elevator?.brand }} {{ event.elevator?.model }}）</span>
          <span>🏢 {{ event.building?.name }} ｜ 被困楼层：{{ event.trappedFloor || '未知' }}</span>
          <span>👥 {{ event.passengerCount }} 人<template v-if="event.elderlyCount > 0">（老人 {{ event.elderlyCount }}）</template><template v-if="event.childrenCount > 0">（儿童 {{ event.childrenCount }}）</template></span>
          <span>📞 {{ ALARM_SOURCE[event.alarmSource] }} ｜ {{ fmtTime(event.alarmTime) }}</span>
          <span>🔧 维保：{{ event.elevator?.company?.name || '未登记' }}</span>
        </div>
      </div>

      <!-- 进度条 -->
      <el-card class="stage-card" shadow="never">
        <el-steps :active="stepActive" align-center finish-status="success">
          <el-step title="接警登记" :description="fmtTime(event.alarmTime)" />
          <el-step title="通知调度" :description="fmtTime(event.maintenanceNotifiedAt)" />
          <el-step title="维保到场" :description="fmtTime(event.maintenanceArrivedAt)" />
          <el-step title="困人释放" :description="fmtTime(event.releasedAt)" />
          <el-step title="复位复检" :description="fmtTime(event.resetAt)" />
          <el-step title="事件关闭" :description="fmtTime(event.closedAt)" />
        </el-steps>
      </el-card>

      <el-row :gutter="16">
        <!-- 左列：处置流程 -->
        <el-col :span="15">
          <!-- 1. 接警信息 + 异常标记 -->
          <el-card class="stage-card is-done" shadow="never">
            <template #header>
              <div class="stage-title"><span class="stage-index">1</span>接警信息（接警人：{{ event.handler?.realName || '—' }}）</div>
            </template>
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="报警来源">{{ ALARM_SOURCE[event.alarmSource] }}</el-descriptions-item>
              <el-descriptions-item label="报警时间">{{ fmtTime(event.alarmTime) }}</el-descriptions-item>
              <el-descriptions-item label="被困楼层">{{ event.trappedFloor || '—' }}</el-descriptions-item>
              <el-descriptions-item label="门区位置">{{ event.doorZone || '—' }}</el-descriptions-item>
              <el-descriptions-item label="通话状态">
                <el-tag :type="CALL_STATUS[event.callStatus]?.type" size="small">{{ CALL_STATUS[event.callStatus]?.label || '—' }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="求助人">{{ event.reporterName || '—' }} {{ event.reporterPhone }}</el-descriptions-item>
              <el-descriptions-item label="报警详情" :span="2">{{ event.reportDetail || '—' }}</el-descriptions-item>
            </el-descriptions>

            <el-divider content-position="left" style="margin: 14px 0">异常情况标记</el-divider>
            <el-checkbox v-for="flag in EVENT_FLAGS" :key="flag.key" v-model="flagsForm[flag.key]" :label="flag.label"
              :disabled="event.status === 'CLOSED'" style="margin-right: 16px" />
            <el-input v-if="flagsForm.compensationRequested" v-model="flagsForm.compensationDetail" type="textarea" :rows="2"
              placeholder="赔偿诉求说明" style="margin-top: 10px" :disabled="event.status === 'CLOSED'" />
            <div v-if="event.status !== 'CLOSED'" style="margin-top: 10px">
              <el-button size="small" @click="saveFlags">保存标记</el-button>
            </div>
          </el-card>

          <!-- 2. 通知调度 -->
          <el-card class="stage-card" :class="{ 'is-done': stageDone('DISPATCHED'), 'is-disabled': !stageReach('PENDING') }" shadow="never">
            <template #header>
              <div class="stage-title"><span class="stage-index">2</span>通知调度</div>
            </template>
            <template v-if="event.status === 'PENDING'">
              <el-alert type="warning" :closable="false" show-icon title="请同步通知维保单位、保安、楼栋管家；如有老人儿童或通话中断，建议同步通知消防救援"
                style="margin-bottom: 12px" />
              <el-checkbox v-model="dispatchForm.notifyMaintenance" label="通知维保单位（30 分钟内到场）" />
              <el-checkbox v-model="dispatchForm.notifySecurity" label="通知保安（现场秩序维护）" />
              <el-checkbox v-model="dispatchForm.notifyButler" label="通知楼栋管家（业主安抚）" />
              <el-checkbox v-model="dispatchForm.notifyFire" label="通知消防救援（119 联动）" />
              <el-input v-model="dispatchForm.note" placeholder="调度备注（可空）" style="margin: 12px 0" />
              <div>
                <el-button type="danger" :loading="acting" @click="doDispatch">确认通知并调度</el-button>
              </div>
            </template>
            <template v-else>
              <el-descriptions :column="2" size="small">
                <el-descriptions-item label="维保通知">{{ fmtTime(event.maintenanceNotifiedAt) }}</el-descriptions-item>
                <el-descriptions-item label="保安通知">{{ fmtTime(event.securityNotifiedAt) }}</el-descriptions-item>
                <el-descriptions-item label="管家通知">{{ fmtTime(event.butlerNotifiedAt) }}</el-descriptions-item>
                <el-descriptions-item label="消防通知">{{ fmtTime(event.fireNotifiedAt) }}</el-descriptions-item>
              </el-descriptions>
            </template>
          </el-card>

          <!-- 3. 到场登记 -->
          <el-card class="stage-card" :class="{ 'is-done': stageDone('ARRIVED'), 'is-disabled': !stageDone('DISPATCHED') }" shadow="never">
            <template #header>
              <div class="stage-title"><span class="stage-index">3</span>到场登记</div>
            </template>
            <template v-if="event.status === 'DISPATCHED' || event.status === 'ARRIVED'">
              <el-form label-width="90px" size="default">
                <el-form-item label="到场方">
                  <el-radio-group v-model="arriveForm.party">
                    <el-radio-button value="MAINTENANCE">维保人员</el-radio-button>
                    <el-radio-button value="FIRE">消防救援</el-radio-button>
                  </el-radio-group>
                </el-form-item>
                <el-form-item label="到场时间">
                  <el-date-picker v-model="arriveForm.arrivedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                    placeholder="默认当前时间" style="width: 240px" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="acting" @click="doArrive">登记到场</el-button>
                  <span style="margin-left: 10px; font-size: 12px; color: #8a94a8">超过 30 分钟到场将自动标记“维保迟到”</span>
                </el-form-item>
              </el-form>
              <el-descriptions v-if="event.maintenanceArrivedAt || event.fireArrivedAt" :column="2" size="small" border>
                <el-descriptions-item label="维保到场">{{ fmtTime(event.maintenanceArrivedAt) }}</el-descriptions-item>
                <el-descriptions-item label="消防到场">{{ fmtTime(event.fireArrivedAt) }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <template v-else-if="stageDone('ARRIVED')">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="维保到场">{{ fmtTime(event.maintenanceArrivedAt) }}</el-descriptions-item>
                <el-descriptions-item label="消防到场">{{ fmtTime(event.fireArrivedAt) }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-text v-else type="info" size="small">完成通知调度后开放</el-text>
          </el-card>

          <!-- 4. 救援释放 -->
          <el-card class="stage-card" :class="{ 'is-done': stageDone('RELEASED'), 'is-disabled': !stageDone('DISPATCHED') }" shadow="never">
            <template #header>
              <div class="stage-title"><span class="stage-index">4</span>困人释放登记</div>
            </template>
            <template v-if="event.status === 'ARRIVED' || event.status === 'DISPATCHED'">
              <el-form label-width="100px">
                <el-row :gutter="12">
                  <el-col :span="12">
                    <el-form-item label="释放时间">
                      <el-date-picker v-model="releaseForm.releasedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                        placeholder="默认当前时间" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="开门方式">
                      <el-select v-model="releaseForm.doorOpenMethod" filterable allow-create style="width: 100%" placeholder="选择或填写">
                        <el-option v-for="m in DOOR_OPEN_METHODS" :key="m" :label="m" :value="m" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-row :gutter="12">
                  <el-col :span="12">
                    <el-form-item label="故障代码">
                      <el-input v-model="releaseForm.faultCode" placeholder="如 E43-门机控制器故障" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="需要医疗协助">
                      <el-switch v-model="releaseForm.medicalAssistance" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="乘客身体状态">
                  <el-input v-model="releaseForm.passengerHealth" placeholder="如：乘客情绪稳定，无身体不适" />
                </el-form-item>
                <el-form-item label="救援说明">
                  <el-input v-model="releaseForm.rescueNote" type="textarea" :rows="2" placeholder="救援过程说明（可空）" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="acting" @click="doRelease">确认乘客已释放</el-button>
                </el-form-item>
              </el-form>
            </template>
            <template v-else-if="stageDone('RELEASED')">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="释放时间">{{ fmtTime(event.releasedAt) }}</el-descriptions-item>
                <el-descriptions-item label="救援时长">{{ rescueMinutes(event) }} 分钟</el-descriptions-item>
                <el-descriptions-item label="开门方式">{{ event.doorOpenMethod || '—' }}</el-descriptions-item>
                <el-descriptions-item label="故障代码">{{ event.faultCode || '—' }}</el-descriptions-item>
                <el-descriptions-item label="乘客状态">{{ event.passengerHealth || '—' }}</el-descriptions-item>
                <el-descriptions-item label="医疗协助">{{ event.medicalAssistance ? '需要' : '不需要' }}</el-descriptions-item>
                <el-descriptions-item v-if="event.rescueNote" label="救援说明" :span="2">{{ event.rescueNote }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-text v-else type="info" size="small">完成通知调度后开放</el-text>
          </el-card>

          <!-- 5. 复位复检 -->
          <el-card class="stage-card" :class="{ 'is-done': stageDone('RESET'), 'is-disabled': !stageDone('RELEASED') }" shadow="never">
            <template #header>
              <div class="stage-title"><span class="stage-index">5</span>复位 / 停梯 / 复检</div>
            </template>
            <template v-if="event.status === 'RELEASED'">
              <el-form label-width="100px">
                <el-row :gutter="12">
                  <el-col :span="12">
                    <el-form-item label="复位时间">
                      <el-date-picker v-model="resetForm.resetAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                        placeholder="默认当前时间" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="复检时间">
                      <el-date-picker v-model="resetForm.recheckedAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
                        placeholder="可空" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="是否停梯">
                  <el-switch v-model="resetForm.elevatorStopped" active-text="停梯待检" inactive-text="恢复运行" />
                  <span style="margin-left: 10px; font-size: 12px; color: #8a94a8">停梯后建议同步发布楼栋公告（右侧面板）</span>
                </el-form-item>
                <el-form-item label="复检结果">
                  <el-input v-model="resetForm.recheckResult" placeholder="如：复位后试运行正常，故障代码清除" />
                </el-form-item>
                <el-form-item>
                  <el-button type="primary" :loading="acting" @click="doReset">确认复位登记</el-button>
                </el-form-item>
              </el-form>
            </template>
            <template v-else-if="stageDone('RESET')">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="复位时间">{{ fmtTime(event.resetAt) }}</el-descriptions-item>
                <el-descriptions-item label="电梯状态">
                  <el-tag :type="event.elevatorStopped ? 'danger' : 'success'" size="small">
                    {{ event.elevatorStopped ? '停梯待检' : '恢复运行' }}
                  </el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="复检时间">{{ fmtTime(event.recheckedAt) }}</el-descriptions-item>
                <el-descriptions-item label="复检结果">{{ event.recheckResult || '—' }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-text v-else type="info" size="small">乘客释放后开放</el-text>
          </el-card>

          <!-- 6. 关闭归档 -->
          <el-card class="stage-card" :class="{ 'is-done': event.status === 'CLOSED', 'is-disabled': !stageDone('RESET') }" shadow="never">
            <template #header>
              <div class="stage-title"><span class="stage-index">6</span>责任判定与关闭归档</div>
            </template>
            <template v-if="event.status === 'RESET'">
              <el-form label-width="100px">
                <el-row :gutter="12">
                  <el-col :span="12">
                    <el-form-item label="责任判定" required>
                      <el-select v-model="closeForm.responsibility" style="width: 100%">
                        <el-option v-for="(v, k) in RESPONSIBILITY" :key="k" :label="v" :value="k" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="费用承担">
                      <el-select v-model="closeForm.costBearer" style="width: 100%" clearable>
                        <el-option v-for="(v, k) in COST_BEARER" :key="k" :label="v" :value="k" />
                      </el-select>
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="责任说明">
                  <el-input v-model="closeForm.responsibilityDetail" placeholder="责任认定依据" />
                </el-form-item>
                <el-row :gutter="12">
                  <el-col :span="12">
                    <el-form-item label="费用金额(元)">
                      <el-input-number v-model="closeForm.costAmount" :min="0" :precision="2" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                  <el-col :span="12">
                    <el-form-item label="整改期限">
                      <el-date-picker v-model="closeForm.rectificationDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
                    </el-form-item>
                  </el-col>
                </el-row>
                <el-form-item label="整改要求">
                  <el-input v-model="closeForm.rectification" type="textarea" :rows="2" placeholder="维保整改要求" />
                </el-form-item>
                <el-form-item label="业主通知">
                  <el-switch v-model="closeForm.ownerNotified" active-text="已通知业主处理结果" />
                </el-form-item>
                <el-form-item label="关闭备注">
                  <el-input v-model="closeForm.closeRemark" placeholder="可空" />
                </el-form-item>
                <el-form-item>
                  <el-button type="success" :loading="acting" @click="doClose">确认关闭事件并归档</el-button>
                </el-form-item>
              </el-form>
            </template>
            <template v-else-if="event.status === 'CLOSED'">
              <el-descriptions :column="2" size="small" border>
                <el-descriptions-item label="责任判定">{{ RESPONSIBILITY[event.responsibility] }}</el-descriptions-item>
                <el-descriptions-item label="费用承担">{{ event.costBearer ? COST_BEARER[event.costBearer] : '—' }}
                  <template v-if="event.costAmount">（{{ event.costAmount }} 元）</template>
                </el-descriptions-item>
                <el-descriptions-item label="责任说明" :span="2">{{ event.responsibilityDetail || '—' }}</el-descriptions-item>
                <el-descriptions-item label="整改要求" :span="2">{{ event.rectification || '—' }}</el-descriptions-item>
                <el-descriptions-item label="整改期限">{{ event.rectificationDeadline || '—' }}</el-descriptions-item>
                <el-descriptions-item label="整改状态">
                  <el-tag :type="event.rectificationDone ? 'success' : 'warning'" size="small">
                    {{ event.rectificationDone ? '已完成' : '未完成' }}
                  </el-tag>
                  <el-button v-if="!event.rectificationDone" size="small" link type="primary" style="margin-left: 8px"
                    @click="doRectify(true)">标记完成</el-button>
                </el-descriptions-item>
                <el-descriptions-item label="业主通知">{{ event.ownerNotified ? '已通知' : '未通知' }}</el-descriptions-item>
                <el-descriptions-item label="关闭时间">{{ fmtTime(event.closedAt) }}</el-descriptions-item>
                <el-descriptions-item v-if="event.compensationDetail" label="赔偿说明" :span="2">{{ event.compensationDetail }}</el-descriptions-item>
                <el-descriptions-item v-if="event.closeRemark" label="关闭备注" :span="2">{{ event.closeRemark }}</el-descriptions-item>
              </el-descriptions>
            </template>
            <el-text v-else type="info" size="small">复位复检后开放</el-text>
          </el-card>

          <!-- 7. 善后跟踪（关闭后） -->
          <el-card v-if="event.status === 'CLOSED'" class="stage-card" shadow="never">
            <template #header>
              <div class="stage-title"><span class="stage-index">7</span>善后跟踪：业主回访</div>
            </template>
            <el-table :data="detail.followups || []" size="small" style="margin-bottom: 12px">
              <el-table-column prop="ownerName" label="回访对象" width="100" />
              <el-table-column label="回访时间" width="150">
                <template #default="{ row }">{{ fmtTime(row.followupTime) }}</template>
              </el-table-column>
              <el-table-column label="满意度" width="120">
                <template #default="{ row }"><el-rate :model-value="row.satisfaction" disabled /></template>
              </el-table-column>
              <el-table-column prop="feedback" label="反馈内容" />
              <el-table-column prop="followerName" label="回访人" width="80" />
            </el-table>
            <el-form inline>
              <el-form-item label="业主">
                <el-input v-model="followupForm.ownerName" placeholder="姓名" style="width: 110px" />
              </el-form-item>
              <el-form-item label="电话">
                <el-input v-model="followupForm.ownerPhone" placeholder="电话" style="width: 130px" />
              </el-form-item>
              <el-form-item label="满意度">
                <el-rate v-model="followupForm.satisfaction" />
              </el-form-item>
              <el-form-item label="反馈">
                <el-input v-model="followupForm.feedback" placeholder="回访反馈" style="width: 220px" />
              </el-form-item>
              <el-form-item>
                <el-button type="primary" size="small" :loading="acting" @click="doFollowup">添加回访</el-button>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <!-- 右列 -->
        <el-col :span="9">
          <!-- 通话安抚 -->
          <el-card class="stage-card" shadow="never">
            <template #header>
              <div class="stage-title">📞 通话安抚记录</div>
            </template>
            <div v-if="(detail.calls || []).length === 0" style="color: #8a94a8; font-size: 13px; margin-bottom: 10px">
              暂无通话记录
            </div>
            <div v-for="call in detail.calls || []" :key="call.id" style="border-left: 3px solid #2563eb; padding: 4px 10px; margin-bottom: 10px; background: #f6f8fc; border-radius: 4px">
              <div style="font-size: 12px; color: #8a94a8">
                {{ fmtTime(call.callTime, 'MM-DD HH:mm') }} ｜ {{ call.caller?.realName }} ｜ {{ call.passengerState }}
              </div>
              <div style="font-size: 13px; margin-top: 2px">{{ call.content }}</div>
            </div>
            <template v-if="event.status !== 'CLOSED'">
              <el-divider style="margin: 10px 0" />
              <el-input v-model="callForm.passengerState" placeholder="乘客状态（如：情绪平稳）" size="small" style="margin-bottom: 8px" />
              <el-input v-model="callForm.content" type="textarea" :rows="2" placeholder="通话内容" size="small" />
              <el-select v-model="callForm.callStatus" size="small" style="width: 100%; margin-top: 8px" placeholder="更新通话状态">
                <el-option label="通话顺畅" value="SMOOTH" />
                <el-option label="时断时续" value="INTERMITTENT" />
                <el-option label="无法接通" value="LOST" />
              </el-select>
              <el-button type="primary" size="small" style="margin-top: 8px; width: 100%" :loading="acting" @click="doAddCall">
                记录通话
              </el-button>
            </template>
          </el-card>

          <!-- 通知记录 -->
          <el-card class="stage-card" shadow="never">
            <template #header>
              <div class="stage-title">📣 通知记录</div>
            </template>
            <el-table :data="detail.notifications || []" size="small">
              <el-table-column label="对象" width="130">
                <template #default="{ row }">{{ ROLE_LABEL[row.targetRole] }}·{{ row.targetName }}</template>
              </el-table-column>
              <el-table-column label="时间" width="90">
                <template #default="{ row }">{{ fmtTime(row.notifiedAt, 'MM-DD HH:mm') }}</template>
              </el-table-column>
              <el-table-column label="状态" width="80">
                <template #default="{ row }">
                  <el-tag :type="NOTIFY_STATUS[row.status]?.type" size="small">{{ NOTIFY_STATUS[row.status]?.label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作">
                <template #default="{ row }">
                  <template v-if="row.status === 'SENT' && event.status !== 'CLOSED'">
                    <el-button size="small" link type="success" @click="doNotify(row.id, 'ACKED')">确认</el-button>
                    <el-button size="small" link type="danger" @click="doNotify(row.id, 'UNREACHABLE')">联系不上</el-button>
                  </template>
                </template>
              </el-table-column>
            </el-table>
          </el-card>

          <!-- 楼栋公告 -->
          <el-card class="stage-card" shadow="never">
            <template #header>
              <div style="display: flex; justify-content: space-between; align-items: center">
                <div class="stage-title">📢 楼栋公告</div>
                <el-button size="small" type="primary" plain @click="noticeDialog = true">发布公告</el-button>
              </div>
            </template>
            <div v-if="(detail.notices || []).length === 0" style="color: #8a94a8; font-size: 13px">暂无关联公告</div>
            <div v-for="n in detail.notices || []" :key="n.id" style="margin-bottom: 10px">
              <el-tag :type="NOTICE_TYPE[n.type]?.type" size="small">{{ NOTICE_TYPE[n.type]?.label }}</el-tag>
              <span style="font-size: 13px; font-weight: 600; margin-left: 6px">{{ n.title }}</span>
              <div style="font-size: 12px; color: #8a94a8; margin-top: 2px">{{ n.publisherName }} ｜ {{ fmtTime(n.publishedAt) }}</div>
            </div>
          </el-card>

          <!-- 时间线 -->
          <el-card class="stage-card" shadow="never">
            <template #header>
              <div class="stage-title">🕒 处置时间线</div>
            </template>
            <el-timeline style="padding-left: 4px">
              <el-timeline-item v-for="log in [...(detail.logs || [])].reverse()" :key="log.id"
                :timestamp="fmtTime(log.createdAt, 'MM-DD HH:mm:ss')" placement="top"
                :type="log.action === '事件关闭' ? 'success' : log.action === '困人释放' ? 'primary' : ''">
                <div class="timeline-action">{{ log.action }}</div>
                <div class="timeline-meta">操作人：{{ log.actorName }}</div>
                <div v-if="log.detail" class="timeline-detail">{{ log.detail }}</div>
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>

      <!-- 发布公告对话框 -->
      <el-dialog v-model="noticeDialog" title="发布楼栋公告" width="560px">
        <el-form label-width="90px">
          <el-form-item label="公告类型">
            <el-select v-model="noticeForm.type" style="width: 100%" @change="onNoticeTypeChange">
              <el-option v-for="(v, k) in NOTICE_TYPE" :key="k" :label="v.label" :value="k" />
            </el-select>
          </el-form-item>
          <el-form-item label="标题">
            <el-input v-model="noticeForm.title" />
          </el-form-item>
          <el-form-item label="内容">
            <el-input v-model="noticeForm.content" type="textarea" :rows="4"
              placeholder="向本楼栋业主说明：是否停用电梯、是否开放备用梯、老人上下楼临时协助安排等" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="noticeDialog = false">取消</el-button>
          <el-button type="primary" :loading="acting" @click="doPublishNotice">发布</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import {
  EVENT_STATUS, ALARM_SOURCE, CALL_STATUS, RESPONSIBILITY, COST_BEARER,
  NOTIFY_STATUS, ROLE_LABEL, NOTICE_TYPE, DOOR_OPEN_METHODS, EVENT_FLAGS
} from '../utils/dict'
import { fmtTime, rescueMinutes, elapsedMinutes } from '../utils/format'

const STATUS_ORDER = ['PENDING', 'DISPATCHED', 'ARRIVED', 'RELEASED', 'RESET', 'CLOSED']

const route = useRoute()
const id = route.params.id
const loading = ref(false)
const acting = ref(false)
const detail = ref(null)
const noticeDialog = ref(false)

const event = computed(() => detail.value?.event)
const activeFlags = computed(() => EVENT_FLAGS.filter((f) => event.value?.[f.key]))
const stepActive = computed(() => (event.value ? STATUS_ORDER.indexOf(event.value.status) + 1 : 0))

const dispatchForm = reactive({ notifyMaintenance: true, notifySecurity: true, notifyButler: true, notifyFire: false, note: '' })
const arriveForm = reactive({ party: 'MAINTENANCE', arrivedAt: '' })
const releaseForm = reactive({ releasedAt: '', doorOpenMethod: '', faultCode: '', passengerHealth: '', medicalAssistance: false, rescueNote: '' })
const resetForm = reactive({ resetAt: '', elevatorStopped: false, recheckedAt: '', recheckResult: '' })
const closeForm = reactive({ responsibility: 'MAINTENANCE', responsibilityDetail: '', costBearer: null, costAmount: null, rectification: '', rectificationDeadline: '', ownerNotified: false, closeRemark: '' })
const callForm = reactive({ passengerState: '', content: '', callStatus: '' })
const flagsForm = reactive({})
const followupForm = reactive({ ownerName: '', ownerPhone: '', satisfaction: 5, feedback: '' })
const noticeForm = reactive({ type: 'STOP_NOTICE', title: '', content: '' })

function stageDone(status) {
  return event.value && STATUS_ORDER.indexOf(event.value.status) >= STATUS_ORDER.indexOf(status)
}

function stageReach(status) {
  return stageDone(status)
}

async function load() {
  loading.value = true
  try {
    const res = await api.get(`/events/${id}`)
    detail.value = res.data
    EVENT_FLAGS.forEach((f) => { flagsForm[f.key] = !!res.data.event[f.key] })
    flagsForm.compensationDetail = res.data.event.compensationDetail || ''
  } finally {
    loading.value = false
  }
}

async function act(fn, successMsg) {
  acting.value = true
  try {
    await fn()
    if (successMsg) ElMessage.success(successMsg)
    await load()
  } finally {
    acting.value = false
  }
}

function doDispatch() {
  const f = dispatchForm
  if (!f.notifyMaintenance && !f.notifySecurity && !f.notifyButler && !f.notifyFire) {
    ElMessage.warning('请至少选择一方进行通知')
    return
  }
  act(() => api.post(`/events/${id}/dispatch`, f), '调度通知已发出')
}

function doArrive() {
  act(() => api.post(`/events/${id}/arrive`, arriveForm), '到场已登记')
}

function doRelease() {
  act(() => api.post(`/events/${id}/release`, releaseForm), '乘客释放已登记')
}

function doReset() {
  act(() => api.post(`/events/${id}/reset`, resetForm), '复位复检已登记')
}

async function doClose() {
  await ElMessageBox.confirm('关闭后事件进入档案，不可再处置，确认关闭？', '关闭事件', { type: 'warning' })
  act(() => api.post(`/events/${id}/close`, closeForm), '事件已关闭归档')
}

function doAddCall() {
  if (!callForm.content) {
    ElMessage.warning('请填写通话内容')
    return
  }
  act(async () => {
    await api.post(`/events/${id}/calls`, { ...callForm, callStatus: callForm.callStatus || null })
    callForm.content = ''
    callForm.passengerState = ''
  }, '通话已记录')
}

function saveFlags() {
  act(() => api.put(`/events/${id}/flags`, flagsForm), '异常标记已保存')
}

function doNotify(notificationId, status) {
  act(() => api.put(`/events/${id}/notifications/${notificationId}`, { status }), '通知状态已更新')
}

function doFollowup() {
  if (!followupForm.ownerName) {
    ElMessage.warning('请填写回访对象')
    return
  }
  act(async () => {
    await api.post(`/events/${id}/followups`, followupForm)
    followupForm.ownerName = ''
    followupForm.ownerPhone = ''
    followupForm.feedback = ''
  }, '回访已记录')
}

function doRectify(done) {
  act(() => api.put(`/events/${id}/rectification`, { done }), '整改状态已更新')
}

function onNoticeTypeChange(type) {
  const buildingName = event.value?.building?.name || ''
  const code = event.value?.elevator?.code || ''
  const suggestions = {
    STOP_NOTICE: `${buildingName} ${code} 电梯停梯公告`,
    BACKUP_LIFT: `${buildingName} 备用电梯开放通知`,
    ELDERLY_ASSIST: `${buildingName} 高龄业主上下楼临时协助安排`,
    RECHECK: `${buildingName} ${code} 电梯复检通知`,
    GENERAL: ''
  }
  noticeForm.title = suggestions[type] || ''
}

function doPublishNotice() {
  if (!noticeForm.title || !noticeForm.content) {
    ElMessage.warning('请填写公告标题和内容')
    return
  }
  act(async () => {
    await api.post('/notices', {
      building: { id: event.value.building.id },
      eventId: event.value.id,
      type: noticeForm.type,
      title: noticeForm.title,
      content: noticeForm.content
    })
    noticeDialog.value = false
    noticeForm.content = ''
  }, '公告已发布')
}

onMounted(load)
</script>
