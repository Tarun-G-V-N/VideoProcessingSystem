package com.videostore.amazon.clients;

import com.amazonaws.services.lambda.runtime.Context;
import com.videostore.amazon.dtos.MediaInfo;
import com.videostore.amazon.utiles.Mapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class VideoMetadataExtractor {
    public static MediaInfo extract(String url, Context context) {
        ProcessBuilder processBuilder = new ProcessBuilder("/var/task/mediainfo", "--output=JSON", url);
        Process process;
        StringBuilder jsonOutput = new StringBuilder();
        try {
            process = processBuilder.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonOutput.append(line);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return Mapper.mapToVideoMetadata(jsonOutput.toString(), context);
    }
}
