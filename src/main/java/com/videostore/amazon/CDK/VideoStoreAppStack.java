package com.videostore.amazon.CDK;

import software.amazon.awscdk.*;
import software.amazon.awscdk.customresources.Provider;
import software.amazon.awscdk.services.apigateway.Deployment;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.events.EventPattern;
import software.amazon.awscdk.services.events.Rule;
import software.amazon.awscdk.services.events.targets.LambdaFunction;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.opensearchservice.CapacityConfig;
import software.amazon.awscdk.services.opensearchservice.Domain;
import software.amazon.awscdk.services.opensearchservice.EngineVersion;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.EventType;
import software.amazon.awscdk.services.s3.notifications.LambdaDestination;
import software.constructs.Construct;

import java.util.Collections;
import java.util.List;
import java.util.Map;


public class VideoStoreAppStack extends Stack {
    public VideoStoreAppStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        //DynamoDB Table
        TableV2 table = TableV2.Builder.create(this, "video-table")
                .tableName("video-store-cdk-app-video-table")
                .partitionKey(Attribute.builder().name("id").type(AttributeType.STRING).build())
                .billing(Billing.onDemand())
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        table.addGlobalSecondaryIndex(GlobalSecondaryIndexPropsV2.builder()
                .indexName("userId-updatedTime-index")
                .partitionKey(Attribute.builder().name("userId").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("updatedTime").type(AttributeType.NUMBER).build())
                .build());

        //S3 Buckets
        Bucket uploadBucket = Bucket.Builder.create(this, "video-store-upload-bucket")
                .bucketName("video-store-cdk-app-upload-bucket")
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        Bucket streamBucket = Bucket.Builder.create(this, "video-store-stream-bucket")
                .bucketName("video-store-cdk-app-stream-bucket")
                .removalPolicy(RemovalPolicy.DESTROY)
                .blockPublicAccess(BlockPublicAccess.Builder.create()
                        .blockPublicAcls(false).blockPublicPolicy(false).ignorePublicAcls(false).restrictPublicBuckets(false).build())
                .publicReadAccess(true)
                .build();

