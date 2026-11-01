package com.shubham.featureflagplatform.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableConfigurationProperties(PropagationProperties.class)
public class KafkaConfig {
    @Bean
    public NewTopic flagChangesTopic(PropagationProperties props) {
        return TopicBuilder.name(props.topic()).partitions(props.partitions()).replicas(1).build();
    }
}
