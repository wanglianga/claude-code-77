# 小区电梯困人救援与维保责任追踪平台

面向高层住宅小区的电梯困人应急救援与维保责任追踪系统。覆盖「接警 → 调度 → 到场 → 释放 → 复位/停梯/复检 → 责任判定 → 关闭归档 → 善后回访」全流程，并接入电梯年检、维保合同、配件更换、业主投诉、物业值班表与楼栋公告，实现困人事件的全生命周期闭环管理与维保单位考核追责。

## 原始需求

> 建设小区电梯困人救援与维保责任追踪平台，可采用 Vue 3、Spring Boot 和 PostgreSQL。电梯物联网报警、乘客电话求助或物业巡查发现困人后，平台记录电梯编号、楼栋、楼层、轿厢人数、老人儿童情况、通话状态、门区位置和电梯维保单位。物业值班员接警后，需要同步通知维保人员、保安、楼栋管家和必要时的消防救援，并持续记录与轿厢内乘客的通话安抚。维保人员到场后，平台记录到达时间、开门方式、故障代码、困人释放、乘客身体状态和是否需要医疗协助。若发生维保迟到、物业联系不上、消防先到场、乘客要求赔偿、同一电梯反复故障或维保单位认为使用不当，平台要把报警、救援、复位、停梯、复检、业主通知和费用责任放在同一个事件里处理。事件关闭后，救援时长、责任判定、维保整改、停梯公告和业主回访进入档案，用于判断是否更换部件、处罚维保单位或调整物业值班。平台还要接入电梯年检、维保合同、配件更换、业主投诉和物业值班表。对于高层住宅，救援过程中还要同步告知同楼栋业主是否停用电梯、是否开放备用梯、老人上下楼如何临时协助，避免救援结束后楼栋秩序继续混乱。

## 原始需求（本轮任务：停梯整改触发证据与复检版本审计修复）

> 修复反复故障停梯整改的触发证据与复检版本审计：整改申请只能基于同一电梯一周内达到阈值的困人事件，事务内重新核验并固化触发事件、故障代码和投诉汇总快照；复检未通过后不得覆盖原方案及失败结论，应生成可追溯的新方案版本或不可变复检记录，保留每版配件、预计到货、复检人、公告时间、提交/复检人员与结论。当前有效版本未通过时电梯和停梯公告持续有效，只有最新版本复检通过才允许恢复运行并发布复检公告。验收：0/1起事件的电梯申请返回4xx且方案、停梯状态、公告不变；同一电梯首版失败后修订并通过，历史失败版本及其字段可查，运行状态和恢复公告只在最新版通过后变更。

## 原始需求（本轮任务：复检通过后的居民通知状态冲突修复）

> 修复停梯整改复检通过后的居民通知状态冲突。同一电梯的整改方案最新版复检通过并恢复运行后，系统当前仍将此前“复检通过前保持停用”的停梯公告保留为有效，同时又发布恢复运行通知，居民会收到相反的出行指引。停梯公告、恢复公告、电梯运行状态和整改版本必须围绕同一整改单联动：首版或修订版未通过时原停梯公告持续有效且不得出现恢复通知；只有最新版通过时，才在同一处置中结束或撤回关联停梯公告并发布唯一有效的恢复公告，历史公告仍可审计；历史版本重复复检或旧版本通过均不得改写当前公告。验收：首版失败、第二版通过后，公告时间线保留失败与停梯依据，但居民端仅显示一条有效的恢复运行指引；重复提交复检或对旧版本操作被拒绝，电梯状态、公告数量和通知内容不变。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端 | Vue 3 + Vite + Element Plus + Pinia + Vue Router + ECharts |
| 后端 | Spring Boot 3（Web / Data JPA / Security / Validation / Actuator）+ JWT |
| 数据库 | PostgreSQL 16 |
| 部署 | Docker Compose（db / backend / frontend 三服务，仅前端发布宿主端口） |

## 快速开始

### 环境要求

- Docker 与 Docker Compose 可用

### 启动

```bash
cp .env.example .env      # 按需修改端口与密钥
docker compose up -d --build
```

启动后查看前端实际映射端口（`.env` 中的 `CC_PUBLISH_PORT`，缺省 8080）：

```bash
docker compose port frontend 8080
# 例如输出 0.0.0.0:3077，浏览器访问 http://localhost:3077
```

首次启动时后端自动建表并写入演示数据（3 栋楼、6 台电梯、2 家维保单位、5 起已闭环历史事件、2 起进行中事件、投诉/值班/公告/回访等）。

