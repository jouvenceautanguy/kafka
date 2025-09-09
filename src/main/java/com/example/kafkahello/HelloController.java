package com.example.kafkahello;


import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class HelloController {


    private final KafkaTemplate<String, String> kafkaTemplate;


    public HelloController(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }


    @GetMapping("/")
    public String hello() {
        return "Hello World";
    }


    @GetMapping("/publish")
    public String publish(@RequestParam(defaultValue = "Hello Kafka!") String msg) {
        kafkaTemplate.send("demo", msg);
        return "Sent to Kafka: " + msg;
    }
}