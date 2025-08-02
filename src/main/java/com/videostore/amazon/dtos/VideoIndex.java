package com.videostore.amazon.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class VideoIndex {
    private String title;
    private String description;
    private List<String> tags;
}
