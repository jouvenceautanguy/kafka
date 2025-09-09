package com.example.kafkahello;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class KafkaTopicConfig {


    @Bean
    public NewTopic demoTopic() {
// 1 partition, replication factor 1 (une seule instance Kafka)
        return new NewTopic("demo", 1, (short) 1);
    }
}