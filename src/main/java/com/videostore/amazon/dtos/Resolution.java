package com.videostore.amazon.dtos;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Resolution {
    private int width;
    private int height;
    private int bitrate;
    private String fileExtension;
    private String nameExtension;
}
