package eu.cymo.as400.adapter;

import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;

import java.util.Map;
import java.util.Properties;

public class KafkaTopologyAvroTestBase {

    private static final String SCHEMA_REGISTRY_SCOPE = KafkaTopologyAvroTestBase.class.getName();
    private static final String MOCK_SCHEMA_REGISTRY_URL = "mock://" + SCHEMA_REGISTRY_SCOPE;



    protected static TopologyTestDriver createTestDriver(StreamsBuilder builder) {
        Topology topology = builder.build();

        // Dummy properties needed for test diver
        Properties topologyConfig = new Properties();
        topologyConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        topologyConfig.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:1234");
        topologyConfig.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class);
        topologyConfig.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        topologyConfig.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL);
        return new TopologyTestDriver(topology, topologyConfig);
    }

    protected Map<String,String> serdesConfig(){
        return Map.of(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, MOCK_SCHEMA_REGISTRY_URL);

    }



}
