package com.quantai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.quantai.common.BusinessException;
import com.quantai.dto.LoginRequest;
import com.quantai.dto.LoginResponse;
import com.quantai.dto.RegisterRequest;
import com.quantai.entity.User;
import com.quantai.mapper.UserMapper;
import com.quantai.security.JwtTokenProvider;
import com.quantai.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Value("${jwt.expiration:7200000}")
    private long jwtExpirationMs;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, request.getUsername());

        if (userMapper.selectCount(queryWrapper) > 0) {
            throw new BusinessException("用户名已存在");
        }

        if (request.getEmail() != null) {
            queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(User::getEmail, request.getEmail());
            if (userMapper.selectCount(queryWrapper) > 0) {
                throw new BusinessException("邮箱已被注册");
            }
        }

        User user = User.builder()
                .username(request.getUsername())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .phone(request.getPhone())
                .role("USER")
                .status("ACTIVE")
                .apiQuotaDaily(1000)
                .apiUsedToday(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        userMapper.insert(user);
        log.info("用户注册成功: {}", request.getUsername());
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, request.getUsername());
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            throw new BusinessException("用户名或密码错误");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("账号已被禁用或锁定");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("用户名或密码错误");
        }

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        String accessToken = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        log.info("用户登录成功: {}", request.getUsername());

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }

    @Override
    public LoginResponse refreshToken(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BusinessException("Refresh Token无效或已过期");
        }

        Long userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userMapper.selectById(userId);

        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException("用户状态异常");
        }

        String newAccessToken = tokenProvider.generateToken(user.getId(), user.getUsername(), user.getRole());
        String newRefreshToken = tokenProvider.generateRefreshToken(user.getId(), user.getUsername());

        return LoginResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtExpirationMs / 1000)
                .userId(user.getId())
                .username(user.getUsername())
                .role(user.getRole())
                .build();
    }
}
