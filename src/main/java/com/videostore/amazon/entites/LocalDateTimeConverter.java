package com.videostore.amazon.entites;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class LocalDateTimeConverter implements DynamoDBTypeConverter<Long, LocalDateTime> {
    @Override
    public Long convert(LocalDateTime localDateTime) {
        return localDateTime.toEpochSecond(ZoneOffset.UTC);
    }

    @Override
    public LocalDateTime unconvert(Long epochSecond) {
        return LocalDateTime.ofEpochSecond(epochSecond, 0, ZoneOffset.UTC);
    }
}
