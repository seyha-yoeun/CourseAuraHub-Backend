package com.course_aura_hub.file.storage;

import com.course_aura_hub.common.exception.ServiceUnavailableException;
import com.course_aura_hub.file.config.StorageProperties;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

public class S3ObjectStore implements ObjectStore, AutoCloseable {

    private static final int NOT_FOUND = 404;

    private final String bucket;
    private final S3Client client;
    // Signed for the address browsers use, which can differ from the one the backend calls
    private final S3Presigner presigner;

    public S3ObjectStore(StorageProperties properties) {
        this.bucket = properties.bucket();
        Region region = Region.of(properties.region());
        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        // MinIO serves buckets by path, not by subdomain
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(properties.browserEndpoint()))
                .region(region)
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();
    }

    @Override
    public String presignUpload(String key, String contentType, long size, Duration ttl) {
        PutObjectRequest upload = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .build();
        return call(() -> presigner
                .presignPutObject(request -> request.signatureDuration(ttl).putObjectRequest(upload))
                .url().toString());
    }

    @Override
    public String presignDownload(String key, Duration ttl) {
        GetObjectRequest download = GetObjectRequest.builder().bucket(bucket).key(key).build();
        return call(() -> presigner
                .presignGetObject(request -> request.signatureDuration(ttl).getObjectRequest(download))
                .url().toString());
    }

    @Override
    public Optional<Long> size(String key) {
        return call(() -> {
            try {
                return Optional.of(client.headObject(request -> request.bucket(bucket).key(key)).contentLength());
            } catch (S3Exception ex) {
                if (ex.statusCode() == NOT_FOUND) {
                    return Optional.empty();
                }
                throw ex;
            }
        });
    }

    @Override
    public byte[] readStart(String key, int length) {
        return call(() -> client.getObjectAsBytes(request -> request
                .bucket(bucket)
                .key(key)
                .range("bytes=0-" + (length - 1))).asByteArray());
    }

    @Override
    public void move(String fromKey, String toKey) {
        call(() -> client.copyObject(request -> request
                .sourceBucket(bucket)
                .sourceKey(fromKey)
                .destinationBucket(bucket)
                .destinationKey(toKey)));
        delete(fromKey);
    }

    @Override
    public void delete(String key) {
        call(() -> client.deleteObject(request -> request.bucket(bucket).key(key)));
    }

    @Override
    public void close() {
        client.close();
        presigner.close();
    }

    private <T> T call(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (SdkException ex) {
            throw new ServiceUnavailableException("File storage is unavailable. Please try again.", ex);
        }
    }
}
