package com.videostore.amazon.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;

import java.util.Map;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Detail {
    private String status;
    private String jobId;
    private long timestamp;
    private String accountId;
    private String queue;
    private Map<String, String> userMetadata;
}
