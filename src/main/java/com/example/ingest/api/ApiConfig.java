package com.example.ingest.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@Configuration
@Profile("api")
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class ApiConfig {
}
