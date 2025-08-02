package com.videostore.amazon.lambdas;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.videostore.amazon.clients.DynamoDB;
import com.videostore.amazon.clients.S3;
import com.videostore.amazon.dtos.*;
import com.videostore.amazon.entites.Video;
import com.videostore.amazon.entites.VideoStatus;
import com.videostore.amazon.clients.VideoTranscoder;
import com.videostore.amazon.clients.VideoMetadataExtractor;
import software.amazon.awssdk.regions.Region;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class S3Handler implements RequestHandler<S3Event, String> {
    private final DynamoDBMapper dynamoDBMapper = DynamoDB.dynamoDBMapper;
    private final S3 s3 = new S3();

    @Override
    public String handleRequest(S3Event event, Context context) {
        if (event.getRecords() == null || event.getRecords().isEmpty()) {
            return "No records found.";
        }
        String objectKey = event.getRecords().get(0).getS3().getObject().getKey();
        updateRecord(objectKey, context);
        analyzeVideo(objectKey, context);
        return "Updated record with ID: " + objectKey;
    }

    private void updateRecord(String id, Context context) {
        Video video = dynamoDBMapper.load(Video.class, id);
        if(video == null)  {
            throw new RuntimeException("Video not found with ID: " + id);
        }
        video.setStatus(VideoStatus.Uploaded);
        video.setUpdatedTime(LocalDateTime.now());
        dynamoDBMapper.save(video);
        context.getLogger().log("Updated the status of the record with ID: " + id);
    }

    private void analyzeVideo(String objectKey, Context context) {
        String presignedDownloadUrl = s3.getPresignedDownloadUrl(objectKey, context);
        MediaInfo videoMetadata = VideoMetadataExtractor.extract(presignedDownloadUrl, context);
        VideoTranscoderConfig config = new VideoTranscoderConfig(Region.of(System.getenv("REGION")), System.getenv("MEDIA_CONVERT_ROLE_ARN"), "s3://"+System.getenv("UPLOAD_BUCKET_NAME")+"/"+objectKey, "s3://"+System.getenv("STREAM_BUCKET_NAME")+"/"+objectKey, Map.of("id", objectKey));
        VideoTranscoder videoTranscoder = new VideoTranscoder(config);
        Video video = dynamoDBMapper.load(Video.class, objectKey);
        if(video == null)  {
            throw new RuntimeException("Video not found with ID: " + objectKey);
        }
        Media media = videoMetadata.getMedia();
        List<Track> tracks = media.getTrack();
        for (Track track : tracks) {
            if(track.getType().equals("General")) {
                context.getLogger().log("Track Type: "+track.getType());
                context.getLogger().log(String.valueOf("File Size: "+track.getFileSize()));
            } else if (track.getType().equals("Video")) {
                context.getLogger().log("Track Type: "+track.getType());
                context.getLogger().log(String.valueOf("Width: "+track.getWidth()));
                context.getLogger().log(String.valueOf("Height: "+track.getHeight()));
                video.setTranscodeFiles(new HashMap<>());
                if (track.getWidth() >= 1280 && track.getHeight() >= 720) {
                    videoTranscoder.addResolution(new Resolution(1280, 720, 500000, ".mp4", "_1280x720"));
                    video.getTranscodeFiles().put("1280x720", "https://"+System.getenv("STREAM_BUCKET_NAME")+".s3"+"."+System.getenv("REGION")+".amazonaws.com/"+objectKey+"_1280x720.mp4");
                }
                if (track.getWidth() >= 640 && track.getHeight() >= 360) {
                    videoTranscoder.addResolution(new Resolution(640, 360, 500000, ".mp4", "_640x360"));
                    video.getTranscodeFiles().put("640x360", "https://"+System.getenv("STREAM_BUCKET_NAME")+".s3"+"."+System.getenv("REGION")+".amazonaws.com/"+objectKey+"_640x360.mp4");
                }
                if(track.getWidth() >= 480 && track.getHeight() >= 270) {
                    videoTranscoder.addResolution(new Resolution(480, 270, 100000, ".mp4", "_480x270"));
                    video.getTranscodeFiles().put("480x270", "https://"+System.getenv("STREAM_BUCKET_NAME")+".s3"+"."+System.getenv("REGION")+".amazonaws.com/"+objectKey+"_480x270.mp4");
                }
                videoTranscoder.addResolution(new Resolution(320, 180, 100000, ".mp4", "_320x180"));
                video.getTranscodeFiles().put("320x180", "https://"+System.getenv("STREAM_BUCKET_NAME")+".s3"+"."+System.getenv("REGION")+".amazonaws.com/"+objectKey+"_320x180.mp4");
                videoTranscoder.convert();
                dynamoDBMapper.save(video);
            }
        }
    }
}
