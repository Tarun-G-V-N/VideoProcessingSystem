package com.videostore.amazon.services;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.lambda.runtime.Context;
import com.videostore.amazon.clients.DynamoDB;
import com.videostore.amazon.clients.OpenSearch;
import com.videostore.amazon.clients.S3;
import com.videostore.amazon.dtos.VideoDTO;
import com.videostore.amazon.dtos.VideoIndex;
import com.videostore.amazon.entites.Video;
import com.videostore.amazon.entites.VideoStatus;
import com.videostore.amazon.utiles.Mapper;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.IndexRequest;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class VideoService {
    private final DynamoDBMapper dynamoDBMapper;
    private final OpenSearch openSearch;
    private OpenSearchClient openSearchClient;
    private final S3 S3;

    public VideoService() {
        this.dynamoDBMapper = DynamoDB.dynamoDBMapper;
        this.openSearch = new OpenSearch((String) System.getenv("OPEN_SEARCH_ENDPOINT"), System.getenv("REGION"));
        this.openSearchClient = openSearch.getClient();
        this.S3 = new S3();
    }

    public VideoService(DynamoDBMapper dynamoDBMapper, OpenSearch openSearch, S3 S3) {
        this.dynamoDBMapper = dynamoDBMapper;
        this.openSearch = openSearch;
        this.S3 = S3;
    }

    public String saveVideo(VideoDTO videoDTO, Context context) {
        Video video = new Video();
        video.setUserId(videoDTO.getUserId());
        video.setTitle(videoDTO.getTitle());
        video.setDescription(videoDTO.getDescription());
        video.setTags(videoDTO.getTags());
        video.setUpdatedTime(LocalDateTime.now());
        video.setStatus(VideoStatus.NotUploaded);
        dynamoDBMapper.save(video);
        context.getLogger().log("Data saved successfully to dynamoDB for user: "+video.getUserId());
        return "Created a new video entry for user: "+video.getUserId()+". \nPlease upload the video to the following url: "+ S3.getPresignedUploadUrl(video.getId(), context);
    }

    public Video getVideoById(String id, Context context) throws RuntimeException {
        Video video = dynamoDBMapper.load(Video.class, id);
        if(video == null)  throw new RuntimeException("Video not found with ID: " + id);
        return video;
    }

    public List<Video> getVideosByUserId(String userId, Context context) {
        Video queryObject = new Video();
        queryObject.setUserId(userId);
        DynamoDBQueryExpression<Video> queryExpression = new DynamoDBQueryExpression<Video>()
                .withIndexName("userId-updatedTime-index")
                .withHashKeyValues(queryObject)
                .withConsistentRead(false) // GSI doesn't support consistent read
                .withScanIndexForward(false); //for latest updatedTime first
        return dynamoDBMapper.query(Video.class, queryExpression);
    }

    public String updateVideoMetadata(VideoDTO videoDTO, Context context) {
        Video video = dynamoDBMapper.load(Video.class, videoDTO.getId());
        if(video == null)  throw new RuntimeException("Video not found with ID: " + videoDTO.getId());
        video.setTitle(videoDTO.getTitle());
        video.setDescription(videoDTO.getDescription());
        video.setTags(videoDTO.getTags());
        video.setUpdatedTime(LocalDateTime.now());
        dynamoDBMapper.save(video);
        VideoIndex videoIndex = new VideoIndex(video.getTitle(), video.getDescription(), video.getTags());
        IndexRequest<VideoIndex> indexRequest = new IndexRequest.Builder<VideoIndex>().index("video").id(video.getId()).document(videoIndex).build();
        try {
            openSearchClient.index(indexRequest);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        context.getLogger().log("Updated the status of the record with ID: " + video.getId());
        return Mapper.mapToString(video, context);
    }

    public String deleteVideo(String id, Context context) {
        Video video = dynamoDBMapper.load(Video.class, id);
        if(video == null)  throw new RuntimeException("Video not found with ID: " + id);
        Map<String, String> transcodeObjects = video.getTranscodeFiles();
        dynamoDBMapper.delete(video);
        context.getLogger().log("Deleted the record with ID in DynamoDB: " + id);
        S3.deleteTranscodeVideos(transcodeObjects, context);
        return "Deleted the video with ID: " + id;
    }
}
