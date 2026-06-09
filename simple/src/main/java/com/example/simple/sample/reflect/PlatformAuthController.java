package com.example.simple.sample.reflect;

import org.byteora.kyra.core.annotation.Reflect;

/** Sample: nested record inside a controller-style outer class. */
public final class PlatformAuthController {
    private PlatformAuthController() {
    }

    @Reflect
    public record LoginRequest(String username, String password) {
    }
}
