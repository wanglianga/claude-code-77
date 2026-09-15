// 业务字典：枚举值 → 中文标签 / 标签类型
export const EVENT_STATUS = {
  PENDING: { label: '待调度', type: 'danger', step: 0 },
  DISPATCHED: { label: '已调度', type: 'warning', step: 1 },
  ARRIVED: { label: '已到场', type: 'warning', step: 2 },
  RELEASED: { label: '已救出', type: 'success', step: 3 },
  RESET: { label: '已复位', type: 'success', step: 4 },
  CLOSED: { label: '已关闭', type: 'info', step: 5 }
}

export const ALARM_SOURCE = {
  IOT: '物联网报警',
  PHONE: '电话求助',
  PATROL: '巡查发现'
}

export const CALL_STATUS = {
  SMOOTH: { label: '通话顺畅', type: 'success' },
  INTERMITTENT: { label: '时断时续', type: 'warning' },
  LOST: { label: '无法接通', type: 'danger' }
}

export const ELEVATOR_STATUS = {
  RUNNING: { label: '运行中', type: 'success' },
  STOPPED: { label: '停梯', type: 'danger' },
  MAINTENANCE: { label: '维保中', type: 'warning' }
}

export const RESPONSIBILITY = {
  MAINTENANCE: '维保单位责任',
  PROPERTY: '物业管理责任',
  OWNER_MISUSE: '业主使用不当',
  EQUIPMENT_AGING: '设备老化',
  JOINT: '共同责任',
  PENDING: '待定'
}

export const COST_BEARER = {
  MAINTENANCE: '维保单位承担',
  PROPERTY: '物业承担',
  OWNER: '业主承担',
  SHARED: '共同分担',
  NONE: '无费用'
}

export const NOTIFY_STATUS = {
  SENT: { label: '已通知', type: 'warning' },
  ACKED: { label: '已确认', type: 'success' },
  UNREACHABLE: { label: '联系不上', type: 'danger' }
}

export const ROLE_LABEL = {
  ADMIN: '系统管理员',
  DUTY: '物业值班员',
  MAINTENANCE: '维保人员',
  SECURITY: '保安',
  BUTLER: '楼栋管家',
  FIRE: '消防救援',
  OWNER: '业主'
}

export const COMPLAINT_STATUS = {
  PENDING: { label: '待处理', type: 'danger' },
  PROCESSING: { label: '处理中', type: 'warning' },
  RESOLVED: { label: '已办结', type: 'success' }
}

export const NOTICE_TYPE = {
  STOP_NOTICE: { label: '停梯公告', type: 'danger' },
  BACKUP_LIFT: { label: '备用梯开放', type: 'warning' },
  ELDERLY_ASSIST: { label: '老人临时协助', type: 'success' },
  RECHECK: { label: '复检通知', type: 'primary' },
  GENERAL: { label: '一般通知', type: 'info' }
}

export const INSPECTION_RESULT = {
  PASS: { label: '合格', type: 'success' },
  RECTIFY: { label: '整改后合格', type: 'warning' },
  FAIL: { label: '不合格', type: 'danger' }
}

export const SHIFT_LABEL = {
  DAY: '白班',
  NIGHT: '夜班'
}

export const DOOR_OPEN_METHODS = [
  '松闸盘车平层开门',
  '检修运行至平层开门',
  '消防协助开门',
  '自动救援装置（ARD）平层开门',
  '其他方式'
]

export const EVENT_FLAGS = [
  { key: 'maintenanceLate', label: '维保迟到' },
  { key: 'propertyUnreachable', label: '物业联系不上' },
  { key: 'fireArrivedFirst', label: '消防先到场' },
  { key: 'compensationRequested', label: '乘客要求赔偿' },
  { key: 'repeatFault', label: '反复故障' },
  { key: 'misuseClaimed', label: '维保主张使用不当' }
]
