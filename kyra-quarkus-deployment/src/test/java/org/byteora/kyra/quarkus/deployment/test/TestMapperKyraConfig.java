package org.byteora.kyra.quarkus.deployment.test.config;

import org.byteora.kyra.orm.annotation.KyraScan;

@KyraScan(
        entity = {"org.byteora.kyra.quarkus.deployment.test.entity"},
        mapper = {"org.byteora.kyra.quarkus.deployment.test.mapper"}
)
public class TestMapperKyraConfig {
}
