package com.videostore.amazon.services;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.videostore.amazon.clients.DynamoDB;
import com.videostore.amazon.clients.OpenSearch;
import com.videostore.amazon.clients.S3;
import com.videostore.amazon.dtos.VideoDTO;
import com.videostore.amazon.entites.Video;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class VideoServiceTest {
    @Mock
    private DynamoDBMapper dynamoDBMapper;
    @Mock
    private  OpenSearch openSearch;
    @Mock
    private OpenSearchClient openSearchClient;
    @Mock
    private S3 S3;
    @InjectMocks
    private VideoService videoService;

    @Test
    void S3PresignedUrlTest() {
        VideoDTO videoDTO = new VideoDTO();
        videoDTO.setUserId("user123");
        videoDTO.setTitle("Test Title");
        videoDTO.setDescription("Test Desc");
        videoDTO.setTags(List.of("tag1", "tag2"));

        Video video = Mockito.mock(Video.class);
        Context context = Mockito.mock(Context.class);
        LambdaLogger mockLogger = Mockito.mock(LambdaLogger.class);
        Mockito.when(context.getLogger()).thenReturn(mockLogger);

        Mockito.doNothing().when(mockLogger).log(Mockito.anyString());
        Mockito.doNothing().when(dynamoDBMapper).save(Mockito.any(Video.class));
        Mockito.when(S3.getPresignedUploadUrl(null, context))
                .thenReturn("http://dummy-url");

        videoService.saveVideo(videoDTO, context);
        Mockito.verify(S3, Mockito.times(1)).getPresignedUploadUrl(null, context);
    }

    @Test
    void videoNotFoundTest() {
        Mockito.when(dynamoDBMapper.load(Video.class, "video-123")).thenReturn(null);
        assertThrows(RuntimeException.class, () -> videoService.getVideoById("video-123", Mockito.mock(Context.class)));
    }

    @Test
    void videoFoundTest() {
        Video video = Mockito.mock(Video.class);
        Mockito.when(dynamoDBMapper.load(Video.class, "video-123")).thenReturn(video);
        assertEquals(video, videoService.getVideoById("video-123", Mockito.mock(Context.class)));
    }
}
