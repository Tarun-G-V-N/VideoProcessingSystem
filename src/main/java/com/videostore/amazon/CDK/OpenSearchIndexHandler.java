package com.videostore.amazon.CDK;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.videostore.amazon.clients.OpenSearch;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.indices.CreateIndexRequest;
import software.amazon.awssdk.regions.Region;


import java.io.IOException;
import java.util.Map;

public class OpenSearchIndexHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        Map<String, Object> resourceProperties = (Map<String, Object>) event.get("ResourceProperties");
        if("Create".equals((String)event.get("RequestType"))) {
            String indexName = (String) resourceProperties.get("indexName");
            OpenSearch openSearch = new OpenSearch((String) resourceProperties.get("endpoint"), (String) resourceProperties.get("region"));
            OpenSearchClient client = openSearch.getClient();
            CreateIndexRequest createIndexRequest = new CreateIndexRequest.Builder().index(indexName).build();
            try {
                client.indices().create(createIndexRequest);
            } catch (IOException e) {
                context.getLogger().log(e.getMessage());
            }
        }
        return Map.of("PhysicalResourceId", resourceProperties.get("indexName"));
    }
}
