package demo.grid.pricing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.kafka")
public record PricingProperties(String topic) {
}
