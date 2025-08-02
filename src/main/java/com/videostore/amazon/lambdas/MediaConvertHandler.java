package com.videostore.amazon.lambdas;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.videostore.amazon.clients.DynamoDB;
import com.videostore.amazon.clients.OpenSearch;
import com.videostore.amazon.clients.S3;
import com.videostore.amazon.dtos.MediaConvertEvent;
import com.videostore.amazon.dtos.VideoIndex;
import com.videostore.amazon.entites.Video;
import com.videostore.amazon.entites.VideoStatus;
import com.videostore.amazon.utiles.Mapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;

import java.io.IOException;
import java.time.LocalDateTime;

public class MediaConvertHandler implements RequestHandler<Object, String> {
    private final DynamoDBMapper dynamoDBMapper = DynamoDB.dynamoDBMapper;
    private final OpenSearch openSearch = new OpenSearch((String) System.getenv("OPEN_SEARCH_ENDPOINT"), System.getenv("REGION"));
    private final OpenSearchClient openSearchClient = openSearch.getClient();
    private final S3 s3 = new S3();

    @Override
    public String handleRequest(Object input, Context context) {
        MediaConvertEvent mediaConvertEvent = Mapper.mapToMediaConvertEvent(input, context);
        String videoId = mediaConvertEvent.getDetail().getUserMetadata().get("id");
        if(videoId == null) throw new RuntimeException("No ID found in the metadata: " +videoId);
        Video video = dynamoDBMapper.load(Video.class, mediaConvertEvent.getDetail().getUserMetadata().get("id"));
        if(video == null)  throw new RuntimeException("Video not found with ID: " +videoId);

        switch (mediaConvertEvent.getDetail().getStatus()) {
            case "COMPLETE" -> {
                video.setStatus(VideoStatus.Ready);
                video.setUpdatedTime(LocalDateTime.now());
                dynamoDBMapper.save(video);
                VideoIndex videoIndex = new VideoIndex(video.getTitle(), video.getDescription(), video.getTags());
                IndexRequest<VideoIndex> indexRequest = new IndexRequest.Builder<VideoIndex>().index("video").id(videoId).document(videoIndex).build();
                try {
                    openSearchClient.index(indexRequest);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                s3.deleteObject(videoId, context);
                context.getLogger().log("Updated the status of the record with ID: " + videoId);
            }
            case "PROGRESSING" -> {
                video.setStatus(VideoStatus.Processing);
                video.setUpdatedTime(LocalDateTime.now());
                dynamoDBMapper.save(video);
                context.getLogger().log("Updated the status of the record with ID: " + videoId);
            }
            case "ERROR" -> {
                video.setStatus(VideoStatus.Error);
                video.setUpdatedTime(LocalDateTime.now());
                dynamoDBMapper.save(video);
                s3.deleteObject(videoId, context);
                context.getLogger().log("Updated the status of the record with ID: " + videoId);
            }
        }
        return "";
    }
}
