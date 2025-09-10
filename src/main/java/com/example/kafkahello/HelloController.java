package com.example.kafkahello;


import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;


@RestController
public class HelloController {


    private final KafkaTemplate<String, String> kafkaTemplate;
    private final MeterRegistry meterRegistry;


    public HelloController(KafkaTemplate<String, String> kafkaTemplate, MeterRegistry meterRegistry) {
        this.kafkaTemplate = kafkaTemplate;
        this.meterRegistry = meterRegistry;
    }


    @GetMapping("/")
    public String hello() {
        return "Hello World";
    }


    @GetMapping("/publish")
    public String publish(@RequestParam(defaultValue = "Hello Kafka!") String msg) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            kafkaTemplate.send("demo", msg);
            meterRegistry.counter("app_messages_produced_total").increment();
            return "Sent to Kafka: " + msg;
        } finally {
            sample.stop(Timer.builder("app_publish_latency_seconds")
                    .description("Time to publish a message to Kafka")
                    .register(meterRegistry));
        }
    }
}