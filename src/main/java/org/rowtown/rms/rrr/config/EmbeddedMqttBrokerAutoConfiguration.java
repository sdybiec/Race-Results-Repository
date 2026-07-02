package org.rowtown.rms.rrr.config;

import java.io.IOException;
import java.util.Properties;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.moquette.broker.Server;
import io.moquette.broker.config.IConfig;
import io.moquette.broker.config.MemoryConfig;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

/**
 * Auto-configuration for embedded Moquette MQTT broker.
 *
 * Enabled when mqtt.embedded.enabled=true
 *
 * This allows running the application without requiring an external MQTT broker.
 * Useful for:
 * - Development environments
 * - Single-server deployments
 * - Testing in production-like environments
 * - Demos and POCs
 *
 * For production deployments with high availability requirements,
 * use an external MQTT broker like Eclipse Mosquitto or HiveMQ.
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "mqtt.embedded.enabled", havingValue = "true")
@EnableConfigurationProperties(EmbeddedMqttBrokerAutoConfiguration.EmbeddedMqttBrokerProperties.class)
public class EmbeddedMqttBrokerAutoConfiguration {

    private Server mqttBroker;

    @Bean
    public Server embeddedMqttBroker(EmbeddedMqttBrokerProperties properties) throws IOException {
        log.info("Starting embedded Moquette MQTT broker");
        log.info("Host: {}, Port: {}, Anonymous: {}, Persistence: {}",
            properties.getHost(),
            properties.getPort(),
            properties.isAllowAnonymous(),
            properties.isPersistent() ? "enabled" : "disabled");

        Properties brokerProperties = new Properties();
        brokerProperties.setProperty(IConfig.HOST_PROPERTY_NAME, properties.getHost());
        brokerProperties.setProperty(IConfig.PORT_PROPERTY_NAME, String.valueOf(properties.getPort()));
        brokerProperties.setProperty(IConfig.ALLOW_ANONYMOUS_PROPERTY_NAME, String.valueOf(properties.isAllowAnonymous()));
        brokerProperties.setProperty(IConfig.ENABLE_TELEMETRY_NAME, String.valueOf(properties.isTelemetry()));

        if (properties.isPersistent() && properties.getDataPath() != null) {
            brokerProperties.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "true");
            brokerProperties.setProperty(IConfig.DATA_PATH_PROPERTY_NAME, properties.getDataPath());
            log.info("Persistent storage enabled at: {}", properties.getDataPath());
        } else {
            brokerProperties.setProperty(IConfig.PERSISTENCE_ENABLED_PROPERTY_NAME, "false");
            // Don't set DATA_PATH_PROPERTY_NAME when persistence is disabled to avoid file loading errors
            log.info("Using in-memory storage (no persistence)");
        }

        if (properties.getWebsocketPort() != null && properties.getWebsocketPort() > 0) {
            brokerProperties.setProperty(IConfig.WEB_SOCKET_PORT_PROPERTY_NAME, String.valueOf(properties.getWebsocketPort()));
            log.info("WebSocket enabled on port: {}", properties.getWebsocketPort());
        }

        IConfig config = new MemoryConfig(brokerProperties);
        mqttBroker = new Server();
        mqttBroker.startServer(config);

        log.info("Embedded Moquette MQTT broker started successfully");
        log.info("Clients can connect to: tcp://{}:{}", properties.getHost(), properties.getPort());

        return mqttBroker;
    }

    @PreDestroy
    public void shutdown() {
        if (mqttBroker != null) {
            log.info("Stopping embedded Moquette MQTT broker");
            mqttBroker.stopServer();
            log.info("Embedded MQTT broker stopped");
        }
    }

    /**
     * Configuration properties for embedded MQTT broker.
     */
    @ConfigurationProperties(prefix = "mqtt.embedded")
    public static class EmbeddedMqttBrokerProperties {

        /** Enable embedded MQTT broker */
        private boolean enabled = false;

        /** Broker host address */
        private String host = "0.0.0.0";

        /** Broker TCP port */
        private int port = 1883;

        /** Allow anonymous connections */
        private boolean allowAnonymous = true;

        /** Enable persistent storage */
        private boolean persistent = false;

        /** Path for persistent data storage */
        private String dataPath;

        /** Enable telemetry */
        private boolean telemetry = false;

        /** WebSocket port (optional) */
        private Integer websocketPort;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public boolean isAllowAnonymous() {
            return allowAnonymous;
        }

        public void setAllowAnonymous(boolean allowAnonymous) {
            this.allowAnonymous = allowAnonymous;
        }

        public boolean isPersistent() {
            return persistent;
        }

        public void setPersistent(boolean persistent) {
            this.persistent = persistent;
        }

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public boolean isTelemetry() {
            return telemetry;
        }

        public void setTelemetry(boolean telemetry) {
            this.telemetry = telemetry;
        }

        public Integer getWebsocketPort() {
            return websocketPort;
        }

        public void setWebsocketPort(Integer websocketPort) {
            this.websocketPort = websocketPort;
        }
    }
}
