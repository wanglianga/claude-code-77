package com.elevator.rescue.security;

import com.elevator.rescue.entity.User;
import com.elevator.rescue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthHelper {

    private final UserRepository userRepository;

    public User requireUser(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("当前用户不存在"));
    }
}
