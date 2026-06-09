package org.byteora.kyra.quarkus.deployment.test.mapper;

import org.byteora.kyra.quarkus.deployment.test.entity.TestUser;

public interface TestUserMapper {
    TestUser selectById(Long id);
}
