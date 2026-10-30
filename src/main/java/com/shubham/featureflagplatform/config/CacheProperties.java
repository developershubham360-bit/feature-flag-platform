package com.shubham.featureflagplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.cache")
public record CacheProperties (

    @DefaultValue("300") int flagTtlSeconds,

    @DefaultValue("30") int negativeTtlSeconds
) {}
