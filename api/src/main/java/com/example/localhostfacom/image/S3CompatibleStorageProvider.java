package com.example.localhostfacom.image;

import com.example.localhostfacom.common.ApiException;
import com.example.localhostfacom.config.AppProperties;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Works with any S3-protocol store: MinIO locally, Cloudflare R2 in production.
 * Instantiated by {@code StorageConfig} rather than component-scanned, because its
 * constructor takes a nested config record that is not itself a bean.
 */
public class S3CompatibleStorageProvider implements StorageProvider {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final String publicBaseUrl;

    public S3CompatibleStorageProvider(AppProperties.Storage storage) {
        this.bucket = storage.bucket();
        this.publicBaseUrl = storage.publicBaseUrl().replaceAll("/+$", "");

        var credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
        var region = Region.of(storage.region() == null || storage.region().isBlank() ? "auto" : storage.region());

        this.client = S3Client.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .credentialsProvider(credentials)
                .region(region)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.pathStyle())
                        .build())
                .build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(storage.endpoint()))
                .credentialsProvider(credentials)
                .region(region)
                .build();
    }

    @Override
    public void upload(String key, InputStream body, long size, String mimeType) {
        try {
            client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType(mimeType).build(),
                    RequestBody.fromInputStream(body, size));
        } catch (S3Exception exception) {
            throw ApiException.badGateway("storage-upload-failed",
                    "Could not upload the image to object storage");
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (S3Exception exception) {
            throw ApiException.badGateway("storage-delete-failed",
                    "Could not delete the object from storage");
        }
    }

    @Override
    public String publicUrl(String key) {
        return publicBaseUrl + "/" + key;
    }

    @Override
    public String presignDownloadUrl(String key, Duration expires) {
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                        .signatureDuration(expires)
                        .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                        .build())
                .url()
                .toString();
    }
}
