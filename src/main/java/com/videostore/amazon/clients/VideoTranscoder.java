package com.videostore.amazon.clients;

import com.videostore.amazon.dtos.Resolution;
import com.videostore.amazon.dtos.VideoTranscoderConfig;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.mediaconvert.MediaConvertClient;
import software.amazon.awssdk.services.mediaconvert.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VideoTranscoder {
    private final List<Resolution> resolutions;
    private final VideoTranscoderConfig config;
    private static final MediaConvertClient mediaConvertClient = MediaConvertClient.builder()
            .region(Region.of(System.getenv("REGION")))
            .build();

    public VideoTranscoder(VideoTranscoderConfig config) {
        this.resolutions = new ArrayList<>();
        this.config = config;
    }

    public void addResolution(Resolution resolution) {
        this.resolutions.add(resolution);
    }

    public CreateJobResponse convert() {
        TimecodeConfig timecodeConfig = TimecodeConfig.builder()
                .source("ZEROBASED")
                .build();

        Input input = Input.builder()
                .audioSelectors(Map.of("Audio Selector 1", AudioSelector.builder().defaultSelection("DEFAULT").build()))
                .videoSelector(VideoSelector.builder().build())
                .timecodeSource("ZEROBASED")
                .fileInput(config.getInputFilePath())
                .build();

        OutputGroup outputGroup = OutputGroup.builder()
                .outputGroupSettings(OutputGroupSettings.builder()
                        .type("FILE_GROUP_SETTINGS")
                        .fileGroupSettings(FileGroupSettings.builder()
                                .destination(config.getOutputFilePath()).build()).build())
                .outputs(resolutions.stream().map(
                        resolution -> createOutput(
                                resolution.getWidth(),
                                resolution.getHeight(),
                                resolution.getBitrate(),
                                resolution.getFileExtension(),
                                resolution.getNameExtension())).toList())
                .build();

        JobSettings jobSettings = JobSettings.builder()
                .inputs(List.of(input))
                .outputGroups(List.of(outputGroup))
                .timecodeConfig(timecodeConfig)
                .build();

        CreateJobRequest createJobRequest = CreateJobRequest.builder().role(config.getRoleArn())
                .userMetadata(config.getUserMetadata())
                .settings(jobSettings)
                .build();

        return mediaConvertClient.createJob(createJobRequest);
    }

    private Output createOutput(int width, int height, int bitrate, String fileExtension, String nameExtension) {
        return Output.builder().containerSettings(ContainerSettings.builder().container(ContainerType.MP4).mp4Settings(Mp4Settings.builder().build()).build())
                .videoDescription(VideoDescription.builder().codecSettings(VideoCodecSettings.builder().codec(VideoCodec.H_264).h264Settings(H264Settings.builder().bitrate(bitrate != 0 ? bitrate : 500000).rateControlMode(String.valueOf(H264RateControlMode.CBR)).build()).build()).height(height).width(width).build())
                .extension(fileExtension != null ? fileExtension : "mp4")
                .nameModifier(nameExtension != null ? nameExtension : "_" + width + "x" + height).build();
    }
}