### 停止

```bash
docker compose down          # 保留数据卷
docker compose down -v       # 同时清空数据库
```

## 验证方式

验证以「宿主 docker compose up 健康 + 关键业务流可走通」为标准：

1. `docker compose up -d --build` 后，`docker compose ps` 三个服务均为 healthy/running；
2. `docker compose port frontend 8080` 取实际映射端口，浏览器打开 `http://host.docker.internal:<端口>` 可打开登录页；
3. 用 `duty01 / 123456` 登录 → 工作台展示统计与图表；
4. 打开「困人事件管理」→ 进行中的 `EV当天日期-001` → 依次完成 通知调度 → 到场登记 → 困人释放 → 复位复检 → 关闭归档，右侧时间线、通话安抚、通知记录同步更新；
5. 接口自测（可选）：

```bash
PORT=$(docker compose port frontend 8080 | cut -d: -f2)
TOKEN=$(curl -s -X POST "http://host.docker.internal:${PORT}/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d '{"username":"duty01","password":"123456"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
curl -s "http://host.docker.internal:${PORT}/api/dashboard" -H "Authorization: Bearer ${TOKEN}"
```

### 停梯整改验收流（对应本轮任务验收标准）

以下脚本按「0/1 起事件 4xx 且状态不变 → 达标电梯申请 → 首版失败 → 修订通过」走通，可直接执行（需先按上文取得 `PORT` 与 `TOKEN`，另需维保账号 `maint02/123456` 的 `MTOKEN`）：

```bash
B="http://host.docker.internal:${PORT}/api"
AUTH="Authorization: Bearer ${TOKEN}"
MAUTH="Authorization: Bearer ${MTOKEN}"
CT="Content-Type: application/json"

# 电梯 id：1=DT-1-1(0起) 2=DT-1-2(0起) 3=DT-2-1(3起) 5=DT-3-1(1起) 6=DT-3-2(整改中)
# ① 0/1 起事件的电梯申请 → 4xx，且方案、停梯状态、公告不变
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$B/elevators/1/rectification-plans" -H "$AUTH" -H "$CT" -d '{"requestNote":"x"}'   # → 400
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$B/elevators/5/rectification-plans" -H "$AUTH" -H "$CT" -d '{"requestNote":"x"}'   # → 400
curl -s "$B/elevators/1" -H "$AUTH" | grep -o '"status":"RUNNING"'            # 电梯仍为运行
curl -s "$B/rectification-plans" -H "$AUTH"                                   # 无新申请（仅种子 DT-3-2 一条）

# ② 达标电梯（DT-2-1，近 7 天 3 起）申请 → 成功并固化快照、停梯、发停梯公告
curl -s -X POST "$B/elevators/3/rectification-plans" -H "$AUTH" -H "$CT" -d '{"requestNote":"一周3起困人，要求彻底整改"}'
# 返回中含 triggerEventCount=3、triggerEventsJson/faultCodesJson/complaintSummaryJson 快照
curl -s "$B/elevators/3" -H "$AUTH" | grep -o '"status":"STOPPED"'            # 已停梯

# ③ 首版提交 → 复检未通过（maint02 为 DT-2-1 所属维保单位账号）
curl -s -X POST "$B/rectification-plans/2/versions" -H "$MAUTH" -H "$CT" \
  -d '{"parts":"安全回路触点组件 ×2","expectedArrival":"2026-09-20","recheckInspector":"特检院 李工","noticePublishTime":"2026-09-21T09:00:00","planDetail":"更换触点组件"}'
curl -s -X POST "$B/rectification-versions/2/recheck" -H "$MAUTH" -H "$CT" \
  -d '{"pass":false,"result":"更换后安全回路仍偶发断开，未通过"}'
curl -s "$B/elevators/3" -H "$AUTH" | grep -o '"status":"STOPPED"'            # 仍未通过 → 保持停梯

# ④ 修订第 2 版 → 复检通过 → 仅此时电梯恢复运行并发布复检公告
curl -s -X POST "$B/rectification-plans/2/versions" -H "$MAUTH" -H "$CT" \
  -d '{"parts":"安全回路整套组件（含主板） ×1","expectedArrival":"2026-09-25","recheckInspector":"特检院 李工","noticePublishTime":"2026-09-26T09:00:00","planDetail":"整套更换并全检"}'
curl -s -X POST "$B/rectification-versions/3/recheck" -H "$MAUTH" -H "$CT" \
  -d '{"pass":true,"result":"全检合格，同意恢复运行"}'
curl -s "$B/elevators/3" -H "$AUTH" | grep -o '"status":"RUNNING"'            # 最新版通过 → 恢复运行

# ⑤ 历史失败版本及其字段、不可变复检记录均可查
curl -s "$B/rectification-plans/2" -H "$AUTH"    # versions[0] 为 RECHECK_FAILED 且配件/结论完整；recheckRecords 两条
```

