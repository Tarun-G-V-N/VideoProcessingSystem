package com.videostore.amazon.clients;

import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.regions.Region;

@ExtendWith(MockitoExtension.class)
public class S3Test {
    private String bucketName = "UPLOAD_BUCKET_NAME";
    private String streamBucketName = "STREAM_BUCKET_NAME";
    private Region region = Region.of("ap-south-1");
}
