package org.byteora.kyra.spring.boot.autoconfigure.collision;

import org.byteora.kyra.orm.annotation.KyraScan;

@KyraScan(
        entity = {
                "org.byteora.kyra.spring.boot.autoconfigure.collision.left",
                "org.byteora.kyra.spring.boot.autoconfigure.collision.right"
        },
        mapper = {
                "org.byteora.kyra.spring.boot.autoconfigure.collision.left",
                "org.byteora.kyra.spring.boot.autoconfigure.collision.right"
        }
)
public class CollisionMapperKyraConfig {
}