### 公告联动验收流（对应「居民通知状态冲突」验收标准）

```bash
# 业主（居民端）token
OTOKEN=$(curl -s -X POST "$B/auth/login" -H "$CT" -d '{"username":"owner01","password":"123456"}' | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
OAUTH="Authorization: Bearer ${OTOKEN}"

# ⑥ 首版失败、第二版通过后：公告时间线保留停梯依据（已撤回），居民端仅一条有效恢复指引
curl -s "$B/rectification-plans/2" -H "$AUTH" | python3 -c "
import json,sys; d=json.load(sys.stdin)
for n in d['notices']: print(n['type'], n['status'], n['title'], '| 撤回:', n.get('revokeReason'))"
#   → STOP_NOTICE REVOKED（停梯公告，撤回原因=第 2 版复检通过）+ RECHECK PUBLISHED（恢复公告）
curl -s "$B/notices?buildingId=2" -H "$OAUTH"    # 居民端（业主）只见 PUBLISHED
#   → 仅一条 DT-2-1 相关有效指引：RECHECK 恢复运行公告（停梯公告已撤回不可见）

# ⑦ 重复复检 / 旧版本操作被拒绝，电梯状态、公告数量和通知内容不变
curl -s "$B/notices" -H "$AUTH" | python3 -c "import json,sys; print('公告总数:', len(json.load(sys.stdin)))"
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$B/rectification-versions/3/recheck" -H "$MAUTH" -H "$CT" \
  -d '{"pass":false,"result":"试图重复复检"}'                                   # → 400（已闭环）
curl -s -o /dev/null -w '%{http_code}\n' -X POST "$B/rectification-versions/2/recheck" -H "$MAUTH" -H "$CT" \
  -d '{"pass":true,"result":"试图操作旧版本"}'                                  # → 400（非最新版本）
curl -s "$B/elevators/3" -H "$AUTH" | grep -o '"status":"RUNNING"'            # 状态不变
curl -s "$B/notices" -H "$AUTH" | python3 -c "import json,sys; print('公告总数:', len(json.load(sys.stdin)))"  # 数量不变

# ⑧ 关联整改单且未闭环的停梯公告禁止手动撤回（DT-3-2 仍整改中）
curl -s "$B/rectification-plans/1" -H "$AUTH" | python3 -c "import json,sys; print([n['id'] for n in json.load(sys.stdin)['notices'] if n['status']=='PUBLISHED'])"
curl -s -o /dev/null -w '%{http_code}\n' -X PUT "$B/notices/<停梯公告id>/revoke" -H "$AUTH"   # → 400
```

> 注：上例中整改申请 id 为 `2`（种子数据 DT-3-2 的申请为 `1`）、版本 id 依次为 `2`、`3`；若数据库非全新，请先用 `GET /api/rectification-plans` 查实际 id。

## 测试账号（逐角色）

| 用户名 | 密码 | 角色 | 权限/职责说明 |
| --- | --- | --- | --- |
| `admin` | `admin123` | 系统管理员 | 全部功能 + 用户管理 |
| `duty01` | `123456` | 物业值班员（张伟） | 接警登记、通知调度、处置登记、关闭归档、发布公告、值班表 |
| `duty02` | `123456` | 物业值班员（李敏） | 同上 |
| `maint01` | `123456` | 维保人员（王强，安捷维保） | 查看事件与档案、到场/释放等处置协同 |
| `maint02` | `123456` | 维保人员（赵鹏，恒升维保） | 同上 |
| `sec01` | `123456` | 保安（刘建国） | 查看事件、接收通知 |
| `butler01` | `123456` | 楼栋管家（陈静，1 栋） | 查看事件、发布楼栋公告、业主安抚 |
| `butler02` | `123456` | 楼栋管家（周婷，2 栋） | 同上 |
| `fire01` | `123456` | 消防救援联络（周正） | 查看事件、消防到场登记协同 |
| `owner01` | `123456` | 业主（王秀兰） | 查看公告与基础信息 |

