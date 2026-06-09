package com.example.simple.sample.reflect;

import org.byteora.kyra.core.annotation.Reflect;

/** Sample: nested record with the same simple name as {@link PlatformAuthController.LoginRequest}. */
public final class AdminAuthController {
    private AdminAuthController() {
    }

    @Reflect
    public record LoginRequest(String username) {
    }
}
