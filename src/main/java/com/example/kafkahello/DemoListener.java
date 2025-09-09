package com.example.kafkahello;


import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Component
public class DemoListener {
    private static final Logger log = LoggerFactory.getLogger(DemoListener.class);


    @KafkaListener(topics = "demo", groupId = "hello-group")
    public void onMessage(String value) {
        log.info("[Consumer] Reçu: {}", value);
    }
}