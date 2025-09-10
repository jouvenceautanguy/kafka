# Kafka Microservices Monitoring with Prometheus & Grafana

## Architecture
- Spring Boot app (`kafkahello`) with producer (`/publish`) and consumer (`@KafkaListener`)
- Apache Kafka single-broker (KRaft)
- Prometheus scrapes:
  - Spring Boot Actuator `/actuator/prometheus`
  - Kafka JMX exporter (broker/JVM metrics)
  - Kafka Exporter (consumer lag)
- Grafana with Prometheus datasource and a sample dashboard auto-provisioned

## Run
```bash
docker compose up -d --build
open http://localhost:3000   # Grafana (admin/admin)
open http://localhost:9090   # Prometheus
open http://localhost:8080   # App
```

Produce messages:
```bash
curl "http://localhost:8080/publish?msg=hello"
```

## Verify metrics
- Prometheus > Status > Targets: all UP
- Prometheus query examples:
  - `app_messages_produced_total`
  - `app_messages_consumed_total`
  - `histogram_quantile(0.95, sum(rate(app_publish_latency_seconds_bucket[5m])) by (le))`
  - `kafka_consumergroup_lag`

## Files of interest
- `docker-compose.yml`: Kafka, App, Prometheus, Grafana, exporters
- `prometheus.yml`: scrape jobs
- `provisioning/datasources/datasource.yml`: Grafana datasource
- `provisioning/dashboards/*.json`: Grafana dashboards
- `src/main/java/com/example/kafkahello/*`: app code

## Deliverables
- Source code of microservices (this repo)
- `docker-compose.yml`
- `prometheus.yml`
- Grafana dashboard JSON (`provisioning/dashboards/kafka-microservices.json`)
- This README with instructions and screenshots (add screenshots after run)
