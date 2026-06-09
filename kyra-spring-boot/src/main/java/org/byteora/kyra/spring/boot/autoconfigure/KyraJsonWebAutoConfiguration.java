package org.byteora.kyra.spring.boot.autoconfigure;

import org.byteora.kyra.json.JsonMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.converter.HttpMessageConverter;

@AutoConfiguration
@ConditionalOnClass(HttpMessageConverter.class)
public class KyraJsonWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public JsonMapper kyraJsonMapper() {
        return JsonMapper.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean(KyraJsonHttpMessageConverter.class)
    public KyraJsonHttpMessageConverter kyraJsonHttpMessageConverter(JsonMapper jsonMapper) {
        return new KyraJsonHttpMessageConverter(jsonMapper);
    }
}