### 事件处置权限矩阵（后端强制校验）

| 操作 | 管理员 | 物业值班员 | 维保人员 | 楼栋管家 | 消防 | 保安 | 业主 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 通知调度 / 关闭归档 | ✓ | ✓ | — | — | — | — | — |
| 到场登记 | ✓ | ✓ | 仅本维保单位事件（登记维保到场） | — | 仅登记消防到场 | — | — |
| 困人释放 / 复位复检 | ✓ | ✓ | 仅本维保单位事件 | — | — | — | — |
| 通话安抚 | ✓ | ✓ | 仅本维保单位事件 | 仅本楼栋事件 | ✓ | — | — |
| 异常标记 | ✓ | ✓ | 仅本维保单位事件 | — | — | — | — |
| 通知确认 | ✓ | ✓ | 本单位通知 | 本楼栋通知 | 本岗位通知 | 本岗位通知 | — |
| 业主回访 | ✓ | ✓ | — | 仅本楼栋事件 | — | — | — |
| 整改核验 | ✓ | ✓ | 仅本维保单位事件 | — | — | — | — |

> 业主、保安及非本楼栋管家无法推进救援状态；越权请求返回 403 且不产生任何事件变更、通知或日志。用户管理仅 admin 可用。

### 处置资料门禁（后端强制校验）

- **调度完整性**：必须同步通知维保单位、保安、楼栋管家；含老人/儿童或通话中断（无法接通/时断时续）的高风险场景必须通知消防救援；
- **释放门禁**：仅在维保（或消防）到场后允许登记，且开门方式、故障代码、乘客身体状态、是否医疗协助均为必填；
- **复位门禁**：必须填写复检结果（复检时间缺省取当前），作为归档资料保留；
- **关闭门禁**：责任判定不能为「待定」，必须填写责任说明并选择费用承担方；判定维保责任/共同责任时必须填写整改要求，责任资料随事件归档。

### 乘客健康安抚（救援过程）

- **健康记录**：每次通话可记录乘客年龄、是否恐慌、是否有心脏病/孕妇、能否清楚描述状态，以及物业安抚内容；通话频率（次数、次/小时、平均间隔）自动统计；
- **超时提醒**：被困超过阈值（默认 30 分钟，`RESCUE_ALERT_MINUTES` 可调）未释放，页面红色提醒物业联系 120 并协调消防优先介入；
- **描述不清**：乘客无法清楚描述状态时，页面提示值班员保持通话，并自动向保安推送「到现场确认轿厢声音和楼层」的通知；
- **通话中断**：通话状态置为「无法接通」时，页面提示物业立即重拨，并自动通知现场保安确认轿厢内回应；
- **健康风险**：记录到恐慌/心脏病/孕妇时，页面实时提示保持安抚、提前联系 120 待命；
- **归档可见**：事件关闭后，安抚通话次数、频率、健康风险标记与全部通话记录随事件归档，业主回访时可直接查看。

### 反复故障停梯整改（触发证据 + 复检版本审计）

- **自动汇总**：同一电梯一周内 ≥2 次困人即列入反复故障预警，自动汇总故障代码、困人事件、维保（配件更换）记录、本次停梯时长与业主投诉；
- **整改申请（触发证据门禁）**：申请只能基于同一电梯一周内达到阈值（默认 7 天 / 2 起，`RECTIFICATION_TRIGGER_WINDOW_DAYS` / `RECTIFICATION_TRIGGER_THRESHOLD` 可调）的困人事件；事务内锁定电梯行**重新核验**，不达标整体回滚返回 4xx，方案、停梯状态、公告均不变；核验通过后同事务**固化触发事件、故障代码、投诉汇总快照**（含证据窗口与当时阈值，此后不可变），电梯随即保持停用并自动发布楼栋停梯公告；
- **方案版本流转**：维保每次提交生成**新版本行**（版本号递增，配件、预计到货、复检人、业主公告发布时间必填）；复检未通过后**不得覆盖原方案及失败结论**，只能提交可追溯的修订版本，历史失败版本及其全部字段永久可查；
- **不可变复检记录**：每次复检登记生成一条只增不改的复检记录（是否通过、结果、复检登记人、时间），每版仅允许登记一次，且只能对**最新版本**登记；
- **公告围绕整改单联动**：停梯公告/恢复公告均关联整改单（`rectificationPlanId`）。首版或修订版未通过时，原停梯公告持续有效且**不会出现任何恢复通知**；只有最新版复检通过时，才在**同一事务**中撤回关联停梯公告（保留撤回时间与原因，历史公告可审计）并发布**唯一有效**的恢复公告；历史版本重复复检或旧版本操作均被拒绝，不改写当前公告。关联整改单且未闭环的停梯公告禁止手动撤回；
- **居民端只显示有效指引**：业主（OWNER）查询公告时仅返回当前有效（PUBLISHED）公告——停梯期间看到停梯指引，复检通过后只看到恢复运行公告，不会收到相反出行指引；管理角色可查看含已撤回的全部公告用于审计；
- **恢复运行门禁**：当前有效版本未通过时电梯与停梯公告持续有效（事件复位、电梯档案编辑等旁路也被禁止恢复运行）；**只有最新版本复检通过**才允许恢复运行；
- **停梯时长**：电梯进入停梯状态自动计时，恢复运行清零，故障汇总中实时展示；
- **老人帮扶**：停梯期间可登记老人上下楼需求（就医、买菜等）与临时帮扶人员，帮扶中/已办结全程可跟踪，避免整改影响日常生活。

