package com.schoen.fosproducer.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NoArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.messaging.Message;

import java.util.HashMap;
import java.util.Map;


@Configuration
@NoArgsConstructor
public class KafkaProducerConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaProducerConfiguration.class);

    public static final String BOOTSTRAP_SERVERS = System.getenv("BOOTSTRAP_SERVERS");

    @Bean
    KafkaTemplate<String, Message<?>> kafkaTemplate() {
        return new KafkaTemplate<>(kafkaProducerFactory());
    }

    @Bean
    public KafkaAdmin.NewTopics topics() {
        return new KafkaAdmin.NewTopics(
                createTopic("producedEvents"),
                createTopic("availableDBEntries"));
    }

    private NewTopic createTopic(final String topic) {
        return TopicBuilder.name(topic)
                            .partitions(1)
                            .replicas(1)
                            .compact()
                            .build();
    }

    private ObjectMapper objectMapper() {
        return Jackson2ObjectMapperBuilder.json()
                .visibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
                .build();
    }

    private ProducerFactory<String, Message<?>> kafkaProducerFactory() {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        return new DefaultKafkaProducerFactory<>(properties, new StringSerializer(), new JsonSerializer<>(objectMapper()));
    }

}
