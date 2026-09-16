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

    <!-- 整改申请与方案版本 -->
    <el-card class="panel-card" shadow="never" style="margin-bottom: 16px">
      <template #header>
        <span>停梯整改申请与方案版本（未复检通过前电梯保持停用；历史版本与复检记录不可更改）</span>
      </template>
      <el-table :data="plans" v-loading="loading">
        <el-table-column label="电梯" width="90">
          <template #default="{ row }"><span class="mono">{{ row.plan?.elevator?.code }}</span></template>
        </el-table-column>
        <el-table-column label="楼栋" width="120">
          <template #default="{ row }">{{ row.plan?.elevator?.building?.name }}</template>
        </el-table-column>
        <el-table-column label="维保单位" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.plan?.companyName }}</template>
        </el-table-column>
        <el-table-column label="申请状态" width="105">
          <template #default="{ row }">
            <el-tag :type="PLAN_STATUS[row.plan?.status]?.type" size="small">{{ PLAN_STATUS[row.plan?.status]?.label }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="方案版本" width="100">
          <template #default="{ row }">
            <el-tag v-if="row.latestVersion" size="small" effect="plain" :type="VERSION_STATUS[row.latestVersion.status]?.type">
              第 {{ row.latestVersion.versionNo }} 版
            </el-tag>
            <span v-else style="color: #8a94a8">未提交</span>
            <span v-if="row.versionCount > 1" style="font-size: 12px; color: #8a94a8">（共 {{ row.versionCount }} 版）</span>
          </template>
        </el-table-column>
        <el-table-column label="配件" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.latestVersion?.parts || '—' }}</template>
        </el-table-column>
        <el-table-column label="预计到货" width="96">
          <template #default="{ row }">{{ row.latestVersion?.expectedArrival || '—' }}</template>
        </el-table-column>
        <el-table-column label="复检人" width="140">
          <template #default="{ row }">{{ row.latestVersion?.recheckInspector || '—' }}</template>
        </el-table-column>
        <el-table-column label="公告发布" width="125">
          <template #default="{ row }">{{ row.latestVersion?.noticePublishTime ? fmtTime(row.latestVersion.noticePublishTime) : '—' }}</template>
        </el-table-column>
        <el-table-column label="申请 / 最新提交" width="160">
          <template #default="{ row }">
            <div style="font-size: 12px">{{ row.plan?.requestedBy }} ｜ {{ fmtTime(row.plan?.requestedAt, 'MM-DD HH:mm') }}</div>
            <div v-if="row.latestVersion?.submittedBy" style="font-size: 12px; color: #8a94a8">
              {{ row.latestVersion.submittedBy }} ｜ {{ fmtTime(row.latestVersion.submittedAt, 'MM-DD HH:mm') }}
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-if="['REQUESTED', 'RECHECK_FAILED'].includes(row.plan?.status) && canSubmit"
              size="small" type="primary" link @click="openSubmit(row)">
              {{ row.plan?.status === 'RECHECK_FAILED' ? '提交修订版' : '提交方案' }}
            </el-button>
            <el-button v-if="row.plan?.status === 'SUBMITTED' && row.latestVersion && canRecheck"
              size="small" type="success" link @click="openRecheck(row)">复检登记</el-button>
            <el-button size="small" link @click="openPlanDetail(row.plan.id)">版本历史</el-button>
            <el-button size="small" link @click="openSummary(row.plan.elevator)">汇总</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 老人帮扶（按整改单批次交接） -->
    <el-card class="panel-card" shadow="never">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>停梯期间老人上下楼帮扶（按整改单批次交接：电梯恢复后未办结需求自动转待复核，由管家逐条确认结案 / 续办 / 改约）</span>
          <el-button v-if="canAssist" type="primary" size="small" @click="openAssist">登记帮扶需求</el-button>
        </div>
      </template>
      <el-tabs v-model="assistTab">
        <!-- 当前停梯批次：新批次需求 + 历史批次续办转入 -->
        <el-tab-pane name="current">
          <template #label>
            当前批次帮扶
            <el-badge v-if="currentCount" :value="currentCount" type="warning" style="margin-left: 6px" />
          </template>
          <el-empty v-if="currentBatches.length === 0" description="当前无停梯整改批次，暂无帮扶需求" :image-size="60" />
          <div v-for="batch in currentBatches" :key="batch.plan.id" class="batch-block">
            <div class="batch-header">
              <el-tag type="danger" size="small">停梯中</el-tag>
              <span class="mono" style="font-weight: 700">{{ batch.plan.elevator?.code }}</span>
              <span class="batch-meta">
                整改单 #{{ batch.plan.id }} ｜ {{ PLAN_STATUS[batch.plan.status]?.label }} ｜ {{ batch.plan.companyName }}
              </span>
              <el-button size="small" link type="primary" @click="openPlanDetail(batch.plan.id)">整改单详情</el-button>
            </div>
            <el-table :data="batch.newBatch" size="small" v-loading="loading">
              <el-table-column prop="residentName" label="老人/住户" width="100" />
              <el-table-column prop="roomNo" label="房号" width="100" />
              <el-table-column prop="needDescription" label="上下楼需求" min-width="200" show-overflow-tooltip />
              <el-table-column label="临时帮扶人员（人力安排）" width="150">
                <template #default="{ row }">{{ row.helperName }}<br />
                  <span style="font-size: 12px; color: #8a94a8">{{ row.helperPhone }}</span>
                </template>
              </el-table-column>
              <el-table-column label="批次归属" width="110">
                <template #default><el-tag size="small" type="warning" effect="plain">本批次新登记</el-tag></template>
              </el-table-column>
              <el-table-column label="状态" width="90">
                <template #default="{ row }">
                  <el-tag :type="ASSISTANCE_STATUS[row.status]?.type" size="small">{{ ASSISTANCE_STATUS[row.status]?.label }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="登记" width="140">
                <template #default="{ row }">
                  <div style="font-size: 12px">{{ row.createdByName || '—' }}</div>
                  <div style="font-size: 12px; color: #8a94a8">{{ fmtTime(row.createdAt) }}</div>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="row.status === 'ACTIVE' && canAssist" size="small" link type="success"
                    @click="openReview(row, 'COMPLETE')">办结</el-button>
                  <el-button size="small" link @click="openAssistDetail(row)">详情</el-button>
                </template>
              </el-table-column>
              <template #empty>本批次暂无新登记需求</template>
            </el-table>
            <template v-if="batch.carried.length > 0">
              <div class="carried-title">续办关怀（历史批次明确续办，转入本批次人力安排）</div>
              <el-table :data="batch.carried" size="small">
                <el-table-column prop="residentName" label="老人/住户" width="100" />
                <el-table-column prop="roomNo" label="房号" width="100" />
                <el-table-column prop="needDescription" label="上下楼需求" min-width="200" show-overflow-tooltip />
                <el-table-column label="临时帮扶人员（人力安排）" width="150">
                  <template #default="{ row }">{{ row.helperName }}<br />
                    <span style="font-size: 12px; color: #8a94a8">{{ row.helperPhone }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="批次归属" width="130">
                  <template #default="{ row }">
                    <el-tag size="small" type="primary" effect="plain">续办自整改单 #{{ row.rectificationPlan?.id }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag :type="ASSISTANCE_STATUS[row.status]?.type" size="small">{{ ASSISTANCE_STATUS[row.status]?.label }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="上次复核" width="140">
                  <template #default="{ row }">
                    <div style="font-size: 12px">{{ row.reviewedBy || '—' }}</div>
                    <div style="font-size: 12px; color: #8a94a8">{{ row.reviewedAt ? fmtTime(row.reviewedAt) : '' }}</div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="150" fixed="right">
                  <template #default="{ row }">
                    <el-button v-if="canAssist" size="small" link type="success" @click="openReview(row, 'COMPLETE')">结案</el-button>
                    <el-button v-if="canAssist" size="small" link type="warning" @click="openReview(row, 'RESCHEDULE')">改约</el-button>
                    <el-button size="small" link @click="openAssistDetail(row)">详情</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </template>
          </div>
        </el-tab-pane>

        <!-- 待复核：电梯已恢复，等待管家逐条确认 -->
        <el-tab-pane name="pending">
          <template #label>
            待复核（电梯已恢复）
            <el-badge v-if="pendingReview.length" :value="pendingReview.length" type="danger" style="margin-left: 6px" />
          </template>
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 10px"
            title="以下帮扶需求登记于已恢复运行的停梯批次，不再计入「停梯期间帮扶中」；请管家逐条确认：已完成、继续关怀（转入续办）或改约" />
          <el-table :data="pendingReview" size="small" v-loading="loading">
            <el-table-column prop="residentName" label="老人/住户" width="100" />
            <el-table-column prop="roomNo" label="房号" width="100" />
            <el-table-column prop="needDescription" label="上下楼需求" min-width="180" show-overflow-tooltip />
            <el-table-column label="临时帮扶人员" width="130">
              <template #default="{ row }">{{ row.helperName }}<br />
                <span style="font-size: 12px; color: #8a94a8">{{ row.helperPhone }}</span>
              </template>
            </el-table-column>
            <el-table-column label="原停梯批次" width="130">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openPlanDetail(row.rectificationPlan?.id)">
                  整改单 #{{ row.rectificationPlan?.id }}
                </el-button>
                <div style="font-size: 12px; color: #8a94a8" class="mono">{{ row.elevator?.code }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="ASSISTANCE_STATUS[row.status]?.type" size="small">{{ ASSISTANCE_STATUS[row.status]?.label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="登记" width="140">
              <template #default="{ row }">
                <div style="font-size: 12px">{{ row.createdByName || '—' }}</div>
                <div style="font-size: 12px; color: #8a94a8">{{ fmtTime(row.createdAt) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" fixed="right">
              <template #default="{ row }">
                <template v-if="canAssist">
                  <el-button size="small" link type="success" @click="openReview(row, 'COMPLETE')">完成</el-button>
                  <el-button size="small" link type="primary" @click="openReview(row, 'CONTINUE')">续办</el-button>
                  <el-button size="small" link type="warning" @click="openReview(row, 'RESCHEDULE')">改约</el-button>
                </template>
                <el-button size="small" link @click="openAssistDetail(row)">详情</el-button>
              </template>
            </el-table-column>
            <template #empty>暂无待复核记录</template>
          </el-table>
        </el-tab-pane>

        <!-- 续办关怀：恢复后仍持续关怀，下次停梯自动纳入新批次 -->
        <el-tab-pane name="continued">
          <template #label>
            续办关怀
            <el-badge v-if="continuedList.length" :value="continuedList.length" type="primary" style="margin-left: 6px" />
          </template>
          <el-alert type="info" :closable="false" show-icon style="margin-bottom: 10px"
            title="电梯恢复后仍需持续关怀的需求；同一电梯下次停梯时，自动纳入新整改单批次的帮扶汇总与人力安排" />
          <el-table :data="continuedList" size="small" v-loading="loading">
            <el-table-column prop="residentName" label="老人/住户" width="100" />
            <el-table-column prop="roomNo" label="房号" width="100" />
            <el-table-column prop="needDescription" label="上下楼需求" min-width="180" show-overflow-tooltip />
            <el-table-column label="临时帮扶人员" width="130">
              <template #default="{ row }">{{ row.helperName }}<br />
                <span style="font-size: 12px; color: #8a94a8">{{ row.helperPhone }}</span>
              </template>
            </el-table-column>
            <el-table-column label="原停梯批次" width="130">
              <template #default="{ row }">
                <el-button size="small" link type="primary" @click="openPlanDetail(row.rectificationPlan?.id)">
                  整改单 #{{ row.rectificationPlan?.id }}
                </el-button>
                <div style="font-size: 12px; color: #8a94a8" class="mono">{{ row.elevator?.code }}</div>
              </template>
            </el-table-column>
            <el-table-column label="续办确认" width="150">
              <template #default="{ row }">
                <div style="font-size: 12px">{{ row.reviewedBy || '—' }}</div>
                <div style="font-size: 12px; color: #8a94a8">{{ row.reviewedAt ? fmtTime(row.reviewedAt) : '' }}</div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="ASSISTANCE_STATUS[row.status]?.type" size="small">{{ ASSISTANCE_STATUS[row.status]?.label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <template v-if="canAssist">
                  <el-button size="small" link type="success" @click="openReview(row, 'COMPLETE')">结案</el-button>
                  <el-button size="small" link type="warning" @click="openReview(row, 'RESCHEDULE')">改约</el-button>
                </template>
                <el-button size="small" link @click="openAssistDetail(row)">详情</el-button>
              </template>
            </el-table-column>
            <template #empty>暂无续办关怀记录</template>
          </el-table>
        </el-tab-pane>

        <!-- 全部记录：含历史批次已结案，可按状态过滤 -->
        <el-tab-pane label="全部记录" name="all">
          <div style="margin-bottom: 10px; display: flex; align-items: center; gap: 10px">
            <span style="font-size: 13px; color: #5a6478">状态筛选</span>
            <el-select v-model="assistStatusFilter" clearable placeholder="全部状态" size="small" style="width: 150px"
              @change="loadAssistAll">
              <el-option v-for="(v, k) in ASSISTANCE_STATUS" :key="k" :label="v.label" :value="k" />
            </el-select>
          </div>
          <el-table :data="assistances" size="small" v-loading="loading">
            <el-table-column prop="residentName" label="老人/住户" width="100" />
            <el-table-column prop="roomNo" label="房号" width="100" />
            <el-table-column label="楼栋" width="130">
              <template #default="{ row }">{{ row.building?.name }}</template>
            </el-table-column>
            <el-table-column label="关联停梯" width="90">
              <template #default="{ row }"><span class="mono">{{ row.elevator?.code || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="停梯批次" width="110">
              <template #default="{ row }">
                <el-button v-if="row.rectificationPlan" size="small" link type="primary"
                  @click="openPlanDetail(row.rectificationPlan.id)">整改单 #{{ row.rectificationPlan.id }}</el-button>
                <span v-else>—</span>
              </template>
            </el-table-column>
            <el-table-column prop="needDescription" label="上下楼需求" min-width="170" show-overflow-tooltip />
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="ASSISTANCE_STATUS[row.status]?.type" size="small">{{ ASSISTANCE_STATUS[row.status]?.label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="复核处理" width="160">
              <template #default="{ row }">
                <template v-if="row.reviewedBy">
                  <div style="font-size: 12px">{{ REVIEW_ACTION[row.reviewAction]?.label }} ｜ {{ row.reviewedBy }}</div>
                  <div style="font-size: 12px; color: #8a94a8">{{ fmtTime(row.reviewedAt) }}</div>
                </template>
                <span v-else style="color: #8a94a8">—</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" fixed="right">
              <template #default="{ row }">
                <el-button size="small" link @click="openAssistDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
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
    <el-dialog v-model="requestDialog" :title="`发起停梯整改申请：${requestElevator?.code || ''}`" width="520px">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 12px"
        title="系统将在事务内重新核验该电梯一周内困人事件是否达到阈值，核验通过后固化触发事件、故障代码与投诉汇总快照；电梯保持停用并自动发布停梯公告，复检通过后方可恢复运行" />
      <el-form label-width="90px">
        <el-form-item label="整改要求">
          <el-input v-model="requestNote" type="textarea" :rows="3"
            placeholder="如：一周内 3 次困人，要求提交彻底整改方案，写明配件、到货时间、复检人" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="requestDialog = false">取消</el-button>
        <el-button type="danger" :loading="acting" @click="doRequest">确认发起整改</el-button>
      </template>
    </el-dialog>

    <!-- 提交方案对话框（每次提交生成新版本，历史版本不可覆盖） -->
    <el-dialog v-model="submitDialog"
      :title="`提交整改方案（第 ${(submitView?.plan?.currentVersionNo || 0) + 1} 版）：${submitView?.plan?.elevator?.code || ''}`" width="560px">
      <el-alert v-if="submitView?.plan?.status === 'RECHECK_FAILED'" type="error" :closable="false" show-icon
        style="margin-bottom: 12px"
        title="上一版复检未通过，本次提交将生成可追溯的新版本，历史失败版本及其结论永久保留" />
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
        <el-button type="primary" :loading="acting" @click="doSubmit">提交新版本</el-button>
      </template>
    </el-dialog>

    <!-- 复检对话框（仅最新版本可登记，登记后结论不可更改） -->
    <el-dialog v-model="recheckDialog"
      :title="`复检登记（第 ${recheckTarget?.version?.versionNo} 版）：${recheckTarget?.plan?.elevator?.code || ''}`" width="520px">
      <el-descriptions :column="1" size="small" border style="margin-bottom: 12px">
        <el-descriptions-item label="配件">{{ recheckTarget?.version?.parts }}</el-descriptions-item>
        <el-descriptions-item label="复检人">{{ recheckTarget?.version?.recheckInspector }}</el-descriptions-item>
        <el-descriptions-item label="提交">{{ recheckTarget?.version?.submittedBy }} ｜ {{ fmtTime(recheckTarget?.version?.submittedAt) }}</el-descriptions-item>
      </el-descriptions>
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="复检结论出具后不可更改；仅最新版本复检通过才会恢复电梯运行并发布复检公告；通过时本批次未办结帮扶将自动转入待复核" />
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

    <!-- 整改申请详情：触发证据快照 + 版本历史 + 不可变复检记录 + 公告 + 帮扶记录 -->
    <el-dialog v-model="historyDialog" :title="`整改申请详情：${detail?.plan?.elevator?.code || ''}`" width="880px">
      <template v-if="detail">
        <el-descriptions :column="3" size="small" border style="margin-bottom: 6px">
          <el-descriptions-item label="申请状态">
            <el-tag :type="PLAN_STATUS[detail.plan.status]?.type" size="small">{{ PLAN_STATUS[detail.plan.status]?.label }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="触发核验">
            {{ detail.plan.triggerEventCount }} 起 / 阈值 {{ detail.plan.triggerThreshold }} 起
          </el-descriptions-item>
          <el-descriptions-item label="当前版本">第 {{ detail.plan.currentVersionNo }} 版</el-descriptions-item>
          <el-descriptions-item label="证据窗口" :span="2">
            {{ fmtTime(detail.plan.evidenceWindowStart, 'MM-DD HH:mm') }} ~ {{ fmtTime(detail.plan.evidenceWindowEnd, 'MM-DD HH:mm') }}
          </el-descriptions-item>
          <el-descriptions-item label="申请">{{ detail.plan.requestedBy }} ｜ {{ fmtTime(detail.plan.requestedAt, 'MM-DD HH:mm') }}</el-descriptions-item>
          <el-descriptions-item label="故障代码快照" :span="3">
            <el-tag v-for="c in snapshotFaultCodes" :key="c" size="small" effect="plain" class="flag-tag">{{ c }}</el-tag>
            <span v-if="snapshotFaultCodes.length === 0">—</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="detail.plan.requestNote" label="整改要求" :span="3">{{ detail.plan.requestNote }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left" style="margin: 12px 0">触发事件快照（申请时固化，不可变）</el-divider>
        <el-table :data="snapshotEvents" size="small" max-height="160">
          <el-table-column prop="eventNo" label="事件编号" width="150">
            <template #default="{ row }"><span class="mono">{{ row.eventNo }}</span></template>
          </el-table-column>
          <el-table-column label="报警时间" width="150">
            <template #default="{ row }">{{ fmtTime(row.alarmTime) }}</template>
          </el-table-column>
          <el-table-column prop="faultCode" label="故障代码" min-width="160">
            <template #default="{ row }">{{ row.faultCode || '—' }}</template>
          </el-table-column>
          <el-table-column label="事件状态" width="90">
            <template #default="{ row }">
              <el-tag :type="EVENT_STATUS[row.status]?.type" size="small">{{ EVENT_STATUS[row.status]?.label || row.status }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left" style="margin: 12px 0">
          投诉汇总快照（{{ snapshotComplaints.count || 0 }} 条，申请时固化）
        </el-divider>
        <el-table :data="snapshotComplaints.items || []" size="small" max-height="140">
          <el-table-column prop="ownerName" label="投诉人" width="90" />
          <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
          <el-table-column label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="COMPLAINT_STATUS[row.status]?.type" size="small">{{ COMPLAINT_STATUS[row.status]?.label || row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="投诉时间" width="140">
            <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left" style="margin: 12px 0">
          方案版本历史（共 {{ detail.versions.length }} 版，历史版本与复检记录不可更改）
        </el-divider>
        <el-timeline style="padding-left: 4px">
          <el-timeline-item v-for="v in versionsDesc" :key="v.id"
            :type="v.status === 'RECHECK_PASSED' ? 'success' : v.status === 'RECHECK_FAILED' ? 'danger' : 'primary'"
            :timestamp="fmtTime(v.submittedAt)" placement="top">
            <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 6px">
              <b>第 {{ v.versionNo }} 版</b>
              <el-tag size="small" :type="VERSION_STATUS[v.status]?.type">{{ VERSION_STATUS[v.status]?.label }}</el-tag>
              <el-tag v-if="v.versionNo === detail.plan.currentVersionNo" size="small" effect="plain">最新版本</el-tag>
            </div>
            <el-descriptions :column="2" size="small" border>
              <el-descriptions-item label="配件" :span="2">{{ v.parts }}</el-descriptions-item>
              <el-descriptions-item label="预计到货">{{ v.expectedArrival }}</el-descriptions-item>
              <el-descriptions-item label="复检人">{{ v.recheckInspector }}</el-descriptions-item>
              <el-descriptions-item label="公告发布时间">{{ fmtTime(v.noticePublishTime) }}</el-descriptions-item>
              <el-descriptions-item label="提交人">{{ v.submittedBy }} ｜ {{ fmtTime(v.submittedAt, 'MM-DD HH:mm') }}</el-descriptions-item>
              <el-descriptions-item v-if="v.planDetail" label="方案详情" :span="2">{{ v.planDetail }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="v.recheckedAt" style="margin-top: 6px">
              <el-alert :type="v.status === 'RECHECK_PASSED' ? 'success' : 'error'" :closable="false" show-icon>
                <template #title>
                  复检{{ v.status === 'RECHECK_PASSED' ? '通过' : '未通过' }}：{{ v.recheckResult }}
                  （复检登记：{{ v.recheckedBy }} ｜ {{ fmtTime(v.recheckedAt) }}）
                  <span v-if="recordOf(v)" style="color: #8a94a8">不可变复检记录 #{{ recordOf(v).id }}</span>
                </template>
              </el-alert>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-if="detail.versions.length === 0" description="尚未提交方案版本" :image-size="60" />

        <el-divider content-position="left" style="margin: 12px 0">
          公告联动（围绕本整改单，{{ detail.notices.length }} 条；已撤回公告保留可审计）
        </el-divider>
        <el-table :data="detail.notices" size="small">
          <el-table-column label="类型" width="90">
            <template #default="{ row }">
              <el-tag :type="NOTICE_TYPE[row.type]?.type" size="small">{{ NOTICE_TYPE[row.type]?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="190" show-overflow-tooltip />
          <el-table-column label="状态" width="76">
            <template #default="{ row }">
              <el-tag :type="row.status === 'PUBLISHED' ? 'success' : 'info'" size="small">
                {{ row.status === 'PUBLISHED' ? '有效' : '已撤回' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布时间" width="135">
            <template #default="{ row }">{{ fmtTime(row.publishedAt) }}</template>
          </el-table-column>
          <el-table-column label="撤回时间 / 原因" min-width="190">
            <template #default="{ row }">
              <span v-if="row.revokedAt" style="font-size: 12px">{{ fmtTime(row.revokedAt) }}<br />{{ row.revokeReason }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="detail.notices.length === 0" description="暂无关联公告" :image-size="60" />

        <el-divider content-position="left" style="margin: 12px 0">
          帮扶记录（本批次登记 {{ detail.assistances?.length || 0 }} 条，续办转入 {{ detail.carriedAssistances?.length || 0 }} 条）
        </el-divider>
        <el-table :data="detail.assistances || []" size="small" max-height="200">
          <el-table-column prop="residentName" label="老人/住户" width="90" />
          <el-table-column prop="roomNo" label="房号" width="90" />
          <el-table-column prop="needDescription" label="上下楼需求" min-width="180" show-overflow-tooltip />
          <el-table-column prop="helperName" label="帮扶人员" width="110" />
          <el-table-column label="状态" width="86">
            <template #default="{ row }">
              <el-tag :type="ASSISTANCE_STATUS[row.status]?.type" size="small">{{ ASSISTANCE_STATUS[row.status]?.label }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="复核处理" width="150">
            <template #default="{ row }">
              <template v-if="row.reviewedBy">
                <div style="font-size: 12px">{{ REVIEW_ACTION[row.reviewAction]?.label }} ｜ {{ row.reviewedBy }}</div>
                <div style="font-size: 12px; color: #8a94a8">{{ fmtTime(row.reviewedAt) }}</div>
              </template>
              <span v-else style="color: #8a94a8">—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70">
            <template #default="{ row }">
              <el-button size="small" link @click="openAssistDetail(row)">详情</el-button>
            </template>
          </el-table-column>
          <template #empty>本批次未登记帮扶需求</template>
        </el-table>
        <template v-if="(detail.carriedAssistances || []).length > 0">
          <div class="carried-title">续办转入（历史批次明确续办，纳入本批次人力安排）</div>
          <el-table :data="detail.carriedAssistances" size="small" max-height="160">
            <el-table-column prop="residentName" label="老人/住户" width="90" />
            <el-table-column prop="roomNo" label="房号" width="90" />
            <el-table-column prop="needDescription" label="上下楼需求" min-width="180" show-overflow-tooltip />
            <el-table-column label="原批次" width="110">
              <template #default="{ row }">整改单 #{{ row.rectificationPlan?.id }}</template>
            </el-table-column>
            <el-table-column label="状态" width="86">
              <template #default="{ row }">
                <el-tag :type="ASSISTANCE_STATUS[row.status]?.type" size="small">{{ ASSISTANCE_STATUS[row.status]?.label }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="70">
              <template #default="{ row }">
                <el-button size="small" link @click="openAssistDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </el-dialog>

    <!-- 帮扶登记对话框（必须关联当前停梯整改批次） -->
    <el-dialog v-model="assistDialog" title="登记老人上下楼帮扶" width="560px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px"
        title="帮扶需求将归入所选电梯当前进行中的停梯整改单（批次）；电梯恢复运行后，未办结需求自动转入待复核" />
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
            <el-form-item label="关联停梯" required>
              <el-select v-model="assistForm.elevatorId" style="width: 100%" placeholder="选择停梯整改中的电梯">
                <el-option v-for="e in stoppedElevatorsOf(assistForm.buildingId)" :key="e.id"
                  :label="`${e.code}（整改单 #${activePlanOf(e.id)?.id}）`" :value="e.id" />
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

    <!-- 帮扶复核对话框（完成 / 续办 / 改约，处理人与时间自动留痕） -->
    <el-dialog v-model="reviewDialog" :title="`帮扶复核：${reviewTarget?.residentName || ''}`" width="520px">
      <template v-if="reviewTarget">
        <el-descriptions :column="2" size="small" border style="margin-bottom: 12px">
          <el-descriptions-item label="老人/住户">{{ reviewTarget.residentName }}（{{ reviewTarget.roomNo || '—' }}）</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="ASSISTANCE_STATUS[reviewTarget.status]?.type" size="small">
              {{ ASSISTANCE_STATUS[reviewTarget.status]?.label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="上下楼需求" :span="2">{{ reviewTarget.needDescription }}</el-descriptions-item>
          <el-descriptions-item label="所属批次" :span="2">
            整改单 #{{ reviewTarget.rectificationPlan?.id }}（{{ reviewTarget.elevator?.code }}）
          </el-descriptions-item>
        </el-descriptions>
        <el-form label-width="100px">
          <el-form-item label="处理方式" required>
            <el-radio-group v-model="reviewForm.action">
              <el-radio-button v-for="a in allowedReviewActions" :key="a.value" :value="a.value">{{ a.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="reviewForm.action === 'RESCHEDULE'" label="下次服务时间" required>
            <el-date-picker v-model="reviewForm.nextAppointmentAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%" placeholder="选择改约后的服务时间" />
          </el-form-item>
          <el-form-item label="处理备注">
            <el-input v-model="reviewForm.note" type="textarea" :rows="2"
              :placeholder="reviewForm.action === 'CONTINUE' ? '如：透析为长期需求，电梯恢复后仍需持续关怀' : '处理情况说明（可选）'" />
          </el-form-item>
        </el-form>
        <el-alert v-if="reviewForm.action === 'CONTINUE'" type="info" :closable="false" show-icon
          title="转为续办关怀后，同一电梯下次停梯时将自动纳入新整改单批次的帮扶汇总与人力安排" />
        <el-alert v-else type="success" :closable="false" show-icon
          title="处理人与处理时间将自动留痕，可在帮扶详情中追溯" />
      </template>
      <template #footer>
        <el-button @click="reviewDialog = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doReview">确认复核</el-button>
      </template>
    </el-dialog>

    <!-- 帮扶详情对话框：处理留痕时间线 + 批次追溯 -->
    <el-dialog v-model="assistDetailDialog" :title="`帮扶记录详情：${assistDetail?.assistance?.residentName || ''}`" width="640px">
      <template v-if="assistDetail">
        <el-descriptions :column="2" size="small" border style="margin-bottom: 8px">
          <el-descriptions-item label="老人/住户">{{ assistDetail.assistance.residentName }}</el-descriptions-item>
          <el-descriptions-item label="房号">{{ assistDetail.assistance.roomNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ assistDetail.assistance.phone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="楼栋">{{ assistDetail.assistance.building?.name }}</el-descriptions-item>
          <el-descriptions-item label="关联停梯"><span class="mono">{{ assistDetail.assistance.elevator?.code || '—' }}</span></el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="ASSISTANCE_STATUS[assistDetail.assistance.status]?.type" size="small">
              {{ ASSISTANCE_STATUS[assistDetail.assistance.status]?.label }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="上下楼需求" :span="2">{{ assistDetail.assistance.needDescription }}</el-descriptions-item>
          <el-descriptions-item label="帮扶人员">{{ assistDetail.assistance.helperName }}（{{ assistDetail.assistance.helperPhone || '—' }}）</el-descriptions-item>
          <el-descriptions-item label="发起批次">
            <el-button v-if="assistDetail.assistance.rectificationPlan" size="small" link type="primary"
              @click="openPlanDetail(assistDetail.assistance.rectificationPlan.id)">
              整改单 #{{ assistDetail.assistance.rectificationPlan.id }}
            </el-button>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="登记">{{ assistDetail.assistance.createdByName || '—' }} ｜ {{ fmtTime(assistDetail.assistance.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="复核处理">
            <span v-if="assistDetail.assistance.reviewedBy">
              {{ REVIEW_ACTION[assistDetail.assistance.reviewAction]?.label }} ｜ {{ assistDetail.assistance.reviewedBy }} ｜ {{ fmtTime(assistDetail.assistance.reviewedAt) }}
            </span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item v-if="assistDetail.assistance.reviewNote" label="处理备注" :span="2">{{ assistDetail.assistance.reviewNote }}</el-descriptions-item>
          <el-descriptions-item v-if="assistDetail.assistance.nextAppointmentAt" label="下次服务时间" :span="2">
            {{ fmtTime(assistDetail.assistance.nextAppointmentAt) }}
          </el-descriptions-item>
          <el-descriptions-item v-if="assistDetail.batchLinks?.length" label="纳入后续批次" :span="2">
            <el-tag v-for="l in assistDetail.batchLinks" :key="l.id" size="small" effect="plain" style="margin-right: 6px">
              整改单 #{{ l.rectificationPlanId }}（{{ fmtTime(l.linkedAt, 'MM-DD HH:mm') }} 纳入）
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left" style="margin: 10px 0">处理留痕（只增不改）</el-divider>
        <el-timeline style="padding-left: 4px">
          <el-timeline-item v-for="ev in assistDetail.events" :key="ev.id" :timestamp="fmtTime(ev.createdAt)" placement="top"
            :type="ev.action === 'AUTO_PENDING_REVIEW' ? 'warning' : ev.action === 'COMPLETE' ? 'success' : 'primary'">
            <b>{{ ASSISTANCE_EVENT_ACTION[ev.action] || ev.action }}</b>
            <span style="font-size: 12px; color: #5a6478">｜处理人：{{ ev.actorName || '系统' }}</span>
            <div v-if="ev.note" style="font-size: 12px; color: #5a6478">{{ ev.note }}</div>
          </el-timeline-item>
        </el-timeline>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'
import { useAuthStore } from '../store/auth'
import {
  EVENT_STATUS, COMPLAINT_STATUS, NOTICE_TYPE,
  ASSISTANCE_STATUS, REVIEW_ACTION, ASSISTANCE_EVENT_ACTION
} from '../utils/dict'
import { fmtTime } from '../utils/format'

const PLAN_STATUS = {
  REQUESTED: { label: '待维保提交', type: 'warning' },
  SUBMITTED: { label: '待复检', type: 'primary' },
  RECHECK_PASSED: { label: '复检通过', type: 'success' },
  RECHECK_FAILED: { label: '复检未通过', type: 'danger' }
}

const VERSION_STATUS = {
  SUBMITTED: { label: '待复检', type: 'primary' },
  RECHECK_PASSED: { label: '复检通过', type: 'success' },
  RECHECK_FAILED: { label: '复检未通过', type: 'danger' }
}

const auth = useAuthStore()
const loading = ref(false)
const acting = ref(false)
const repeatFaults = ref([])
const plans = ref([])
const buildings = ref([])
const elevators = ref([])

// 帮扶（按批次）
const assistTab = ref('current')
const currentBatches = ref([])
const pendingReview = ref([])
const continuedList = ref([])
const assistances = ref([])
const assistStatusFilter = ref('')

const summaryDialog = ref(false)
const requestDialog = ref(false)
const submitDialog = ref(false)
const recheckDialog = ref(false)
const assistDialog = ref(false)
const historyDialog = ref(false)
const reviewDialog = ref(false)
const assistDetailDialog = ref(false)

const summary = ref({})
const requestElevator = ref(null)
const requestNote = ref('')
const submitView = ref(null)
const recheckTarget = ref(null)
const detail = ref(null)
const reviewTarget = ref(null)
const assistDetail = ref(null)

const submitForm = reactive({ parts: '', expectedArrival: '', recheckInspector: '', noticePublishTime: '', planDetail: '' })
const recheckForm = reactive({ pass: true, result: '' })
const assistForm = reactive({ buildingId: null, elevatorId: null, residentName: '', roomNo: '', phone: '', needDescription: '', helperName: '', helperPhone: '' })
const reviewForm = reactive({ action: 'COMPLETE', note: '', nextAppointmentAt: '' })

const canRequest = computed(() => ['ADMIN', 'DUTY'].includes(auth.user?.role))
const canSubmit = computed(() => ['ADMIN', 'DUTY', 'MAINTENANCE'].includes(auth.user?.role))
const canRecheck = computed(() => ['ADMIN', 'DUTY', 'MAINTENANCE'].includes(auth.user?.role))
const canAssist = computed(() => ['ADMIN', 'DUTY', 'BUTLER'].includes(auth.user?.role))

const snapshotEvents = computed(() => parseJson(detail.value?.plan?.triggerEventsJson, []))
const snapshotFaultCodes = computed(() => parseJson(detail.value?.plan?.faultCodesJson, []))
const snapshotComplaints = computed(() => parseJson(detail.value?.plan?.complaintSummaryJson, { count: 0, items: [] }))
const versionsDesc = computed(() => [...(detail.value?.versions || [])].sort((a, b) => b.versionNo - a.versionNo))

const currentCount = computed(() =>
  currentBatches.value.reduce((sum, b) => sum + (b.newBatch?.length || 0) + (b.carried?.length || 0), 0))

const REVIEW_ACTION_OPTIONS = {
  COMPLETE: { value: 'COMPLETE', label: '确认完成' },
  CONTINUE: { value: 'CONTINUE', label: '继续关怀（转续办）' },
  RESCHEDULE: { value: 'RESCHEDULE', label: '改约' }
}

// 各状态允许的复核动作：帮扶中→办结；待复核→完成/续办/改约；续办中→结案/改约
const allowedReviewActions = computed(() => {
  const status = reviewTarget.value?.status
  if (status === 'ACTIVE') return [REVIEW_ACTION_OPTIONS.COMPLETE]
  if (status === 'PENDING_REVIEW') return Object.values(REVIEW_ACTION_OPTIONS)
  if (status === 'CONTINUED') return [REVIEW_ACTION_OPTIONS.COMPLETE, REVIEW_ACTION_OPTIONS.RESCHEDULE]
  return []
})

function parseJson(text, fallback) {
  if (!text) return fallback
  try { return JSON.parse(text) } catch { return fallback }
}

function recordOf(version) {
  return (detail.value?.recheckRecords || []).find((r) => r.version?.id === version.id)
}

function fmtMinutes(minutes) {
  if (minutes == null) return '—'
  if (minutes < 60) return `${minutes} 分钟`
  const h = Math.floor(minutes / 60)
  if (h < 24) return `${h} 小时 ${minutes % 60} 分`
  return `${Math.floor(h / 24)} 天 ${h % 24} 小时`
}

function activePlanOf(elevatorId) {
  return plans.value.find((p) => p.plan?.elevator?.id === elevatorId && p.plan?.status !== 'RECHECK_PASSED')?.plan
}

function stoppedElevatorsOf(buildingId) {
  return elevators.value.filter((e) => e.building?.id === buildingId && e.status === 'STOPPED' && activePlanOf(e.id))
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
  const [cur, pending, continued] = await Promise.all([
    api.get('/assistances/current-batches'),
    api.get('/assistances', { params: { status: 'PENDING_REVIEW' } }),
    api.get('/assistances', { params: { status: 'CONTINUED' } })
  ])
  currentBatches.value = cur.data
  pendingReview.value = pending.data
  continuedList.value = continued.data
  await loadAssistAll()
}

async function loadAssistAll() {
  const res = await api.get('/assistances', {
    params: assistStatusFilter.value ? { status: assistStatusFilter.value } : {}
  })
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
    ElMessage.success('整改申请已创建：证据快照已固化，电梯保持停用并已发布停梯公告；续办关怀需求已自动纳入本批次')
    requestDialog.value = false
    loadAll()
  } finally {
    acting.value = false
  }
}

function openSubmit(view) {
  submitView.value = view
  const latest = view.latestVersion || {}
  Object.assign(submitForm, {
    parts: latest.parts || '', expectedArrival: latest.expectedArrival || '',
    recheckInspector: latest.recheckInspector || '', noticePublishTime: '', planDetail: latest.planDetail || ''
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
    await api.post(`/rectification-plans/${submitView.value.plan.id}/versions`, submitForm)
    ElMessage.success(`第 ${(submitView.value.plan.currentVersionNo || 0) + 1} 版整改方案已提交，等待复检`)
    submitDialog.value = false
    loadAll()
  } finally {
    acting.value = false
  }
}

function openRecheck(view) {
  recheckTarget.value = { plan: view.plan, version: view.latestVersion }
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
    await api.post(`/rectification-versions/${recheckTarget.value.version.id}/recheck`, recheckForm)
    ElMessage.success(recheckForm.pass
      ? '复检通过，电梯已恢复运行并发布复检公告；本批次未办结帮扶已转入待复核'
      : '已登记复检未通过（结论不可更改），电梯保持停梯，帮扶与停梯公告保持有效')
    recheckDialog.value = false
    loadAll()
  } finally {
    acting.value = false
  }
}

async function openPlanDetail(planId) {
  if (!planId) return
  const res = await api.get(`/rectification-plans/${planId}`)
  detail.value = res.data
  historyDialog.value = true
}

function openAssist() {
  Object.assign(assistForm, { buildingId: null, elevatorId: null, residentName: '', roomNo: '', phone: '', needDescription: '', helperName: '', helperPhone: '' })
  assistDialog.value = true
}

async function doAssist() {
  if (!assistForm.buildingId || !assistForm.elevatorId || !assistForm.residentName || !assistForm.needDescription || !assistForm.helperName) {
    ElMessage.warning('请完整填写楼栋、关联停梯、住户、需求与帮扶人员')
    return
  }
  acting.value = true
  try {
    await api.post('/assistances', assistForm)
    ElMessage.success('帮扶需求已登记并归入当前停梯整改批次')
    assistDialog.value = false
    loadAssistances()
  } finally {
    acting.value = false
  }
}

function openReview(row, action) {
  reviewTarget.value = row
  reviewForm.action = action
  reviewForm.note = ''
  reviewForm.nextAppointmentAt = ''
  reviewDialog.value = true
}

async function doReview() {
  if (reviewForm.action === 'RESCHEDULE' && !reviewForm.nextAppointmentAt) {
    ElMessage.warning('改约必须填写下次服务时间')
    return
  }
  acting.value = true
  try {
    await api.post(`/assistances/${reviewTarget.value.id}/review`, {
      action: reviewForm.action,
      note: reviewForm.note || null,
      nextAppointmentAt: reviewForm.action === 'RESCHEDULE' ? reviewForm.nextAppointmentAt : null
    })
    ElMessage.success('复核已登记，处理人与时间已留痕')
    reviewDialog.value = false
    loadAssistances()
  } finally {
    acting.value = false
  }
}

async function openAssistDetail(row) {
  const res = await api.get(`/assistances/${row.id}`)
  assistDetail.value = res.data
  assistDetailDialog.value = true
}

onMounted(loadAll)
</script>

<style scoped>
.batch-block {
  border: 1px solid #e4e9f2;
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 14px;
}

.batch-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.batch-meta {
  font-size: 12px;
  color: #5a6478;
}

.carried-title {
  font-size: 12px;
  color: #5a6478;
  margin: 10px 0 6px;
  padding-left: 8px;
  border-left: 3px solid #7c9cf5;
}
</style>
