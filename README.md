# Kafka Microservices Monitoring – Guide Complet

Ce projet montre un mini-système de microservices (producteur et consommateur) qui communiquent via Apache Kafka, avec un monitoring complet via Prometheus et Grafana. Il est prêt à tourner en local avec Docker Compose.

## Sommaire
- Vue d’ensemble de l’architecture
- Détails du code (producteur/consommateur/config Kafka)
- Flux applicatif de bout en bout
- Métriques, Prometheus et exporters
- Dashboards Grafana
- Services Docker (à quoi ils servent)
- Démarrage, tests et vérifications
- Dépannage (troubleshooting)
- Aller plus loin (vrai découpage microservices)

## 1) Vue d’ensemble de l’architecture
- Application Spring Boot unique qui contient deux rôles logiques:
  - Producteur HTTP → Kafka (endpoint `/publish`)
  - Consommateur Kafka → Traitement (affichage/metrics)
- Kafka (mode KRaft) pour la messagerie asynchrone.
- Prometheus pour collecter les métriques:
  - Actuator/Micrometer de l’application
  - JMX Exporter pour les métriques JVM/broker Kafka
  - Kafka Exporter pour les lags/offsets des consumers
- Grafana pour visualiser les métriques (dashboards provisionnés).

## 2) Détails du code
Les fichiers principaux sont sous `src/main/java/com/example/kafkahello/`.

### `KafkaHelloApplication.java`
Point d’entrée Spring Boot:
- Démarre l’application, enregistre les beans et configurations.

### `KafkaTopicConfig.java`
Crée automatiquement le topic Kafka `demo` au démarrage:
```java
@Bean
public NewTopic demoTopic() {
    return new NewTopic("demo", 1, (short) 1);
}
```
- 1 partition, facteur de réplication 1 (adapté à un broker local unique).

### `HelloController.java` (Producteur)
- Endpoint HTTP `GET /publish?msg=...`.
- Utilise `KafkaTemplate<String, String>` pour publier sur `demo`.
- Instrumentation Micrometer:
  - `app_messages_produced_total`: compteur de messages produits.
  - `app_publish_latency_seconds`: timer/histogramme de latence de publication.

Extrait clé:
```12:33:src/main/java/com/example/kafkahello/HelloController.java
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
```

### `DemoListener.java` (Consommateur)
- Écoute le topic `demo` via `@KafkaListener(topics = "demo", groupId = "hello-group")`.
- Traite chaque message (log) et enregistre des métriques:
  - `app_messages_consumed_total`: compteur de messages consommés.
  - `app_consume_latency_seconds`: timer/histogramme de latence de traitement.

Extrait clé:
```14:34:src/main/java/com/example/kafkahello/DemoListener.java
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
```

### `src/main/resources/application.properties`
- Connexion Kafka, sérializers, groupId.
- Actuator/Micrometer activés et exposés sur `/actuator/prometheus`.

Extrait clé:
```1:19:src/main/resources/application.properties
management.endpoints.web.exposure.include=health,info,prometheus
management.endpoint.health.show-details=always
management.server.port=8080
management.metrics.tags.application=kafkahello
```

## 3) Flux applicatif de bout en bout
1. Appel HTTP: `GET http://localhost:8080/publish?msg=hello`.
2. Le producteur publie `hello` sur Kafka (topic `demo`).
3. Le consommateur lit `hello` depuis `demo`, le journalise, et met à jour ses métriques.
4. Prometheus “scrape” périodiquement l’app (`/actuator/prometheus`), le JMX exporter et le Kafka exporter.
5. Grafana lit Prometheus et affiche les métriques sur des dashboards.

## 4) Métriques, Prometheus et exporters

### Métriques applicatives (Actuator/Micrometer)
- Exposées sur `http://app:8080/actuator/prometheus`.
- Permettent de suivre:
  - Messages produits/consommés
  - Latences p50/p95/p99 de publication et consommation
  - Métriques Spring/JVM de base (si activées par défaut)

### JMX Exporter (métriques JVM/Broker Kafka)
- Image: `bitnami/jmx-exporter`.
- Se connecte au port JMX du broker (`kafka:5555`) via la config `jmx-exporter-kafka.yml`.
- Expose en HTTP sur `:5556` pour Prometheus.
- Mesure: heap/GC, threads, métriques Kafka internes (network, controller, server).

