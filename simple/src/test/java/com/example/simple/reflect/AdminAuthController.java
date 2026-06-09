package com.example.simple.reflect;

import org.byteora.kyra.core.annotation.Reflect;

public final class AdminAuthController {
    private AdminAuthController() {
    }

    @Reflect
    public record LoginRequest(String username) {
    }
}
