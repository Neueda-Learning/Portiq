package com.portiq.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.math.BigDecimal;

@Converter(autoApply = false)
public class EncryptedBigDecimalConverter implements AttributeConverter<BigDecimal, String> {

    private final EncryptedStringConverter delegate = new EncryptedStringConverter();

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        return attribute == null ? null : delegate.convertToDatabaseColumn(attribute.toPlainString());
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        String plain = delegate.convertToEntityAttribute(dbData);
        return plain == null ? null : new BigDecimal(plain);
    }
}
