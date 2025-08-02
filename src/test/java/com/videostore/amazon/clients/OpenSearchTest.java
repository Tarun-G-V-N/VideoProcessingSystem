package com.videostore.amazon.clients;

import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.transport.aws.AwsSdk2Transport;
import org.opensearch.client.transport.aws.AwsSdk2TransportOptions;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;

public class OpenSearchTest {
    private final SdkHttpClient httpClient = ApacheHttpClient.builder().build();
    private OpenSearchClient client;

    public OpenSearchTest(String endpoint, String region) {
        this.client = new OpenSearchClient(
                new AwsSdk2Transport(httpClient,
                        "endpoint", // OpenSearch endpoint, without https://
                        "es",
                        Region.of("ap-south-1"), // signing service region
                        AwsSdk2TransportOptions.builder().setCredentials(DefaultCredentialsProvider.create()).build()
                )
        );
    }
}
