package com.example.simple.sample.inheritance;

import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.annotation.ReflectMetadataLevel;

/** Sample: subclass reflector merges parent metadata and members. */
@Reflect(metadata = ReflectMetadataLevel.METHOD, annotationMetadata = true)
@TestReflectTag("admin")
public class AdminUser extends BaseUser {
    private String role;

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
