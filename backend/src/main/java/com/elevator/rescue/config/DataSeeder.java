package com.elevator.rescue.config;

import com.elevator.rescue.entity.*;
import com.elevator.rescue.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 首次启动时初始化演示数据（仅当用户表为空时执行）。
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepo;
    private final BuildingRepository buildingRepo;
    private final MaintenanceCompanyRepository companyRepo;
    private final ElevatorRepository elevatorRepo;
    private final MaintenanceContractRepository contractRepo;
    private final AnnualInspectionRepository inspectionRepo;
    private final PartReplacementRepository partRepo;
    private final RescueEventRepository eventRepo;
    private final EventLogRepository logRepo;
    private final EventCallRepository callRepo;
    private final EventNotificationRepository notificationRepo;
    private final OwnerComplaintRepository complaintRepo;
    private final DutyScheduleRepository dutyRepo;
    private final BuildingNoticeRepository noticeRepo;
    private final OwnerFollowupRepository followupRepo;
    private final RectificationPlanRepository planRepo;
    private final RectificationPlanVersionRepository versionRepo;
    private final ElderlyAssistanceRepository assistanceRepo;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepo.count() > 0) {
            return;
        }

        // ---------- 楼栋 ----------
        Building b1 = building("1栋（临江阁）", 33, 2, "怡景园小区 1 栋", "陈静", "13800001101");
        Building b2 = building("2栋（望江阁）", 28, 2, "怡景园小区 2 栋", "周婷", "13800001102");
        Building b3 = building("3栋（听涛阁）", 18, 1, "怡景园小区 3 栋", "吴凯", "13800001103");

        // ---------- 维保单位 ----------
        MaintenanceCompany c1 = company("安捷电梯维保有限公司", "王强", "13900002201", "400-800-1101", 92, 1,
                "一级维保资质，负责 1 栋、3 栋电梯");
        MaintenanceCompany c2 = company("恒升机电设备维保有限公司", "赵鹏", "13900002202", "400-800-1102", 85, 2,
                "二级维保资质，负责 2 栋电梯，近半年响应偏慢");

        // ---------- 用户 ----------
        User admin = user("admin", "admin123", "罗志强", "13500000001", User.Role.ADMIN, null, null);
        User duty1 = user("duty01", "123456", "张伟", "13500000002", User.Role.DUTY, null, null);
        User duty2 = user("duty02", "123456", "李敏", "13500000003", User.Role.DUTY, null, null);
        User maint1 = user("maint01", "123456", "王强", "13900002201", User.Role.MAINTENANCE, c1.getId(), null);
        User maint2 = user("maint02", "123456", "赵鹏", "13900002202", User.Role.MAINTENANCE, c2.getId(), null);
        User sec1 = user("sec01", "123456", "刘建国", "13500000006", User.Role.SECURITY, null, null);
        User butler1 = user("butler01", "123456", "陈静", "13800001101", User.Role.BUTLER, null, b1.getId());
        User butler2 = user("butler02", "123456", "周婷", "13800001102", User.Role.BUTLER, null, b2.getId());
        User fire1 = user("fire01", "123456", "周正", "13500000009", User.Role.FIRE, null, null);
        user("owner01", "123456", "王秀兰", "13600000010", User.Role.OWNER, null, b1.getId());

        // ---------- 电梯 ----------
        Elevator e11 = elevator("DT-1-1", b1, c1, 33, "1 单元大堂东侧", "日立", "HGP-1050", "1 单元",
                LocalDate.of(2018, 6, 20), LocalDate.of(2026, 11, 20), Elevator.ElevatorStatus.RUNNING);
        Elevator e12 = elevator("DT-1-2", b1, c1, 33, "2 单元大堂西侧", "日立", "HGP-1050", "2 单元",
                LocalDate.of(2018, 6, 20), LocalDate.of(2026, 11, 20), Elevator.ElevatorStatus.RUNNING);
        Elevator e21 = elevator("DT-2-1", b2, c2, 28, "1 单元大堂正中", "通力", "KONE-3000S", "1 单元",
                LocalDate.of(2017, 3, 15), LocalDate.of(2026, 10, 15), Elevator.ElevatorStatus.RUNNING);
        Elevator e22 = elevator("DT-2-2", b2, c2, 28, "2 单元大堂正中", "通力", "KONE-3000S", "2 单元",
                LocalDate.of(2017, 3, 15), LocalDate.of(2026, 10, 15), Elevator.ElevatorStatus.RUNNING);
        Elevator e31 = elevator("DT-3-1", b3, c1, 18, "单元大堂北侧", "奥的斯", "OTIS-Gen2", "1 单元",
                LocalDate.of(2019, 9, 1), LocalDate.of(2026, 12, 1), Elevator.ElevatorStatus.RUNNING);
        Elevator e32 = elevator("DT-3-2", b3, c2, 18, "单元大堂南侧", "奥的斯", "OTIS-Gen2", "1 单元",
                LocalDate.of(2019, 9, 1), LocalDate.of(2026, 12, 1), Elevator.ElevatorStatus.STOPPED);

        // ---------- 维保合同 ----------
        int contractSeq = 1;
        for (Elevator e : List.of(e11, e12, e21, e22, e31, e32)) {
            MaintenanceContract contract = new MaintenanceContract();
            contract.setContractNo("HT-2026-" + String.format("%03d", contractSeq++));
            contract.setElevator(e);
            contract.setCompany(e.getCompany());
            contract.setStartDate(LocalDate.of(2026, 1, 1));
            contract.setEndDate(LocalDate.of(2026, 12, 31));
            contract.setAnnualFee(new BigDecimal("6000"));
            contract.setContent("含每月 2 次例行维保、困人救援 30 分钟内到场、重大故障 2 小时响应。");
            contractRepo.save(contract);
        }

        // ---------- 年检 ----------
        inspection(e11, LocalDate.of(2025, 11, 20), LocalDate.of(2026, 11, 20),
                AnnualInspection.InspectionResult.PASS, "市特种设备检验研究院", "TJ-2025-1101", null);
        inspection(e12, LocalDate.of(2025, 11, 20), LocalDate.of(2026, 11, 20),
                AnnualInspection.InspectionResult.PASS, "市特种设备检验研究院", "TJ-2025-1102", null);
        inspection(e21, LocalDate.of(2025, 10, 15), LocalDate.of(2026, 10, 15),
                AnnualInspection.InspectionResult.PASS, "市特种设备检验研究院", "TJ-2025-1008", null);
        inspection(e22, LocalDate.of(2025, 10, 15), LocalDate.of(2026, 10, 15),
                AnnualInspection.InspectionResult.PASS, "市特种设备检验研究院", "TJ-2025-1009", null);
        inspection(e31, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 1),
                AnnualInspection.InspectionResult.PASS, "市特种设备检验研究院", "TJ-2025-1201", null);
        inspection(e32, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 1),
                AnnualInspection.InspectionResult.RECTIFY, "市特种设备检验研究院", "TJ-2025-1202",
                "门锁回路接触不良，限期整改后复检合格");

        // ---------- 配件更换 ----------
        part(e11, null, "门机皮带", LocalDate.of(2026, 3, 12), "皮带老化开裂", new BigDecimal("850"), "安捷电梯维保");
        part(e21, null, "曳引轮轴承", LocalDate.of(2026, 5, 8), "运行异响，轴承磨损", new BigDecimal("2300"), "恒升机电维保");
        part(e32, null, "门锁触点", LocalDate.of(2026, 8, 12), "年检整改项：门锁回路接触不良", new BigDecimal("320"), "恒升机电维保");

        // ---------- 历史困人事件（已关闭） ----------
        // 1. DT-1-1 门机故障，28 分钟救出，维保责任
        RescueEvent ev1 = closedEvent("EV20260618-001", e11, b1, RescueEvent.AlarmSource.IOT,
                LocalDateTime.of(2026, 6, 18, 8, 32), "12 层", 3, 1, 0,
                28, "松闸盘车平层开门", "E43-门机控制器故障", "乘客情绪稳定，无身体不适", false,
                RescueEvent.Responsibility.MAINTENANCE, "门机控制器老化失效，属维保保养不到位",
                RescueEvent.CostBearer.MAINTENANCE, new BigDecimal("0"),
                "更换门机控制器并全面检查门系统", LocalDate.of(2026, 6, 25), true,
                duty1, maint1, sec1, butler1, null, 12, false, false, false);

        // 2. DT-2-1 维保迟到 42 分钟，乘客要求赔偿
        RescueEvent ev2 = closedEvent("EV20260702-001", e21, b2, RescueEvent.AlarmSource.PHONE,
                LocalDateTime.of(2026, 7, 2, 19, 5), "7-8 层之间", 2, 1, 0,
                51, "检修运行至平层开门", "E21-安全回路断开", "老人略有惊吓，血压偏高，已联系家属", false,
                RescueEvent.Responsibility.MAINTENANCE, "安全回路触点氧化；维保单位到场超时",
                RescueEvent.CostBearer.MAINTENANCE, new BigDecimal("2000"),
                "更换安全回路触点，维保单位提交超时情况说明", LocalDate.of(2026, 7, 10), true,
                duty2, maint2, sec1, butler2, null, 42, true, false, true);
        ev2.setCompensationDetail("被困老人家属要求赔偿体检费及精神抚慰金 2000 元，经协商由维保单位承担");
        eventRepo.save(ev2);

        // 3. DT-2-1 再次困人（反复故障）
        closedEvent("EV20260725-001", e21, b2, RescueEvent.AlarmSource.PATROL,
                LocalDateTime.of(2026, 7, 25, 7, 48), "3 层", 1, 0, 0,
                22, "松闸盘车平层开门", "E21-安全回路断开", "乘客无不适", false,
                RescueEvent.Responsibility.MAINTENANCE, "同一故障代码重复出现，上次整改不彻底",
                RescueEvent.CostBearer.MAINTENANCE, new BigDecimal("0"),
                "全面排查安全回路，更换整套触点组件；约谈维保单位负责人", LocalDate.of(2026, 8, 1), true,
                duty1, maint2, sec1, butler2, null, 25, true, true, false);

        // 4. DT-3-2 消防先到场，维保主张使用不当
        RescueEvent ev4 = closedEvent("EV20260810-001", e32, b3, RescueEvent.AlarmSource.IOT,
                LocalDateTime.of(2026, 8, 10, 21, 15), "9 层", 4, 0, 1,
                38, "消防协助开门", "E57-门锁回路故障", "儿童受惊吓，家长陪同就医检查无大碍", true,
                RescueEvent.Responsibility.JOINT, "门锁触点老化为主因；事发前有装修人员长时间挡门使用，维保主张使用不当，经调解认定为共同责任",
                RescueEvent.CostBearer.SHARED, new BigDecimal("1500"),
                "更换门锁触点组件；物业加强装修期间电梯使用管理", LocalDate.of(2026, 8, 20), true,
                duty2, maint2, sec1, null, fire1, 55, false, false, true);
        ev4.setMisuseClaimed(true);
        ev4.setFireArrivedFirst(true);
        eventRepo.save(ev4);

        // 5. DT-1-2 设备老化
        closedEvent("EV20260830-001", e12, b1, RescueEvent.AlarmSource.PHONE,
                LocalDateTime.of(2026, 8, 30, 12, 40), "22 层", 2, 0, 0,
                18, "检修运行至平层开门", "E30-平层感应器漂移", "乘客状态良好", false,
                RescueEvent.Responsibility.EQUIPMENT_AGING, "平层感应器老化漂移，电梯已运行 8 年",
                RescueEvent.CostBearer.NONE, null,
                "更换平层感应器，纳入下年度大修计划评估", LocalDate.of(2026, 9, 5), true,
                duty1, maint1, sec1, butler1, null, 15, false, false, false);

        // ---------- 历史事件健康安抚补充记录 ----------
        call(ev2, duty2, LocalDateTime.of(2026, 7, 2, 19, 12), "老人情绪紧张，呼吸急促",
                "安抚老人，指导缓慢深呼吸，告知维保与保安已在途中", "约 68 岁", true, true, false, true);
        call(ev2, duty2, LocalDateTime.of(2026, 7, 2, 19, 40), "老人情绪逐渐平稳",
                "持续安抚，确认无胸闷症状，准备配合开门", "约 68 岁", false, true, false, true);
        call(ev4, duty2, LocalDateTime.of(2026, 8, 10, 21, 22), "儿童哭闹，家长焦急",
                "安抚家长与儿童，告知消防已到场协助", "儿童 5 岁", true, false, false, true);

        // ---------- 进行中事件 ----------
        // 6. 今日新报警：DT-2-1（反复故障电梯），待调度
        RescueEvent ev6 = new RescueEvent();
        ev6.setEventNo("EV" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-001");
        ev6.setElevator(e21);
        ev6.setBuilding(b2);
        ev6.setAlarmSource(RescueEvent.AlarmSource.IOT);
        ev6.setAlarmTime(LocalDateTime.now().minusMinutes(6));
        ev6.setTrappedFloor("15 层附近");
        ev6.setPassengerCount(5);
        ev6.setElderlyCount(2);
        ev6.setChildrenCount(1);
        ev6.setCallStatus(RescueEvent.CallStatus.SMOOTH);
        ev6.setDoorZone(e21.getDoorZone());
        ev6.setReporterName("轿厢乘客");
        ev6.setReporterPhone("13611112222");
        ev6.setReportDetail("物联网平台推送困人报警，轿厢内 5 人，含 2 名老人 1 名儿童，已接通轿厢对讲");
        ev6.setHandler(duty1);
        ev6.setStatus(RescueEvent.EventStatus.PENDING);
        ev6.setRepeatFault(true);
        eventRepo.save(ev6);
        log(ev6, duty1.getRealName(), "接警登记", "物联网报警自动接入，被困 5 人（含老人 2 人、儿童 1 人）；该电梯 90 天内多次困人，已标记反复故障");

        // 7. 今日处置中：DT-3-1，已通知各方，等待维保到场
        RescueEvent ev7 = new RescueEvent();
        ev7.setEventNo("EV" + LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-002");
        ev7.setElevator(e31);
        ev7.setBuilding(b3);
        ev7.setAlarmSource(RescueEvent.AlarmSource.PHONE);
        ev7.setAlarmTime(LocalDateTime.now().minusMinutes(18));
        ev7.setTrappedFloor("5 层");
        ev7.setPassengerCount(2);
        ev7.setElderlyCount(0);
        ev7.setChildrenCount(0);
        ev7.setCallStatus(RescueEvent.CallStatus.SMOOTH);
        ev7.setDoorZone(e31.getDoorZone());
        ev7.setReporterName("刘先生");
        ev7.setReporterPhone("13633334444");
        ev7.setReportDetail("乘客电话求助，电梯停在 5 层不开门，轿厢照明正常");
        ev7.setHandler(duty2);
        ev7.setStatus(RescueEvent.EventStatus.DISPATCHED);
        LocalDateTime notifiedAt = LocalDateTime.now().minusMinutes(12);
        ev7.setMaintenanceNotifiedAt(notifiedAt);
        ev7.setSecurityNotifiedAt(notifiedAt);
        ev7.setButlerNotifiedAt(notifiedAt);
        eventRepo.save(ev7);
        log(ev7, duty2.getRealName(), "接警登记", "电话求助接入，被困 2 人");
        log(ev7, duty2.getRealName(), "通知调度", "已同步通知: 维保单位（安捷电梯维保有限公司）、保安、楼栋管家");
        notification(ev7, User.Role.MAINTENANCE, "王强", "400-800-1101", notifiedAt, true);
        notification(ev7, User.Role.SECURITY, "刘建国", "13500000006", notifiedAt, true);
        notification(ev7, User.Role.BUTLER, "吴凯", "13800001103", notifiedAt, false);
        call(ev7, duty2, LocalDateTime.now().minusMinutes(15), "乘客情绪平稳", "告知维保人员已出发，请勿扒门，保持镇定",
                "约 40 岁", false, false, false, true);
        call(ev7, duty2, LocalDateTime.now().minusMinutes(5), "乘客情绪平稳", "再次确认轿厢通风正常，乘客无不适",
                "约 40 岁", false, false, false, true);

        // ---------- 近 7 天反复故障（DT-2-1，含今日 EV-001 共 3 起） ----------
        closedEvent("EV" + LocalDate.now().minusDays(3).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-901",
                e21, b2, RescueEvent.AlarmSource.IOT,
                LocalDateTime.now().minusDays(3).withHour(9).withMinute(20).withSecond(0).withNano(0), "14 层", 2, 0, 0,
                26, "松闸盘车平层开门", "E21-安全回路断开", "乘客无不适", false,
                RescueEvent.Responsibility.MAINTENANCE, "安全回路触点再次失效",
                RescueEvent.CostBearer.MAINTENANCE, new BigDecimal("0"),
                "更换安全回路触点组件", LocalDate.now().plusDays(4), false,
                duty1, maint2, sec1, butler2, null, 24, false, true, false);
        closedEvent("EV" + LocalDate.now().minusDays(5).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-901",
                e21, b2, RescueEvent.AlarmSource.PHONE,
                LocalDateTime.now().minusDays(5).withHour(18).withMinute(45).withSecond(0).withNano(0), "6 层", 3, 1, 0,
                31, "检修运行至平层开门", "E21-安全回路断开", "老人略受惊吓", false,
                RescueEvent.Responsibility.MAINTENANCE, "安全回路老化，一周内第二次困人",
                RescueEvent.CostBearer.MAINTENANCE, new BigDecimal("0"),
                "全面排查安全回路", LocalDate.now().plusDays(2), false,
                duty2, maint2, sec1, butler2, null, 28, false, true, false);

        // ---------- 近 7 天反复故障（DT-3-2，门锁回路故障 2 起） ----------
        RescueEvent ev32a = closedEvent("EV" + LocalDate.now().minusDays(6).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-901",
                e32, b3, RescueEvent.AlarmSource.IOT,
                LocalDateTime.now().minusDays(6).withHour(8).withMinute(15).withSecond(0).withNano(0), "7 层", 2, 0, 0,
                24, "松闸盘车平层开门", "E57-门锁回路故障", "乘客无不适", false,
                RescueEvent.Responsibility.MAINTENANCE, "门锁触点组件老化，门锁回路断开",
                RescueEvent.CostBearer.MAINTENANCE, new BigDecimal("0"),
                "更换门锁触点组件", LocalDate.now().plusDays(1), false,
                duty2, maint2, sec1, null, null, 22, false, true, false);
        RescueEvent ev32b = closedEvent("EV" + LocalDate.now().minusDays(4).format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd")) + "-901",
                e32, b3, RescueEvent.AlarmSource.PHONE,
                LocalDateTime.now().minusDays(4).withHour(19).withMinute(40).withSecond(0).withNano(0), "11 层", 3, 1, 0,
                29, "检修运行至平层开门", "E57-门锁回路故障", "老人略受惊吓", false,
                RescueEvent.Responsibility.MAINTENANCE, "同一故障代码一周内再次出现，上次整改不彻底",
                RescueEvent.CostBearer.MAINTENANCE, new BigDecimal("0"),
                "全面排查门锁回路并提交彻底整改方案", LocalDate.now().plusDays(3), false,
                duty1, maint2, sec1, null, null, 26, false, true, false);

        // DT-3-2 业主投诉（进入整改申请的投诉汇总快照）
        OwnerComplaint e32Complaint = new OwnerComplaint();
        e32Complaint.setElevator(e32);
        e32Complaint.setEventId(ev32b.getId());
        e32Complaint.setOwnerName("刘先生");
        e32Complaint.setOwnerPhone("13633334444");
        e32Complaint.setBuildingName(b3.getName());
        e32Complaint.setContent("3 栋电梯一周困人两次，家里老人不敢坐电梯，要求彻底整改并公布复检结果");
        e32Complaint.setStatus(OwnerComplaint.ComplaintStatus.PROCESSING);
        e32Complaint.setHandlerName(duty2.getRealName());
        complaintRepo.save(e32Complaint);

        // ---------- DT-3-2 停梯整改申请（含触发证据快照；第 1 版方案已提交，待复检） ----------
        e32.setStoppedSince(LocalDateTime.now().minusDays(2));
        elevatorRepo.save(e32);
        RectificationPlan plan = new RectificationPlan();
        plan.setElevator(e32);
        plan.setEventId(ev32b.getId());
        plan.setCompanyName(c2.getName());
        plan.setRequestNote("一周内 2 起困人（门锁回路故障），要求提交彻底整改方案");
        plan.setRequestedBy(duty2.getRealName());
        plan.setRequestedAt(LocalDateTime.now().minusDays(2));
        plan.setStatus(RectificationPlan.PlanStatus.SUBMITTED);
        // 触发证据快照（申请时固化，不可变）
        plan.setTriggerThreshold(2);
        plan.setEvidenceWindowStart(plan.getRequestedAt().minusDays(7));
        plan.setEvidenceWindowEnd(plan.getRequestedAt());
        plan.setTriggerEventCount(2);
        plan.setTriggerEventsJson(toJson(List.of(snapshotEvent(ev32a), snapshotEvent(ev32b))));
        plan.setFaultCodesJson(toJson(List.of("E57-门锁回路故障")));
        Map<String, Object> complaintSummary = new LinkedHashMap<>();
        complaintSummary.put("windowDays", 7);
        complaintSummary.put("count", 1);
        Map<String, Object> complaintItem = new LinkedHashMap<>();
        complaintItem.put("id", e32Complaint.getId());
        complaintItem.put("ownerName", e32Complaint.getOwnerName());
        complaintItem.put("content", e32Complaint.getContent());
        complaintItem.put("status", e32Complaint.getStatus().name());
        complaintItem.put("createdAt", e32Complaint.getCreatedAt().toString());
        complaintSummary.put("items", List.of(complaintItem));
        plan.setComplaintSummaryJson(toJson(complaintSummary));
        plan.setCurrentVersionNo(1);
        planRepo.save(plan);

        // 第 1 版方案（待复检）
        RectificationPlanVersion planV1 = new RectificationPlanVersion();
        planV1.setPlan(plan);
        planV1.setVersionNo(1);
        planV1.setParts("门锁触点组件 ×2、门机控制板 ×1");
        planV1.setExpectedArrival(LocalDate.now().plusDays(3));
        planV1.setRecheckInspector("市特种设备检验研究院 李工");
        planV1.setNoticePublishTime(LocalDateTime.now().plusDays(4));
        planV1.setPlanDetail("更换门锁触点组件与门机控制板，全检门系统与安全回路，复检合格后恢复运行。");
        planV1.setSubmittedBy(maint2.getRealName());
        planV1.setSubmittedAt(LocalDateTime.now().minusDays(1));
        planV1.setStatus(RectificationPlanVersion.VersionStatus.SUBMITTED);
        versionRepo.save(planV1);

        // DT-3-2 停梯公告（关联整改单 #plan，复检通过前持续有效；通过时由系统统一撤回）
        BuildingNotice stopNotice = new BuildingNotice();
        stopNotice.setBuilding(b3);
        stopNotice.setEventId(ev32b.getId());
        stopNotice.setRectificationPlanId(plan.getId());
        stopNotice.setType(BuildingNotice.NoticeType.STOP_NOTICE);
        stopNotice.setTitle("3 栋 DT-3-2 电梯停梯整改公告");
        stopNotice.setContent("因 DT-3-2 电梯最近 7 天内发生 2 起困人故障（故障代码：E57-门锁回路故障），物业已要求维保单位（"
                + c2.getName() + "）提交整改方案。复检通过前电梯保持停用，请业主使用 DT-3-1 电梯或步行梯；"
                + "高龄及行动不便业主可联系楼栋管家登记临时帮扶。 整改要求：" + plan.getRequestNote());
        stopNotice.setPublisherName(duty2.getRealName());
        stopNotice.setPublishedAt(plan.getRequestedAt());
        noticeRepo.save(stopNotice);

        // ---------- 停梯期间老人帮扶登记 ----------
        assistance(b3, e32, "张桂英", "3 栋 1502", "13611110001", "每周二、五上午去医院透析，需协助上下楼", "物业客服小李", "13500000101", true);
        assistance(b3, e32, "王德发", "3 栋 0901", "13611110002", "每日买菜需协助搬运上楼", "保安刘建国", "13500000006", true);
        assistance(b3, e32, "刘淑芬", "3 栋 1103", "13611110003", "临时下楼取药一次", "楼栋管家吴凯", "13800001103", false);

        // ---------- 业主投诉 ----------
        complaint(e21, ev2.getId(), "王秀兰", "13600000010", b2.getName(),
                "7 月 2 日困人事件维保 40 多分钟才到，老人受到惊吓，要求物业加强维保考核",
                OwnerComplaint.ComplaintStatus.RESOLVED, duty1,
                "已与维保单位约谈，赔偿由维保承担；物业将维保响应时效纳入月度考核");
        complaint(e21, null, "刘先生", "13655556666", b2.getName(),
                "2 栋 1 单元电梯近期运行有异响，担心再次困人，请尽快检查",
                OwnerComplaint.ComplaintStatus.PROCESSING, duty2,
                "已通知维保单位本周内安排专项检查");

        // ---------- 值班表（本周） ----------
        LocalDate monday = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1L);
        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            duty(day, i % 2 == 0 ? duty1 : duty2, DutySchedule.Shift.DAY, "监控中心");
            duty(day, i % 2 == 0 ? duty2 : duty1, DutySchedule.Shift.NIGHT, "监控中心");
            duty(day, sec1, DutySchedule.Shift.DAY, "1 号门岗");
        }

        // ---------- 楼栋公告 ----------
        notice(b3, ev4.getId(), BuildingNotice.NoticeType.BACKUP_LIFT,
                "3 栋备用梯开放通知",
                "停梯期间，3 栋 DT-3-1 电梯作为备用梯全时段开放，高峰期物业安排专人引导，请错峰出行。",
                duty2);
        notice(b1, null, BuildingNotice.NoticeType.ELDERLY_ASSIST,
                "1 栋高龄业主上下楼临时协助安排",
                "电梯维保期间，如需协助的高龄业主可联系楼栋管家陈静（13800001101），物业将安排人员协助上下楼及代购生活用品。",
                butler1);

        // ---------- 业主回访 ----------
        followup(ev1, "赵先生", "13677778888", LocalDateTime.of(2026, 6, 20, 10, 0), 4,
                "救援速度满意，希望加强电梯日常保养", duty1);
        followup(ev2, "王秀兰", "13600000010", LocalDateTime.of(2026, 7, 5, 15, 30), 2,
                "维保到场太慢，对处理结果不满意，要求更换维保单位", duty2);
    }

    // ---------------- 构造辅助 ----------------

    private Building building(String name, int floors, int units, String address, String manager, String phone) {
        Building b = new Building();
        b.setName(name);
        b.setFloors(floors);
        b.setUnits(units);
        b.setAddress(address);
        b.setManagerName(manager);
        b.setManagerPhone(phone);
        return buildingRepo.save(b);
    }

    private MaintenanceCompany company(String name, String contact, String phone, String emergency,
                                       int credit, int penalty, String remark) {
        MaintenanceCompany c = new MaintenanceCompany();
        c.setName(name);
        c.setContactPerson(contact);
        c.setContactPhone(phone);
        c.setEmergencyPhone(emergency);
        c.setCreditScore(credit);
        c.setPenaltyCount(penalty);
        c.setRemark(remark);
        return companyRepo.save(c);
    }

    private User user(String username, String password, String realName, String phone,
                      User.Role role, Long companyId, Long buildingId) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(passwordEncoder.encode(password));
        u.setRealName(realName);
        u.setPhone(phone);
        u.setRole(role);
        u.setCompanyId(companyId);
        u.setBuildingId(buildingId);
        u.setEnabled(true);
        return userRepo.save(u);
    }

    private Elevator elevator(String code, Building building, MaintenanceCompany company, int floors,
                              String doorZone, String brand, String model, String position,
                              LocalDate installDate, LocalDate nextInspection, Elevator.ElevatorStatus status) {
        Elevator e = new Elevator();
        e.setCode(code);
        e.setBuilding(building);
        e.setCompany(company);
        e.setFloors(floors);
        e.setDoorZone(doorZone);
        e.setBrand(brand);
        e.setModel(model);
        e.setPosition(position);
        e.setInstallDate(installDate);
        e.setNextInspectionDate(nextInspection);
        e.setStatus(status);
        return elevatorRepo.save(e);
    }

    private void inspection(Elevator e, LocalDate date, LocalDate next, AnnualInspection.InspectionResult result,
                            String inspector, String reportNo, String remark) {
        AnnualInspection i = new AnnualInspection();
        i.setElevator(e);
        i.setInspectionDate(date);
        i.setNextInspectionDate(next);
        i.setResult(result);
        i.setInspector(inspector);
        i.setReportNo(reportNo);
        i.setRemark(remark);
        inspectionRepo.save(i);
    }

    private void part(Elevator e, Long eventId, String partName, LocalDate date, String reason,
                      BigDecimal cost, String replacedBy) {
        PartReplacement p = new PartReplacement();
        p.setElevator(e);
        p.setEventId(eventId);
        p.setPartName(partName);
        p.setReplaceDate(date);
        p.setReason(reason);
        p.setCost(cost);
        p.setReplacedBy(replacedBy);
        partRepo.save(p);
    }

    /**
     * 构造一个完整闭环的历史事件（含通知、通话、时间线）。
     */
    private RescueEvent closedEvent(String eventNo, Elevator elevator, Building building,
                                    RescueEvent.AlarmSource source, LocalDateTime alarmTime,
                                    String floor, int passengers, int elderly, int children,
                                    long rescueMinutes, String doorOpenMethod, String faultCode,
                                    String passengerHealth, boolean medical,
                                    RescueEvent.Responsibility responsibility, String responsibilityDetail,
                                    RescueEvent.CostBearer costBearer, BigDecimal costAmount,
                                    String rectification, LocalDate rectificationDeadline, boolean rectificationDone,
                                    User handler, User maint, User sec, User butler, User fire,
                                    long arriveMinutes, boolean late, boolean repeatFault, boolean compensation) {
        RescueEvent e = new RescueEvent();
        e.setEventNo(eventNo);
        e.setElevator(elevator);
        e.setBuilding(building);
        e.setAlarmSource(source);
        e.setAlarmTime(alarmTime);
        e.setTrappedFloor(floor);
        e.setPassengerCount(passengers);
        e.setElderlyCount(elderly);
        e.setChildrenCount(children);
        e.setCallStatus(RescueEvent.CallStatus.SMOOTH);
        e.setDoorZone(elevator.getDoorZone());
        e.setReporterName("值班记录");
        e.setReportDetail("困人报警，被困 " + passengers + " 人");
        e.setHandler(handler);

        LocalDateTime notifiedAt = alarmTime.plusMinutes(3);
        e.setMaintenanceNotifiedAt(notifiedAt);
        e.setSecurityNotifiedAt(notifiedAt);
        if (butler != null) {
            e.setButlerNotifiedAt(notifiedAt);
        }
        if (fire != null) {
            e.setFireNotifiedAt(notifiedAt);
            e.setFireArrivedAt(alarmTime.plusMinutes(30));
        }
        e.setMaintenanceArrivedAt(alarmTime.plusMinutes(arriveMinutes));
        e.setReleasedAt(alarmTime.plusMinutes(rescueMinutes));
        e.setDoorOpenMethod(doorOpenMethod);
        e.setFaultCode(faultCode);
        e.setPassengerHealth(passengerHealth);
        e.setMedicalAssistance(medical);
        e.setMaintenanceLate(late);
        e.setRepeatFault(repeatFault);
        e.setCompensationRequested(compensation);
        e.setResetAt(alarmTime.plusMinutes(rescueMinutes + 40));
        e.setElevatorStopped(false);
        e.setRecheckedAt(alarmTime.plusMinutes(rescueMinutes + 60));
        e.setRecheckResult("复位后试运行正常，故障代码清除");
        e.setResponsibility(responsibility);
        e.setResponsibilityDetail(responsibilityDetail);
        e.setCostBearer(costBearer);
        e.setCostAmount(costAmount);
        e.setRectification(rectification);
        e.setRectificationDeadline(rectificationDeadline);
        e.setRectificationDone(rectificationDone);
        e.setOwnerNotified(true);
        e.setClosedAt(alarmTime.plusDays(1));
        e.setCloseRemark("处置完毕，资料归档");
        e.setStatus(RescueEvent.EventStatus.CLOSED);
        eventRepo.save(e);

        log(e, handler.getRealName(), "接警登记", "报警来源: " + source + "，被困 " + passengers + " 人", alarmTime);
        log(e, handler.getRealName(), "通知调度", "已通知维保单位、保安" + (butler != null ? "、楼栋管家" : "") + (fire != null ? "、消防救援" : ""), notifiedAt);
        notification(e, User.Role.MAINTENANCE, maint.getRealName(), maint.getPhone(), notifiedAt, true);
        notification(e, User.Role.SECURITY, sec.getRealName(), sec.getPhone(), notifiedAt, true);
        if (butler != null) {
            notification(e, User.Role.BUTLER, butler.getRealName(), butler.getPhone(), notifiedAt, true);
        }
        if (fire != null) {
            notification(e, User.Role.FIRE, fire.getRealName(), fire.getPhone(), notifiedAt, true);
        }
        call(e, handler, alarmTime.plusMinutes(8), "乘客情绪平稳", "安抚乘客，告知救援已启动");
        log(e, maint.getRealName(), "维保到场", "维保人员到达现场" + (late ? "（超时）" : ""), alarmTime.plusMinutes(arriveMinutes));
        log(e, maint.getRealName(), "困人释放", "乘客全部救出，救援历时 " + rescueMinutes + " 分钟", e.getReleasedAt());
        log(e, maint.getRealName(), "复位复检", "电梯复位，试运行正常", e.getResetAt());
        log(e, handler.getRealName(), "事件关闭", "责任判定完成，资料归档", e.getClosedAt());
        return e;
    }

    private void notification(RescueEvent e, User.Role role, String name, String phone,
                              LocalDateTime notifiedAt, boolean acked) {
        EventNotification n = new EventNotification();
        n.setEvent(e);
        n.setTargetRole(role);
        n.setTargetName(name);
        n.setTargetPhone(phone);
        n.setNotifiedAt(notifiedAt);
        n.setStatus(acked ? EventNotification.NotifyStatus.ACKED : EventNotification.NotifyStatus.SENT);
        if (acked) {
            n.setAcknowledgedAt(notifiedAt.plusMinutes(2));
        }
        notificationRepo.save(n);
    }

    private void call(RescueEvent e, User caller, LocalDateTime time, String state, String content) {
        call(e, caller, time, state, content, null, false, false, false, true);
    }

    private void call(RescueEvent e, User caller, LocalDateTime time, String state, String content,
                      String age, boolean panic, boolean heart, boolean pregnant, boolean stateClear) {
        EventCall c = new EventCall();
        c.setEvent(e);
        c.setCaller(caller);
        c.setCallTime(time);
        c.setPassengerState(state);
        c.setContent(content);
        c.setPassengerAge(age);
        c.setPanic(panic);
        c.setHeartDisease(heart);
        c.setPregnant(pregnant);
        c.setStateClear(stateClear);
        callRepo.save(c);
    }

    private void log(RescueEvent e, String actor, String action, String detail) {
        log(e, actor, action, detail, LocalDateTime.now());
    }

    private void log(RescueEvent e, String actor, String action, String detail, LocalDateTime time) {
        EventLog l = new EventLog();
        l.setEvent(e);
        l.setActorName(actor);
        l.setAction(action);
        l.setDetail(detail);
        l.setCreatedAt(time);
        logRepo.save(l);
    }

    private void complaint(Elevator e, Long eventId, String ownerName, String phone, String buildingName,
                           String content, OwnerComplaint.ComplaintStatus status, User handler, String result) {
        OwnerComplaint c = new OwnerComplaint();
        c.setElevator(e);
        c.setEventId(eventId);
        c.setOwnerName(ownerName);
        c.setOwnerPhone(phone);
        c.setBuildingName(buildingName);
        c.setContent(content);
        c.setStatus(status);
        c.setHandlerName(handler.getRealName());
        c.setResult(result);
        if (status != OwnerComplaint.ComplaintStatus.PENDING) {
            c.setHandledAt(LocalDateTime.now().minusDays(2));
        }
        complaintRepo.save(c);
    }

    /** 触发事件快照条目（与 RectificationService 申请时固化的结构一致） */
    private Map<String, Object> snapshotEvent(RescueEvent e) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", e.getId());
        item.put("eventNo", e.getEventNo());
        item.put("faultCode", e.getFaultCode());
        item.put("alarmTime", e.getAlarmTime() != null ? e.getAlarmTime().toString() : null);
        item.put("status", e.getStatus() != null ? e.getStatus().name() : null);
        return item;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("种子数据快照序列化失败", e);
        }
    }

    private void duty(LocalDate date, User user, DutySchedule.Shift shift, String position) {
        DutySchedule d = new DutySchedule();
        d.setDutyDate(date);
        d.setUser(user);
        d.setShift(shift);
        d.setPosition(position);
        dutyRepo.save(d);
    }

    private void notice(Building building, Long eventId, BuildingNotice.NoticeType type,
                        String title, String content, User publisher) {
        BuildingNotice n = new BuildingNotice();
        n.setBuilding(building);
        n.setEventId(eventId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        n.setPublisherName(publisher.getRealName());
        n.setPublishedAt(LocalDateTime.now().minusDays(3));
        noticeRepo.save(n);
    }

    private void followup(RescueEvent e, String ownerName, String phone, LocalDateTime time,
                          int satisfaction, String feedback, User follower) {
        OwnerFollowup f = new OwnerFollowup();
        f.setEvent(e);
        f.setOwnerName(ownerName);
        f.setOwnerPhone(phone);
        f.setFollowupTime(time);
        f.setSatisfaction(satisfaction);
        f.setFeedback(feedback);
        f.setFollowerName(follower.getRealName());
        followupRepo.save(f);
    }

    private void assistance(Building building, Elevator elevator, String residentName, String roomNo,
                            String phone, String need, String helperName, String helperPhone, boolean active) {
        ElderlyAssistance a = new ElderlyAssistance();
        a.setBuilding(building);
        a.setElevator(elevator);
        a.setResidentName(residentName);
        a.setRoomNo(roomNo);
        a.setPhone(phone);
        a.setNeedDescription(need);
        a.setHelperName(helperName);
        a.setHelperPhone(helperPhone);
        a.setStatus(active ? ElderlyAssistance.AssistanceStatus.ACTIVE : ElderlyAssistance.AssistanceStatus.RESOLVED);
        if (!active) {
            a.setResolvedAt(LocalDateTime.now().minusHours(6));
        }
        assistanceRepo.save(a);
    }
}
