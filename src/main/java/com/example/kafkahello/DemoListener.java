package com.example.kafkahello;


import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


@Component
public class DemoListener {
    private static final Logger log = LoggerFactory.getLogger(DemoListener.class);


    private final MeterRegistry meterRegistry;


    public DemoListener(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }


    @KafkaListener(topics = "demo", groupId = "hello-group")
    public void onMessage(String value) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            log.info("[Consumer] Reçu: {}", value);
            meterRegistry.counter("app_messages_consumed_total").increment();
        } finally {
            sample.stop(Timer.builder("app_consume_latency_seconds")
                    .description("Time to process a consumed message")
                    .register(meterRegistry));
        }
    }
}