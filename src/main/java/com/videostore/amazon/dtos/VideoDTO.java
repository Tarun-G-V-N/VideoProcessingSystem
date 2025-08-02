package com.videostore.amazon.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VideoDTO {
    private String id;
    @NotEmpty
    private String userId;
    @NotEmpty
    private String title;
    @NotEmpty
    private String description;
    private List<String> tags;
    private Map<String, String> transcodeFiles;
}
