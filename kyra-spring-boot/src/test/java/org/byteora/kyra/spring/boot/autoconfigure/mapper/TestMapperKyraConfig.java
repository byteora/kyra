package org.byteora.kyra.spring.boot.autoconfigure.mapper;

import org.byteora.kyra.orm.annotation.KyraScan;

@KyraScan(
        entity = {"org.byteora.kyra.spring.boot.autoconfigure.mapper"},
        mapper = {"org.byteora.kyra.spring.boot.autoconfigure.mapper"}
)
public class TestMapperKyraConfig {
}
