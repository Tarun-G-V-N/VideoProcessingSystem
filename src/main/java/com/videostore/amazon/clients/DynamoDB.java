package com.videostore.amazon.clients;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;

public final class DynamoDB {
    private static final AmazonDynamoDB dynamoDBClient = AmazonDynamoDBClientBuilder.standard().build();
    private static final String tableName = System.getenv("TABLE_NAME");
    private static final DynamoDBMapperConfig config = DynamoDBMapperConfig.builder()
            .withTableNameOverride(DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(tableName))
            .build();
    public static final DynamoDBMapper dynamoDBMapper = new DynamoDBMapper(dynamoDBClient, config);
}