## 演示数据说明

- **楼栋/电梯**：1 栋（33 层）、2 栋（28 层）、3 栋（18 层），共 6 台电梯（DT-1-1 ~ DT-3-2），DT-3-2 处于停梯状态；
- **维保单位**：安捷电梯维保（信用 92）、恒升机电维保（信用 85，有迟到与反复故障记录）；
- **历史事件（已关闭）**：含维保迟到、乘客索赔、反复故障、消防先到场、维保主张使用不当、设备老化等典型情形，均已走完责任判定与归档；
- **进行中事件**：`EV当天-001`（待调度，含老人儿童）与 `EV当天-002`（已调度待到场），用于演示完整处置流程；
- **停梯整改申请**：DT-3-2 近一周 2 起困人（E57 门锁回路故障）已发起整改申请（含触发事件/故障代码/投诉汇总快照），第 1 版方案已提交待复检；DT-2-1 近一周 3 起困人（E21 安全回路断开）尚未发起申请，可演示「申请 → 首版失败 → 修订通过」全流程；DT-1-1（0 起）/ DT-3-1（1 起）用于演示证据不足返回 4xx；
- **配套数据**：维保合同 6 份、年检记录 6 条、配件更换 3 条、业主投诉 3 条、本周值班表、停梯/备用梯/老人协助公告 3 条、业主回访 2 条。

## 核心业务流程

```
接警登记(PENDING) → 通知调度(DISPATCHED) → 到场登记(ARRIVED)
  → 困人释放(RELEASED) → 复位/停梯/复检(RESET) → 关闭归档(CLOSED) → 业主回访/整改核验
```

- **接警**：记录电梯、楼栋、被困楼层、轿厢人数、老人儿童、通话状态、门区位置、求助人；同一电梯 90 天内 ≥3 次困人自动标记「反复故障」；
- **调度**：一键同步通知维保单位/保安/楼栋管家/消防救援，生成通知记录并可标记「已确认 / 联系不上」；
- **到场**：维保超过 30 分钟到场自动标记「维保迟到」；消防先于维保到场自动标记「消防先到场」；
- **释放**：记录释放时间、开门方式、故障代码、乘客身体状态、是否需医疗协助，自动计算救援时长；
- **复位**：登记复位/停梯/复检结果，停梯会同步更新电梯档案状态并提示发布楼栋公告；
- **关闭**：责任判定（维保/物业/业主使用不当/设备老化/共同责任）、费用承担与金额、整改要求与期限、业主通知；判定维保责任自动扣减维保单位信用分并累计处罚次数；
- **善后**：业主回访（满意度）、整改完成核验、关联停梯/备用梯/老人协助公告，全部沉淀进事件档案。

## 功能清单

- 应急指挥工作台：进行中事件、平均救援时长、停梯数、近 7 天趋势、状态分布、维保单位考核对比、反复故障电梯预警
- 困人事件管理：列表筛选（状态/楼栋/电梯）、接警登记、六阶段处置工作台、通话安抚、通知记录、处置时间线、异常标记
- 电梯与维保档案：电梯台账（含年检到期预警）、维保单位信用/处罚、维保合同、年检记录、配件更换
- 业主投诉：登记、处理流转（待处理/处理中/已办结）
- 物业值班表：白班/夜班排班与删除
- 楼栋公告：停梯公告、备用梯开放、老人临时协助、复检通知，支持撤回
- 用户管理（管理员）：账号增改、角色分配、启停用、重置密码

