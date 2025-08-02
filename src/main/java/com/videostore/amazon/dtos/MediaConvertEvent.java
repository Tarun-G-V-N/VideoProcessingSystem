package com.videostore.amazon.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MediaConvertEvent {
    private String version;
    private String id;
    @JsonProperty("detail-type")
    private String detailType;
    private String source;
    private String account;
    private String time;
    private String region;
    private List<String> resources;
    private Detail detail;
}

