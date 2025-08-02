package com.videostore.amazon.lambdas;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.videostore.amazon.clients.OpenSearch;
import com.videostore.amazon.dtos.VideoIndex;
import com.videostore.amazon.entites.Video;
import com.videostore.amazon.services.VideoService;
import com.videostore.amazon.utiles.Mapper;
import com.videostore.amazon.dtos.VideoDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.indices.DeleteIndexRequest;
import org.opensearch.client.opensearch.indices.DeleteIndexResponse;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class UserRequestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
    private static Validator validator;
    private final VideoService videoService;
    private final OpenSearch openSearch;
    private OpenSearchClient openSearchClient;

    static {
        ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    public UserRequestHandler() {
        this.videoService = new VideoService();
        this.openSearch = new OpenSearch((String) System.getenv("OPEN_SEARCH_ENDPOINT"), System.getenv("REGION"));
        this.openSearchClient = openSearch.getClient();
    }

    public UserRequestHandler(VideoService videoService, OpenSearch openSearch) {
        this.videoService = videoService;
        this.openSearch = openSearch;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        switch (request.getHttpMethod()) {
            case "POST":
                context.getLogger().log("Request received: "+request.getBody());
                if(request.getBody() == null) return createAPIResponse(400, "Invalid request body.");
                logSystemEnvironmentVariables(context);
                VideoDTO videoDTO = Mapper.mapToVideoDTO(request.getBody(), context);
                String violations = validateInput(videoDTO);
                if(violations != null) return createAPIResponse(400, violations);
                return createAPIResponse(201, videoService.saveVideo(videoDTO, context));
            case "GET":
                context.getLogger().log("Request received: "+request.getQueryStringParameters());
                if(request.getQueryStringParameters() == null || (request.getQueryStringParameters().get("id") == null && request.getQueryStringParameters().get("userid") == null && request.getQueryStringParameters().get("search") == null)) return createAPIResponse(400, "Invalid request path. Please provide an id or userid.");
                if(request.getQueryStringParameters().get("id") != null) {
                    String videoId = request.getQueryStringParameters().get("id");
                    try {
                        Video video = videoService.getVideoById(videoId, context);
                        return createAPIResponse(200, Mapper.mapToString(video, context));
                    }
                    catch (RuntimeException e) {
                        return createAPIResponse(404, e.getMessage());
                    }
                }
                else if(request.getQueryStringParameters().get("userid") != null) {
                    String userId = request.getQueryStringParameters().get("userid");
                    List<Video> videos = videoService.getVideosByUserId(userId, context);
                    if(videos.isEmpty()) return createAPIResponse(404, "No videos found for the user.");
                    return createAPIResponse(200, Mapper.mapToString(videos, context));
                }
                else if(request.getQueryStringParameters().get("search") != null) {
                    SearchResponse<VideoIndex> searchResponse = null;
                    try {
                        searchResponse = openSearchClient.search(s -> s.index("video")
                                .query(q -> q.multiMatch(m -> m.fields(List.of("title", "description", "tags")).query(request.getQueryStringParameters().get("search")))), VideoIndex.class);
                        List<VideoIndex> results = searchResponse.hits().hits()
                                .stream()
                                .map(Hit::source)
                                .collect(Collectors.toList());
                        return createAPIResponse(200, Mapper.mapToString(results, context));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
                case "PUT":
                    context.getLogger().log("Request received: "+request.getBody());
                    logSystemEnvironmentVariables(context);
                    if(request.getBody() == null) return createAPIResponse(400, "Invalid request. Please provide a body.");
                    VideoDTO updatedvideoDTO = Mapper.mapToVideoDTO(request.getBody(), context);
                    if(updatedvideoDTO.getId() == null) return createAPIResponse(400, "Invalid request. Please provide an id to update.");
                    String DTOViolations = validateInput(updatedvideoDTO);
                    if(DTOViolations != null) return createAPIResponse(400, DTOViolations);
                    return createAPIResponse(201, videoService.updateVideoMetadata(updatedvideoDTO, context));
                case "DELETE":
                    context.getLogger().log("Request received: "+request.getQueryStringParameters());
                    if(request.getQueryStringParameters() == null || request.getQueryStringParameters().get("id") == null) return createAPIResponse(400, "Invalid request path. Please provide an id to delete.");
                    String videoId = request.getQueryStringParameters().get("id");
                    DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest.Builder().index("video").build();
                    try {
                        openSearchClient.indices().delete(deleteIndexRequest);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return createAPIResponse(200, videoService.deleteVideo(videoId, context));
            default: return createAPIResponse(400, "Invalid request method.");
        }
    }

    private APIGatewayProxyResponseEvent createAPIResponse(int statusCode, String body) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setHeaders(Map.of("Content-Type", "application/json"));
        response.setBody(body);
        return response;
    }

    private String validateInput(VideoDTO videoDTO) {
        Set<ConstraintViolation<VideoDTO>> violations = validator.validate(videoDTO);
        if(!violations.isEmpty()) {
            StringBuilder messageBuilder = new StringBuilder();
            messageBuilder.append("Validation errors: ");
            for (ConstraintViolation<VideoDTO> violation : violations) {
                messageBuilder.append("\n" + violation.getPropertyPath() + ": " + violation.getMessage() + ".");
            }
            return messageBuilder.toString();
        }
        return null;
    }

    private void logSystemEnvironmentVariables(Context context) {
        context.getLogger().log("Region: "+System.getenv("REGION"));
        context.getLogger().log("DynamoDB table: "+System.getenv("TABLE_NAME"));
        context.getLogger().log("S3 bucket: "+System.getenv("UPLOAD_BUCKET_NAME"));
    }
}
