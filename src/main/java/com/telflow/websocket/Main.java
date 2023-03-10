package com.telflow.websocket;

import com.inomial.secore.health.Healthcheck;
import com.inomial.secore.health.HealthcheckServer;
import com.inomial.secore.health.InitialisedHealthCheck;
import com.inomial.secore.health.kafka.KafkaHealthcheck;
import com.inomial.secore.kafka.MessageConsumer;
import com.inomial.secore.mon.MonitoringServer;
import com.inomial.secore.scope.Scope;
import com.telflow.factory.common.exception.InitialisationException;
import com.telflow.factory.common.helper.FabricHelper;
import com.telflow.factory.configuration.management.ConsulManager;
import com.telflow.websocket.listener.NotificationListener;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

/**
 * Main entry point to Quote Generator
 *
 * @author Sandeep Vasani
 */
public class Main {
    /**
     * health check
     */
    private static final InitialisedHealthCheck INITIALISED_HEALTH_CHECK = new InitialisedHealthCheck();

    /**
     * health check server
     */
    private static HealthcheckServer HEALTHCHECK;

    private static transient Logger LOG;

    private static final String APP_NAME = "quote-generator";

    private static final String HTTP = "http";

    private static MessageConsumer MESSAGE_CONSUMER;

    private static FabricHelper FABRIC_HELPER;

    private Main() {
        // do nothing
    }

    /**
     * The main entry point for the application.
     *
     * @param args Any command line arguments that have been passed.
     */
    public static void main(String[] args) {
        // Force libraries that use j.u.l. to use the slf4j handler:
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        LOG = LoggerFactory.getLogger(Main.class);

        LOG.info("Starting Quote generator");
        try {
            initConsul();
            HEALTHCHECK = new HealthcheckServer();

            setupQuoteGenerator();
            LOG.info("Quote generator started");
        } catch (Exception | Error ie) {
            LOG.error("Failed to initialise application, exiting.", ie);
            LOG.error("Properties: {}", System.getProperties());
            LOG.error("Env: {}", System.getenv());
            System.exit(1);
        }
    }

    private static void initConsul() {
        try {
            String consulEndpoint = System.getenv("CONSUL_SERVER");
            LOG.info("Consul Endpoint: {}", consulEndpoint);
            URL url = new URL(consulEndpoint);
            Map<String, String> defaults = getDefaults();
            ConsulManager.init(url, APP_NAME, defaults);
            ConsulManager.addRegisteredObject("taskNotifier", Main::setupQuoteGenerator);
        } catch (MalformedURLException mue) {
            LOG.error("Failed to initialise Consul: ", mue);
            throw new InitialisationException("Failed to initialise Consul", mue);
        }
    }

    private static void initFabricHelper() {
        String endpoint = String.format("%s://%s:%s/api/",
            ConsulManager.getEnvKey(ConsulKeys.ENV_FABRIC_PROTOCOL),
            ConsulManager.getEnvKey(ConsulKeys.ENV_FABRIC_HOST),
            ConsulManager.getEnvKey(ConsulKeys.ENV_FABRIC_PORT));
        LOG.info("Fabric: {}", endpoint);
        FABRIC_HELPER = new FabricHelper(
            endpoint,
            ConsulManager.getEnvKey(ConsulKeys.ENV_FABRIC_USER),
            ConsulManager.getEnvKey(ConsulKeys.ENV_FABRIC_PASSWORD)
        );
    }

    private static String getKafkaEndpoint() {
        return String.format("%s://%s:%s",
            ConsulManager.getEnvKey(ConsulKeys.ENV_KAFKA_PROTOCOL),
            ConsulManager.getEnvKey(ConsulKeys.ENV_KAFKA_HOST),
            ConsulManager.getEnvKey(ConsulKeys.ENV_KAFKA_PORT));
    }

    private static void setupQuoteGenerator() {
        INITIALISED_HEALTH_CHECK.starting();
        initFabricHelper();

        // Kafka configuration
        Map<String, Object> cp = new HashMap<>();
        cp.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, getKafkaEndpoint());
        cp.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        if (MESSAGE_CONSUMER != null) {
            MESSAGE_CONSUMER.shutdown();
        }
        MESSAGE_CONSUMER = new MessageConsumer(cp);
        
        WebsocketServer server = new WebsocketServer();

        NotificationListener listener = new NotificationListener(server);
        MESSAGE_CONSUMER.addMessageHandler(ConsulManager.getAppKey(ConsulKeys.APP_INBOX_TOPIC), listener, Scope.NONE);
        MESSAGE_CONSUMER.start(APP_NAME);
        INITIALISED_HEALTH_CHECK.initialised();
        startHealthCheckServer();
        if (LOG.isTraceEnabled()) {
            LOG.trace("Initialised a consumer for showcase quote generator: ID: {}, Name: {}",
                MESSAGE_CONSUMER.getId(), MESSAGE_CONSUMER.getName());
        }
    }

    private static Map<String, String> getDefaults() {
        ConsulManager.setAppName(APP_NAME);
        Map<String, String> defaultValues = new HashMap<>();
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.APP_INBOX_TOPIC), "telflow.notification");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.ENV_FABRIC_PROTOCOL), HTTP);
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.ENV_FABRIC_HOST), "10.232.3.118");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.ENV_FABRIC_PORT), "9797");
        defaultValues.put(ConsulManager.buildEnvKey(ConsulKeys.ENV_FABRIC_USER), "");
        defaultValues.put(ConsulManager.buildEnvKey(ConsulKeys.ENV_FABRIC_PASSWORD), "");
        defaultValues.put(ConsulManager.buildEnvKey(ConsulKeys.ENV_KAFKA_PROTOCOL), HTTP);
        defaultValues.put(ConsulManager.buildEnvKey(ConsulKeys.ENV_KAFKA_HOST), "10.232.3.118");
        defaultValues.put(ConsulManager.buildEnvKey(ConsulKeys.ENV_KAFKA_PORT), "9093");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.HEALTHCHECK_WAIT), "150");
        defaultValues.put(ConsulManager.buildAppKey(ConsulKeys.HEALTHCHECK_PORT),
            Integer.toString(MonitoringServer.DEFAULT_PORT));
        return defaultValues;
    }

    private static void startHealthCheckServer() {
        Map<String, Healthcheck> checks = new HashMap<>();
        checks.put("initialised", Main.INITIALISED_HEALTH_CHECK);

        checks.put("kafka", new KafkaHealthcheck(
            getKafkaEndpoint(),
            Long.parseLong(ConsulManager.getAppKey(ConsulKeys.HEALTHCHECK_WAIT))));

        long wait = Long.parseLong(ConsulManager.getAppKey(ConsulKeys.HEALTHCHECK_WAIT));
        int port = Integer.parseInt(ConsulManager.getAppKey(ConsulKeys.HEALTHCHECK_PORT));
        Main.HEALTHCHECK.startServer(APP_NAME, checks, wait, port);
    }
}
