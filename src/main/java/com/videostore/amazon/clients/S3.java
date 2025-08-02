package com.videostore.amazon.clients;

import com.amazonaws.services.lambda.runtime.Context;
import lombok.Getter;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
public class S3 {
    private String bucketName = System.getenv("UPLOAD_BUCKET_NAME");
    private String streamBucketName = System.getenv("STREAM_BUCKET_NAME");
    private Region region = Region.of(System.getenv("REGION"));
    private S3Presigner presigner = S3Presigner.builder().region(region)
            .credentialsProvider(DefaultCredentialsProvider.create())
            .build();

    public S3() {}

    public S3(String bucketName, String streamBucketName, Region region, S3Presigner presigner) {
        this.bucketName = bucketName;
        this.streamBucketName = streamBucketName;
        this.region = region;
        this.presigner = presigner;
    }

    public String getPresignedUploadUrl(String objectKey, Context context) {
        context.getLogger().log("BucketName: "+bucketName);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();

        PutObjectPresignRequest putObjectPresignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putObjectRequest)
                .build();

        return presigner.presignPutObject(putObjectPresignRequest).url().toString();
    }

    public String getPresignedDownloadUrl(String objectKey, Context context) {
        context.getLogger().log("BucketName: "+bucketName);
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey).build();

        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getObjectRequest)
                .build();

        return presigner.presignGetObject(getObjectPresignRequest).url().toString();
    }

    public void deleteObject(String objectKey, Context context) {
        try {
            DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .build();
            S3Client s3Client = S3Client.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            context.getLogger().log("Error deleting object: " + objectKey+" : "+ e.getMessage());
        }
    }

    public void deleteTranscodeVideos(Map<String, String> objects, Context context) {
        S3Client s3Client = S3Client.builder().region(region).credentialsProvider(DefaultCredentialsProvider.create()).build();
        List<ObjectIdentifier> keysToDelete = new ArrayList<>();
        for (Map.Entry<String, String> entry : objects.entrySet()) {
            try {
                URI uri = new URI(entry.getValue());
                String rawPath = uri.getPath();
                String objectKey = URLDecoder.decode(rawPath.substring(1), StandardCharsets.UTF_8);
                keysToDelete.add(ObjectIdentifier.builder().key(objectKey).build());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }
        context.getLogger().log("Keys to delete: "+keysToDelete);
        DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                .bucket(streamBucketName)
                .delete(Delete.builder().objects(keysToDelete).build())
                .build();
        DeleteObjectsResponse response = s3Client.deleteObjects(deleteRequest);
        context.getLogger().log("Deleted: " + response.deleted());
        context.getLogger().log("Errors: " + response.errors());
    }
}
