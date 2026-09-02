package com.quantai.service;

import com.quantai.dto.LoginRequest;
import com.quantai.dto.LoginResponse;
import com.quantai.dto.RegisterRequest;

public interface AuthService {

    void register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    LoginResponse refreshToken(String refreshToken);
}
