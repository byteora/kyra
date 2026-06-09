package com.example.simple.entity;

import com.example.simple.TestAnnotation;
import org.byteora.kyra.orm.annotation.ID;
import org.byteora.kyra.orm.annotation.IdStrategy;
import org.byteora.kyra.core.annotation.Reflect;
import org.byteora.kyra.core.annotation.ReflectMetadataLevel;
import lombok.Data;

@Data
//@Reflect(annotationMetadata = true)
public class BaseUser<T> {
    @TestAnnotation(value = "userId",size = 3)
    @ID(strategy = IdStrategy.CUSTOM)
    private Long id;

    private T tag;

    public String describeId(String prefix) {
        return prefix + this.id;
    }
}