## 项目结构

```
├── docker-compose.yml          # 三服务编排（db 不发布端口，仅 frontend 发布 CC_PUBLISH_PORT）
├── .env.example                # 环境变量模板
├── backend/                    # Spring Boot 后端
│   ├── Dockerfile              # 多阶段构建（maven → temurin-jre，非 root + HEALTHCHECK）
│   └── src/main/java/com/elevator/rescue/
│       ├── config/             # 安全配置、JWT 过滤器、全局异常、演示数据种子
│       ├── controller/         # REST 接口（事件工作流 / 档案 / 投诉 / 值班 / 公告 / 用户 / 统计）
│       ├── dto/                # 事件处置请求 DTO
│       ├── entity/             # 15 个 JPA 实体（枚举内嵌）
│       ├── repository/         # Spring Data JPA 仓库
│       ├── security/           # JWT 工具、UserDetailsService、当前用户解析
│       └── service/            # 事件工作流 EventService、统计 DashboardService
└── frontend/                   # Vue 3 前端
    ├── Dockerfile              # 多阶段构建（node → nginx-unprivileged，非 root + HEALTHCHECK）
    ├── nginx.conf              # 静态托管 + /api 反向代理到 backend
    └── src/
        ├── layout/             # 主布局（侧边导航 + 进行中事件角标）
        ├── views/              # 工作台/事件列表/接警/处置详情/档案/投诉/值班/公告/用户
        ├── store/              # Pinia 认证状态
        ├── api/                # axios 实例（JWT 拦截器、401 跳转）
        └── utils/              # 业务字典、时间格式化
```

## 主要接口一览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/login` | 登录获取 JWT |
| GET | `/api/dashboard` | 工作台统计 |
| GET/POST | `/api/events` | 事件列表 / 接警登记 |
| GET | `/api/events/{id}` | 事件详情聚合（事件+通话+通知+时间线+回访+公告+投诉+配件） |
| POST | `/api/events/{id}/dispatch` | 通知调度 |
| POST | `/api/events/{id}/calls` | 通话安抚记录 |
| POST | `/api/events/{id}/arrive` | 到场登记（维保/消防） |
| POST | `/api/events/{id}/release` | 困人释放登记 |
| POST | `/api/events/{id}/reset` | 复位/停梯/复检 |
| POST | `/api/events/{id}/close` | 责任判定与关闭归档 |
| PUT | `/api/events/{id}/flags` | 异常标记更新 |
| POST | `/api/events/{id}/followups` | 业主回访 |
| GET/POST | `/api/elevators` 等 | 电梯/楼栋/维保单位/合同/年检/配件档案 |
| GET/POST | `/api/complaints`、`/api/duty-schedules` | 投诉 / 值班 |
| GET/POST/PUT | `/api/notices`、`/api/notices/{id}/revoke` | 公告（业主仅见有效公告；关联整改单未闭环的停梯公告禁止手动撤回） |
| GET | `/api/elevators/repeat-faults`、`/api/elevators/{id}/fault-summary` | 反复故障预警 / 单梯故障汇总 |
| POST | `/api/elevators/{id}/rectification-plans` | 发起停梯整改申请（事务内核验阈值+固化证据快照，不足返回 4xx） |
| GET | `/api/rectification-plans`、`/api/rectification-plans/{id}` | 整改申请列表 / 详情（快照+全部版本+复检记录） |
| POST | `/api/rectification-plans/{id}/versions` | 提交方案新版本（历史版本不可覆盖） |
| POST | `/api/rectification-versions/{versionId}/recheck` | 复检登记（仅最新版本一次，生成不可变复检记录） |
| GET | `/api/rectification-plans/{id}/recheck-records` | 不可变复检记录列表 |
| GET/POST/PUT | `/api/users` | 用户管理（管理员） |

## 环境变量

| 变量 | 说明 | 缺省 |
| --- | --- | --- |
| `CC_PUBLISH_PORT` | 前端发布到宿主的端口 | 8080 |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | 数据库库名/账号/密码 | elevator_rescue / postgres / 必填 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字符） | 必填 |
| `ARRIVE_LIMIT_MINUTES` | 维保到场时限（超时自动标记迟到） | 30 |
| `RECTIFICATION_TRIGGER_THRESHOLD` | 停梯整改申请触发阈值（窗口内困人事件起数） | 2 |
| `RECTIFICATION_TRIGGER_WINDOW_DAYS` | 触发证据窗口（天） | 7 |
