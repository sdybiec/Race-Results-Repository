package org.rowtown.rms.rrr.config;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.Properties;

/**
 * Test configuration that provides an embedded Moquette MQTT broker.
 * This allows tests to run without requiring an external MQTT broker.
 */
@Slf4j
@TestConfiguration
public class EmbeddedMqttBrokerConfig {

    private Server mqttBroker;

    @Bean(initMethod = "startServer")
    public Server embeddedMqttBroker() throws IOException {
        log.info("Starting embedded Moquette MQTT broker for tests");

        Properties properties = new Properties();
        properties.setProperty(IConfig.HOST_PROPERTY_NAME, "localhost");
        properties.setProperty(IConfig.PORT_PROPERTY_NAME, "1883");
        properties.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, "true");
        properties.setProperty(IConfig.PERSISTENT_STORE_PROPERTY_NAME, "");
        properties.setProperty(IConfig.ENABLE_TELEMETRY_NAME, "false");

        IConfig config = new MemoryConfig(properties);
        mqttBroker = new Server();
        mqttBroker.startServer(config);

        log.info("Embedded Moquette MQTT broker started on port 1883");
        return mqttBroker;
    }

    @PreDestroy
    public void shutdown() {
        if (mqttBroker != null) {
            log.info("Stopping embedded Moquette MQTT broker");
            mqttBroker.stopServer();
        }
    }
}
