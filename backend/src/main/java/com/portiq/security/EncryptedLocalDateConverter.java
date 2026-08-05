package com.portiq.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDate;

@Converter(autoApply = false)
public class EncryptedLocalDateConverter implements AttributeConverter<LocalDate, String> {

    private final EncryptedStringConverter delegate = new EncryptedStringConverter();

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        return attribute == null ? null : delegate.convertToDatabaseColumn(attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        String plain = delegate.convertToEntityAttribute(dbData);
        return plain == null ? null : LocalDate.parse(plain);
    }
}
