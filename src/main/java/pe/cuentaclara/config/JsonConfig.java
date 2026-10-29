package pe.cuentaclara.config;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.databind.type.LogicalType;

import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;

@Configuration
public class JsonConfig {
    @Bean
    Jackson2ObjectMapperBuilderCustomizer financialJson() {
        return builder -> {
            builder.serializerByType(BigDecimal.class, ToStringSerializer.instance);
            builder.postConfigurer(
                    mapper -> {
                        mapper.coercionConfigFor(LogicalType.Integer)
                                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                        mapper.getFactory()
                                .setStreamReadConstraints(
                                        StreamReadConstraints.builder()
                                                .maxNestingDepth(40)
                                                .maxNumberLength(40)
                                                .maxStringLength(131072)
                                                .build());
                    });
        };
    }
}
