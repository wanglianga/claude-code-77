package com.elevator.rescue.controller;

import com.elevator.rescue.entity.User;
import com.elevator.rescue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    /** 各端公用：获取可通知人员清单（值班/维保/保安/管家/消防） */
    @GetMapping("/contacts")
    public List<User> contacts() {
        return userRepo.findAll().stream()
                .filter(u -> u.getRole() != User.Role.ADMIN && Boolean.TRUE.equals(u.getEnabled()))
                .toList();
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<User> list() {
        return userRepo.findAll();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public User create(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        userRepo.findByUsername(username).ifPresent(u -> {
            throw new IllegalArgumentException("用户名已存在: " + username);
        });
        User user = new User();
        user.setUsername(username);
        String password = (String) body.get("password");
        user.setPassword(passwordEncoder.encode(password != null && !password.isBlank() ? password : "123456"));
        user.setRealName((String) body.get("realName"));
        user.setPhone((String) body.get("phone"));
        user.setRole(User.Role.valueOf((String) body.get("role")));
        Object companyId = body.get("companyId");
        user.setCompanyId(companyId != null ? Long.valueOf(companyId.toString()) : null);
        Object buildingId = body.get("buildingId");
        user.setBuildingId(buildingId != null ? Long.valueOf(buildingId.toString()) : null);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        return userRepo.save(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public User update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        User user = userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (body.get("realName") != null) {
            user.setRealName((String) body.get("realName"));
        }
        if (body.get("phone") != null) {
            user.setPhone((String) body.get("phone"));
        }
        if (body.get("role") != null) {
            user.setRole(User.Role.valueOf((String) body.get("role")));
        }
        if (body.containsKey("companyId")) {
            Object companyId = body.get("companyId");
            user.setCompanyId(companyId != null ? Long.valueOf(companyId.toString()) : null);
        }
        if (body.containsKey("buildingId")) {
            Object buildingId = body.get("buildingId");
            user.setBuildingId(buildingId != null ? Long.valueOf(buildingId.toString()) : null);
        }
        String password = (String) body.get("password");
        if (password != null && !password.isBlank()) {
            user.setPassword(passwordEncoder.encode(password));
        }
        return userRepo.save(user);
    }

    @PutMapping("/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public User toggle(@PathVariable Long id) {
        User user = userRepo.findById(id).orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        user.setEnabled(!Boolean.TRUE.equals(user.getEnabled()));
        return userRepo.save(user);
    }
}
