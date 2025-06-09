package eu.cymo.as400.adapter.streams;


import eu.cymo.as400.v2.DossierEvent;
import eu.cymo.as400.v2.DossierState;
import eu.cymo.jade.v2.AangifteEvent;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.Stores;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class DossierStream {

    private static final Logger logger = LogManager.getLogger(DossierStream.class);

    @Value("${spring.kafka.properties.schema.registry.url}")
    String schemaregUrl;
    @Value("${spring.kafka.properties.basic.auth.credentials.source}")
    String schemaSource;
    @Value("${spring.kafka.properties.schema.registry.basic.auth.user.info}")
    String schemaCreds;

    @Value(value = "${kafka.topic.aangifte}")
    private String aangifteTopic;

    @Value(value = "${kafka.topic.dossier}")
    private String dossierTopic;

    // Serdes
    private static final Serde<AangifteEvent> AANGIFTE_SPECIFIC_AVRO_SERDE = new SpecificAvroSerde<>();
    private static final Serde<DossierEvent> DOSSIER_EVENT_SERDE = new SpecificAvroSerde<>();
    private static final Serde<DossierState> DOSSIER_STATE_SERDE = new SpecificAvroSerde<>();

    AtomicInteger counter = new AtomicInteger();

    protected void initSerdes(Map<String, String> serdesConfig){
        AANGIFTE_SPECIFIC_AVRO_SERDE.configure(serdesConfig, false);
        DOSSIER_EVENT_SERDE.configure(serdesConfig, false);
        DOSSIER_STATE_SERDE.configure(serdesConfig, false);
    }

    public StreamsBuilder composeStreams(StreamsBuilder builder, String aangifteTopic, String dossierTopic) {
        builder
                .addStateStore(Stores.keyValueStoreBuilder(
                                Stores.inMemoryKeyValueStore("dossier-store"),
                                Serdes.String(),
                                DOSSIER_STATE_SERDE
                        )
                );

        builder
                .stream(aangifteTopic, Consumed.with(Serdes.String(), AANGIFTE_SPECIFIC_AVRO_SERDE))
                .process(DossierProcessor::new, "dossier-store")
//              .process(DossierProcessorV2::new, "dossier-store")
                .to(dossierTopic, Produced.with(Serdes.String(), DOSSIER_EVENT_SERDE));
        return builder;
    }


    @Autowired
    void buildPipeline(StreamsBuilder streamsBuilder) {
        initSerdes(Map.of(
                "schema.registry.url", schemaregUrl,
                "basic.auth.credentials.source", schemaSource,
                "schema.registry.basic.auth.user.info", schemaCreds
        ));
        composeStreams(streamsBuilder, aangifteTopic, dossierTopic);
    }

}
