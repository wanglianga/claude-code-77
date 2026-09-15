package com.elevator.rescue.controller;

import com.elevator.rescue.entity.User;
import com.elevator.rescue.repository.UserRepository;
import com.elevator.rescue.security.JwtUtil;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public record LoginRequest(@NotBlank(message = "请输入用户名") String username,
                               @NotBlank(message = "请输入密码") String password) {
    }

    public record LoginResponse(String token, User user) {
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        User user = userRepository.findByUsername(req.username()).orElseThrow();
        String token = jwtUtil.generate(user.getUsername(), user.getRole().name());
        return new LoginResponse(token, user);
    }

    @GetMapping("/me")
    public User me(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName()).orElseThrow();
    }
}
