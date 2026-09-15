import dayjs from 'dayjs'

export function fmtTime(value, pattern = 'YYYY-MM-DD HH:mm') {
  if (!value) return '—'
  return dayjs(value).format(pattern)
}

export function fmtDate(value) {
  return fmtTime(value, 'YYYY-MM-DD')
}

/** 救援时长（分钟）：报警时间 → 释放时间 */
export function rescueMinutes(event) {
  if (!event?.alarmTime || !event?.releasedAt) return null
  return Math.round(dayjs(event.releasedAt).diff(dayjs(event.alarmTime), 'minute', true))
}

/** 从报警到现在的分钟数（用于进行中事件） */
export function elapsedMinutes(event) {
  if (!event?.alarmTime) return null
  return Math.max(0, Math.round(dayjs().diff(dayjs(event.alarmTime), 'minute', true)))
}
