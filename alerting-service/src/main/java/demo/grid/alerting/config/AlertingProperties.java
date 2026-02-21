package demo.grid.alerting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record AlertingProperties(String topic) {
}
