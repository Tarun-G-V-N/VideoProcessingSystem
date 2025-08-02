package com.videostore.amazon.utiles;

import com.amazonaws.services.lambda.runtime.Context;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.videostore.amazon.dtos.MediaConvertEvent;
import com.videostore.amazon.dtos.MediaInfo;
import com.videostore.amazon.dtos.VideoDTO;
import com.videostore.amazon.entites.Video;

public class Mapper {
    public static VideoDTO mapToVideoDTO(String body, Context context) {
        VideoDTO videoDTO = null;
        try {
            videoDTO = new ObjectMapper().readValue(body, VideoDTO.class);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting API request to VideoDTO: "+e.getMessage());
        }
        return videoDTO;
    }

    public static Video mapToVideo(String body, Context context) {
        Video video = null;
        try {
            video = new ObjectMapper().readValue(body, Video.class);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting API request to VideoDTO: "+e.getMessage());
        }
        return video;
    }

    public static MediaInfo mapToVideoMetadata(String body, Context context) {
        MediaInfo videoMetadata = null;
        try {
            videoMetadata = new ObjectMapper().readValue(body, MediaInfo.class);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting API request to VideoMetadataDTO: "+e.getMessage());
        }
        return videoMetadata;
    }

    public static String mapToString(Object object, Context context) {
        String jsonString = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());
            jsonString = objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting object to String: "+e.getMessage());
        }
        return jsonString;
    }

    public static MediaConvertEvent mapToMediaConvertEvent(Object body, Context context) {
        MediaConvertEvent mediaConvertEvent = null;
        try {
            String json = new ObjectMapper().writeValueAsString(body);
            mediaConvertEvent = new ObjectMapper().readValue(json, MediaConvertEvent.class);
        } catch (JsonProcessingException e) {
            context.getLogger().log("Error while converting API request to MediaConvertEvent: "+e.getMessage());
        }
        return mediaConvertEvent;
    }
}
