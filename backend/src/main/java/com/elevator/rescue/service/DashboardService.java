package com.elevator.rescue.service;

import com.elevator.rescue.entity.Elevator;
import com.elevator.rescue.entity.MaintenanceCompany;
import com.elevator.rescue.entity.RescueEvent;
import com.elevator.rescue.repository.ElevatorRepository;
import com.elevator.rescue.repository.MaintenanceCompanyRepository;
import com.elevator.rescue.repository.RescueEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final RescueEventRepository eventRepo;
    private final ElevatorRepository elevatorRepo;
    private final MaintenanceCompanyRepository companyRepo;

    @Transactional(readOnly = true)
    public Map<String, Object> stats() {
        List<RescueEvent> all = eventRepo.findAllByOrderByAlarmTimeDesc();
        List<Elevator> elevators = elevatorRepo.findAll();
        LocalDateTime now = LocalDateTime.now();

        Map<String, Object> result = new LinkedHashMap<>();

        // 总览
        long active = all.stream().filter(e -> e.getStatus() != RescueEvent.EventStatus.CLOSED).count();
        long today = all.stream().filter(e -> e.getAlarmTime() != null
                && e.getAlarmTime().toLocalDate().equals(LocalDate.now())).count();
        List<RescueEvent> released = all.stream().filter(e -> e.getReleasedAt() != null && e.getAlarmTime() != null).toList();
        double avgMinutes = released.stream()
                .mapToLong(e -> Duration.between(e.getAlarmTime(), e.getReleasedAt()).toMinutes())
                .average().orElse(0);
        long lateCount = all.stream().filter(e -> Boolean.TRUE.equals(e.getMaintenanceLate())).count();
        long repeatCount = all.stream().filter(e -> Boolean.TRUE.equals(e.getRepeatFault())).count();
        long compensationCount = all.stream().filter(e -> Boolean.TRUE.equals(e.getCompensationRequested())).count();

        result.put("totalEvents", all.size());
        result.put("activeEvents", active);
        result.put("todayEvents", today);
        result.put("avgRescueMinutes", Math.round(avgMinutes * 10.0) / 10.0);
        result.put("lateCount", lateCount);
        result.put("repeatCount", repeatCount);
        result.put("compensationCount", compensationCount);
        result.put("stoppedElevators", elevators.stream()
                .filter(el -> el.getStatus() == Elevator.ElevatorStatus.STOPPED).count());
        result.put("totalElevators", elevators.size());

        // 事件状态分布
        Map<String, Long> byStatus = all.stream().collect(Collectors.groupingBy(
                e -> e.getStatus().name(), LinkedHashMap::new, Collectors.counting()));
        result.put("eventsByStatus", byStatus);

        // 近 7 天趋势
        List<Map<String, Object>> last7Days = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MM-dd");
        for (int i = 6; i >= 0; i--) {
            LocalDate day = LocalDate.now().minusDays(i);
            long count = all.stream().filter(e -> e.getAlarmTime() != null
                    && e.getAlarmTime().toLocalDate().equals(day)).count();
            Map<String, Object> item = new HashMap<>();
            item.put("date", day.format(fmt));
            item.put("count", count);
            last7Days.add(item);
        }
        result.put("eventsLast7Days", last7Days);

        // 维保单位考核
        List<Map<String, Object>> companyStats = new ArrayList<>();
        for (MaintenanceCompany company : companyRepo.findAll()) {
            List<RescueEvent> companyEvents = all.stream()
                    .filter(e -> e.getElevator().getCompany() != null
                            && e.getElevator().getCompany().getId().equals(company.getId()))
                    .toList();
            double avg = companyEvents.stream()
                    .filter(e -> e.getReleasedAt() != null && e.getAlarmTime() != null)
                    .mapToLong(e -> Duration.between(e.getAlarmTime(), e.getReleasedAt()).toMinutes())
                    .average().orElse(0);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", company.getId());
            item.put("name", company.getName());
            item.put("eventCount", companyEvents.size());
            item.put("lateCount", companyEvents.stream().filter(e -> Boolean.TRUE.equals(e.getMaintenanceLate())).count());
            item.put("avgRescueMinutes", Math.round(avg * 10.0) / 10.0);
            item.put("penaltyCount", company.getPenaltyCount());
            item.put("creditScore", company.getCreditScore());
            companyStats.add(item);
        }
        result.put("companyStats", companyStats);

        // 反复故障电梯（90 天内 >=2 次）
        List<Map<String, Object>> repeatElevators = new ArrayList<>();
        for (Elevator elevator : elevators) {
            long count = eventRepo.countByElevatorIdAndAlarmTimeAfter(elevator.getId(), now.minusDays(90));
            if (count >= 2) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("code", elevator.getCode());
                item.put("building", elevator.getBuilding().getName());
                item.put("count", count);
                item.put("status", elevator.getStatus().name());
                repeatElevators.add(item);
            }
        }
        result.put("repeatFaultElevators", repeatElevators);

        // 最新事件
        result.put("recentEvents", all.stream().limit(6).toList());

        return result;
    }
}
