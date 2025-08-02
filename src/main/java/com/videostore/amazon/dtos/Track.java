package com.videostore.amazon.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class Track {
    @JsonProperty("@type")
    private String type;

    @JsonProperty("FileSize")
    private int fileSize;

    @JsonProperty("Width")
    private int width;

    @JsonProperty("Height")
    private int height;
}
