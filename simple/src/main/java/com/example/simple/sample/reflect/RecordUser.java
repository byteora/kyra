package com.example.simple.sample.reflect;

import org.byteora.kyra.core.annotation.Reflect;

/** Sample: top-level record with {@link Reflect}. */
@Reflect
public record RecordUser(String name, int age) {
}
