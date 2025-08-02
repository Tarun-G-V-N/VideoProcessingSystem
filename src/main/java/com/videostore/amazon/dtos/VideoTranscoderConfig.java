package com.videostore.amazon.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import software.amazon.awssdk.regions.Region;

import java.util.Map;

@AllArgsConstructor
@Getter
public class VideoTranscoderConfig {
    private Region region;
    private String roleArn;
    private String inputFilePath;
    private String outputFilePath;
    private Map<String, String> userMetadata;
}