### Kafka Exporter (métriques lag/offset)
- Image: `danielqsj/kafka-exporter`.
- Se connecte au broker `kafka:9092`.
- Expose en HTTP sur `:9308` pour Prometheus.
- Mesure: lags des consumer groups, offsets, partitions/topics.

### Prometheus (`prometheus.yml`)
Extrait:
```1:22:prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: app
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ['app:8080']
  - job_name: kafka-jmx
    static_configs:
      - targets: ['kafka-jmx-exporter:5556']
  - job_name: kafka-exporter
    static_configs:
      - targets: ['kafka-exporter:9308']
```

## 5) Dashboards Grafana
- Datasource Prometheus auto-provisionnée (`provisioning/datasources/datasource.yml`).
- Dashboards auto-chargés (`provisioning/dashboards/`).
  - Exemple: `kafka-microservices.json` affiche:
    - Messages produits/consommés (sur 5 min)
    - Latence p95 de publish/consume
    - Somme des lags consumers
- Accès: `http://localhost:3000` (admin/admin par défaut).

## 6) Services Docker (docker-compose.yml)

### `kafka` (bitnami/kafka)
- Ports: `9092` (client), `5555` (JMX)
- Mode KRaft (sans ZooKeeper)
- Variables clés: `KAFKA_CFG_ADVERTISED_LISTENERS=PLAINTEXT://kafka:9092`, `KAFKA_ENABLE_JMX=yes`
- Volume: `kafka_data`

### `app` (Spring Boot)
- Build via `Dockerfile` (Maven Temurin 17).
- Port: `8080` (HTTP + Actuator)
- Env: `SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:9092`

### `kafka-jmx-exporter` (JMX → Prometheus)
- Image multi-arch `bitnami/jmx-exporter`
- Montre `jmx-exporter-kafka.yml` en `/config.yml`
- Expose: `5556`

### `kafka-exporter` (lags/offsets)
- Image `danielqsj/kafka-exporter`
- Paramètre: `--kafka.server=kafka:9092`
- Expose: `9308`

### `prometheus`
- Montre `prometheus.yml`
- Expose: `9090`

### `grafana`
- Datasource + dashboards provisionnés
- Expose: `3000`

## 7) Démarrage, tests et vérifications

Démarrer:
```bash
docker compose down -v
docker compose up -d --build
```
Vérifier:
- App: `http://localhost:8080`
- Prometheus: `http://localhost:9090` → Status > Targets → cibles UP
- Grafana: `http://localhost:3000` (admin/admin)

Générer du trafic:
```bash
curl "http://localhost:8080/publish?msg=hello"
```

PromQL utiles:
- `sum(increase(app_messages_produced_total[5m]))`
- `sum(increase(app_messages_consumed_total[5m]))`
- `histogram_quantile(0.95, sum(rate(app_publish_latency_seconds_bucket[5m])) by (le))`
- `histogram_quantile(0.95, sum(rate(app_consume_latency_seconds_bucket[5m])) by (le))`
- `sum(kafka_consumergroup_lag)`

## 8) Dépannage
- Un target DOWN dans Prometheus → cliquez sur l’erreur pour voir la raison (DNS, port, auth).
- `kafka-exporter` ne montre pas de lag → générez du trafic; vérifiez le groupId du consumer.
- Apple Silicon (arm64): images choisies multi-arch; en cas d’erreur de plateforme, purgez et relancez.
- Grafana login → identifiants par défaut `admin/admin` (changeables dans `docker-compose.yml`).

## 9) Aller plus loin (vrai découpage microservices)
- Séparer producteur et consommateur en deux projets/images distincts.
- Ajouter un “Analyseur” qui calcule des statistiques (somme, moyenne, TPS) et expose des métriques dédiées.
- Ajouter de l’alerting Prometheus (Alertmanager) sur lag élevé, erreurs, etc.
- Ajouter des dashboards détaillés par topic/partition et par consumer group.

---
Ce dépôt contient tous les livrables attendus:
- Code source (producteur/consommateur)
- `docker-compose.yml`
- `prometheus.yml`
- Dashboard Grafana JSON (`provisioning/dashboards/kafka-microservices.json`)
- Ce README détaillé (ajoutez vos captures après exécution)