        //openSearch
        Domain domain = Domain.Builder.create(this, "video-store-opensearch")
                .version(EngineVersion.OPENSEARCH_2_19)
                .capacity(CapacityConfig.builder().dataNodes(1).dataNodeInstanceType("t3.small.search").multiAzWithStandbyEnabled(false).build())
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        domain.addAccessPolicies(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("*")).principals(List.of(new ArnPrincipal("arn:aws:iam::730335183084:user/opensearch-user")))
                .resources(List.of("*")).build());

        //creating policy for the role attached to lambda
        ManagedPolicy lambdaAccessPolicy = ManagedPolicy.Builder.create(this, "video-store-lambda-access-policy")
                .managedPolicyName("video-store-cdk-app-lambda-access-policy")
                .statements(List.of(
                        PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of("dynamodb:PutItem", "dynamodb:UpdateItem", "dynamoDB:GetItem", "dynamoDB:DeleteItem"))
                                .resources(List.of(table.getTableArn()))
                                .build(),
                        PolicyStatement.Builder.create()
                                .effect(Effect.ALLOW)
                                .actions(List.of("s3:PutObject", "s3:GetObject", "s3:DeleteObject"))
                                .resources(List.of(uploadBucket.getBucketArn() + "/*", streamBucket.getBucketArn() + "/*"))
                                .build()
                )).build();

        //Lambda Execution Role
        Role lambdaExecutionRole = Role.Builder.create(this, "video-store-handler-lambda-role")
                .roleName("video-store-cdk-app-handler-lambda-role")
                .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
                .managedPolicies(List.of(
                        ManagedPolicy.fromAwsManagedPolicyName("service-role/AWSLambdaBasicExecutionRole"),
                        lambdaAccessPolicy
                ))
                .build();

        //MediaConvert Role
        Role mediaConvertRole = Role.Builder.create(this, "video-store-media-convert-role")
                .roleName("video-store-cdk-app-media-convert-role")
                .assumedBy(new ServicePrincipal("mediaconvert.amazonaws.com"))
                .build();

        //Policy to allow mediaConvertRole to access upload and stream buckets
        PolicyStatement streamBucketAccessPolicy = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("s3:PutObject", "s3:GetObject"))
                .resources(List.of(streamBucket.getBucketArn() + "/*"))
                .build();
        PolicyStatement uploadBucketAccessPolicy = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("s3:PutObject", "s3:GetObject", "s3:DeleteObject"))
                .resources(List.of(uploadBucket.getBucketArn() + "/*"))
                .build();
        mediaConvertRole.addToPolicy(streamBucketAccessPolicy);
        mediaConvertRole.addToPolicy(uploadBucketAccessPolicy);

        //Lambda Functions
        Function userRequestHandler = DockerImageFunction.Builder.create(this, "VideoStoreCDKAPP-userRequestHandler")
                .functionName("VideoStoreCDKAPP-userRequestHandler")
                .timeout(Duration.seconds(15))
                .memorySize(512)
                .code(DockerImageCode.fromImageAsset(".", AssetImageCodeProps.builder()
                                .file("docker-file/Dockerfile")
                                .buildArgs(Map.of("INCLUDE_MEDIAINFO", "false", "HANDLER", "com.videostore.amazon.lambdas.UserRequestHandler"))
                        .build()))
                .environment(Map.of("TABLE_NAME", table.getTableName(), "UPLOAD_BUCKET_NAME", uploadBucket.getBucketName(), "STREAM_BUCKET_NAME", streamBucket.getBucketName(),"REGION", "ap-south-1", "OPEN_SEARCH_ENDPOINT",domain.getDomainEndpoint()))
                .role(lambdaExecutionRole)
                .build();
        domain.grantReadWrite(userRequestHandler);

        //Adding permission so that the lambda can read from the GSI.
        table.grantReadData(userRequestHandler);

        Function s3Handler = DockerImageFunction.Builder.create(this, "VideoStoreCDKAPP-S3Handler")
                .functionName("VideoStoreCDKAPP-S3Handler")
                .timeout(Duration.seconds(15))
                .memorySize(512)
                .code(DockerImageCode.fromImageAsset(".", AssetImageCodeProps.builder()
                        .file("docker-file/Dockerfile")
                        .buildArgs(Map.of("INCLUDE_MEDIAINFO", "true", "HANDLER", "com.videostore.amazon.lambdas.S3Handler"))
                        .build()))
                .environment(Map.of("TABLE_NAME", table.getTableName(), "UPLOAD_BUCKET_NAME", uploadBucket.getBucketName(), "REGION", "ap-south-1",
                "STREAM_BUCKET_NAME", streamBucket.getBucketName(), "MEDIA_CONVERT_ROLE_ARN", mediaConvertRole.getRoleArn()))
                .role(lambdaExecutionRole)
                .build();

        //Policy to allow a service to provide a role to another service
        PolicyStatement passRolePolicy = PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(List.of("iam:PassRole", "mediaconvert:CreateJob"))
                .resources(Collections.singletonList("*"))
                .build();
        s3Handler.addToRolePolicy(passRolePolicy);

        Function mediaConvertHandler = DockerImageFunction.Builder.create(this, "VideoStoreCDKAPP-MediaConvertHandler")
                .functionName("VideoStoreCDKAPP-MediaConvertHandler")
                .timeout(Duration.seconds(15))
                .memorySize(512)
                .code(DockerImageCode.fromImageAsset(".", AssetImageCodeProps.builder()
                        .file("docker-file/Dockerfile")
                        .buildArgs(Map.of("INCLUDE_MEDIAINFO", "false", "HANDLER", "com.videostore.amazon.lambdas.MediaConvertHandler"))
                        .build()))
                .environment(Map.of("TABLE_NAME", table.getTableName(), "UPLOAD_BUCKET_NAME", uploadBucket.getBucketName(), "REGION", "ap-south-1", "OPEN_SEARCH_ENDPOINT",domain.getDomainEndpoint()))
                .role(lambdaExecutionRole)
                .build();
        domain.grantReadWrite(mediaConvertHandler);

        //Rule to trigger MediaConvertHandler
        Rule mediaConvertRule = Rule.Builder.create(this, "VideoStoreCDKAPP-MediaConvertRule")
                .eventPattern(EventPattern.builder().source(List.of("aws.mediaconvert")).detailType(List.of("MediaConvert Job State Change")).detail(Map.of("status", List.of("COMPLETE", "PROGRESSING", "ERROR"))).build())
                        .build();
        mediaConvertRule.addTarget(LambdaFunction.Builder.create(mediaConvertHandler).retryAttempts(3).build());

        //S3 Notification
        uploadBucket.addEventNotification(EventType.OBJECT_CREATED, new LambdaDestination(s3Handler));

        //lambda function to create openSearch index
        Function openSearchIndexHandler = DockerImageFunction.Builder.create(this, "VideoStoreCDKAPP-OpenSearchIndexHandler")
                .functionName("VideoStoreCDKAPP-OpenSearchIndexHandler")
                .timeout(Duration.seconds(15))
                .memorySize(512)
                .code(DockerImageCode.fromImageAsset(".", AssetImageCodeProps.builder()
                        .file("docker-file/Dockerfile")
                        .buildArgs(Map.of("INCLUDE_MEDIAINFO", "false", "HANDLER", "com.videostore.amazon.CDK.OpenSearchIndexHandler"))
                        .build()))
                .build();
        domain.grantReadWrite(openSearchIndexHandler);

        Provider provider = Provider.Builder.create(this, "VideoStoreCDKAPP-OpenSearchIndexProvider")
                .onEventHandler(openSearchIndexHandler)
                .build();
        CustomResource.Builder.create(this, "VideoStoreCDKAPP-OpenSearchIndexCustomResource")
                .serviceToken(provider.getServiceToken())
                .properties(Map.of("endpoint", domain.getDomainEndpoint(),
                        "region", this.getRegion(),
                        "indexName", "video",
                        "indexProperties", Map.of("mappings",
                                Map.of("properties",
                                        Map.of("title", Map.of("type", "text"),
                                                "description", Map.of("type", "text"),
                                                "tags", Map.of("type", "text"))))))
                .build();

        //API Gateway
        RestApi restApi = RestApi.Builder.create(this, "video-store-api").restApiName("video-store-cdk-app-api").deploy(false).build();
        restApi.getRoot().addResource("video").addMethod("ANY", new LambdaIntegration(userRequestHandler));

        //deploying the API
        Deployment deployment = Deployment.Builder.create(this, "video-store-api-deployment")
                            .api(restApi).stageName("dev").retainDeployments(true).build();
    }
}
