package com.example.localhostfacom.config;

import com.example.localhostfacom.image.S3CompatibleStorageProvider;
import com.example.localhostfacom.image.StorageProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StorageConfig {

    @Bean
    public StorageProvider storageProvider(AppProperties properties) {
        return new S3CompatibleStorageProvider(properties.storage());
    }
}
