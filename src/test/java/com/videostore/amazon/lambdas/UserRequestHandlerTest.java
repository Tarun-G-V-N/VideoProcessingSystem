package com.videostore.amazon.lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.videostore.amazon.clients.OpenSearch;
import com.videostore.amazon.dtos.VideoDTO;
import com.videostore.amazon.entites.Video;
import com.videostore.amazon.services.VideoService;
import com.videostore.amazon.utiles.Mapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class UserRequestHandlerTest {
    @Mock
    private VideoService videoService;
    @Mock
    private OpenSearch openSearch;
    @Mock
    private OpenSearchClient openSearchClient;
    @InjectMocks
    private UserRequestHandler userRequestHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testPost_emptyBody_returns400() {
        Context context = Mockito.mock(Context.class);
        LambdaLogger mockLogger = Mockito.mock(LambdaLogger.class);
        Mockito.when(context.getLogger()).thenReturn(mockLogger);
        Mockito.doNothing().when(mockLogger).log(Mockito.anyString());
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = userRequestHandler.handleRequest(createRequest("POST", new HashMap<>(), null), context);
        assertEquals(400, apiGatewayProxyResponseEvent.getStatusCode());
    }

    @Test
    void testPost_DBSaveIsCalledWithValidBody() {
        Context context = Mockito.mock(Context.class);
        LambdaLogger mockLogger = Mockito.mock(LambdaLogger.class);
        Mockito.when(context.getLogger()).thenReturn(mockLogger);
        Mockito.doNothing().when(mockLogger).log(Mockito.anyString());
        VideoDTO videoDTO = new VideoDTO();
        videoDTO.setUserId("tarun-123"); videoDTO.setTitle("test"); videoDTO.setDescription("testing the lambda function first time");
        MockedStatic<Mapper> mockedMapper = Mockito.mockStatic(Mapper.class);
        mockedMapper.when(() -> Mapper.mapToVideoDTO(Mockito.any(), Mockito.eq(context)))
                .thenReturn(videoDTO);
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = userRequestHandler.handleRequest(createRequest("POST", new HashMap<>(), videoDTO), context);
        Mockito.verify(videoService).saveVideo(videoDTO, context);
    }

    @Test
    void testGet_emptyQueryParams_returns400() {
        Context context = Mockito.mock(Context.class);
        LambdaLogger mockLogger = Mockito.mock(LambdaLogger.class);
        Mockito.when(context.getLogger()).thenReturn(mockLogger);
        Mockito.doNothing().when(mockLogger).log(Mockito.anyString());
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = userRequestHandler.handleRequest(createRequest("GET", new HashMap<>(), null), context);
        assertEquals(400, apiGatewayProxyResponseEvent.getStatusCode());
    }

    @Test
    void testGet_validVideoId_returns200() {
        Context context = Mockito.mock(Context.class);
        LambdaLogger mockLogger = Mockito.mock(LambdaLogger.class);
        Mockito.when(context.getLogger()).thenReturn(mockLogger);
        Mockito.doNothing().when(mockLogger).log(Mockito.anyString());
        Video video = Mockito.mock(Video.class);
        Mockito.when(videoService.getVideoById("video-123", context)).thenReturn(video);
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = userRequestHandler.handleRequest(createRequest("GET", Map.of("id", "video-123"), null), context);
        assertEquals(200, apiGatewayProxyResponseEvent.getStatusCode());
    }

    @Test
    void testGet_validUserId_returns200() {
        Context context = Mockito.mock(Context.class);
        LambdaLogger mockLogger = Mockito.mock(LambdaLogger.class);
        Mockito.when(context.getLogger()).thenReturn(mockLogger);
        Mockito.doNothing().when(mockLogger).log(Mockito.anyString());
        Video video = Mockito.mock(Video.class);
        Mockito.when(videoService.getVideosByUserId("user-123", context)).thenReturn(List.of(video));
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = userRequestHandler.handleRequest(createRequest("GET", Map.of("userid", "user-123"), null), context);
        assertEquals(200, apiGatewayProxyResponseEvent.getStatusCode());
    }

    @Test
    void testGet_nullVideo_returns404() {
        Context context = Mockito.mock(Context.class);
        LambdaLogger mockLogger = Mockito.mock(LambdaLogger.class);
        Mockito.when(context.getLogger()).thenReturn(mockLogger);
        Mockito.doNothing().when(mockLogger).log(Mockito.anyString());
        Mockito.when(videoService.getVideoById("video-123", context)).thenThrow(new RuntimeException());
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = userRequestHandler.handleRequest(createRequest("GET", Map.of("id", "video-123"), null), context);
        assertEquals(404, apiGatewayProxyResponseEvent.getStatusCode());
    }

    @Test
    void testGet_nullUserId_returns404() {
        Context context = Mockito.mock(Context.class);
        LambdaLogger mockLogger = Mockito.mock(LambdaLogger.class);
        Mockito.when(context.getLogger()).thenReturn(mockLogger);
        Mockito.doNothing().when(mockLogger).log(Mockito.anyString());
        Mockito.when(videoService.getVideosByUserId("user-123", context)).thenReturn(List.of());
        APIGatewayProxyResponseEvent apiGatewayProxyResponseEvent = userRequestHandler.handleRequest(createRequest("GET", Map.of("userid", "user-123"), null), context);
        assertEquals(404, apiGatewayProxyResponseEvent.getStatusCode());
    }

    private APIGatewayProxyRequestEvent createRequest(String method, Map<String, String> queryParams, Object body) {
        APIGatewayProxyRequestEvent req = new APIGatewayProxyRequestEvent();
        req.setHttpMethod(method);
        req.setQueryStringParameters(queryParams);
        try {
            req.setBody(body != null ? objectMapper.writeValueAsString(body) : null);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
        return req;
    }
}
