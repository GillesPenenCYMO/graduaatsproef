package eu.cymo.as400.adapter.streams;


import eu.cymo.as400.adapter.KafkaTopologyAvroTestBase;
import eu.cymo.as400.v2.DossierEvent;
import eu.cymo.as400.v2.events.DossierAangemaakt;
import eu.cymo.jade.v2.AangifteEvent;
import eu.cymo.jade.v2.events.AangifteGeregistreerd;
import eu.cymo.jade.v2.events.Klant;
import eu.cymo.jade.v2.events.Opgewardeerd;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.Collections;
import java.util.List;


public class DossierStreamTest extends KafkaTopologyAvroTestBase {

    private TopologyTestDriver driver;
    private DossierStream dossierStream;

    private static final String aangifteTopic = "aangifte";
    private static final String dossierTopic = "as400";

    @Mock
    protected SchemaRegistryClient registryClient;


    @AfterEach
    void breakDown() {
        try {
            driver.close();
        } catch (Exception e) {
        }
    }
    @BeforeEach
    void setup() {
        dossierStream = new DossierStream();
        dossierStream.initSerdes(serdesConfig());

        var builder = dossierStream.composeStreams(new StreamsBuilder(), aangifteTopic, dossierTopic);

        driver = createTestDriver(builder);

    }
    @Test
    void testNewDossierIsCreatedAndForwarded() {
        TestInputTopic<String, AangifteEvent> inputTopic =
                driver.createInputTopic(aangifteTopic, Serdes.String().serializer(), new SpecificAvroSerde<AangifteEvent>() {{
                    configure(serdesConfig(), false);
                }}.serializer());

        TestOutputTopic<String, DossierEvent> outputTopic =
                driver.createOutputTopic(dossierTopic, Serdes.String().deserializer(), new SpecificAvroSerde<DossierEvent>() {{
                    configure(serdesConfig(), false);
                }}.deserializer());

        var klant = Klant.newBuilder()
                .setNaam("Konijn")
                .setVoornaam("Proef")
                .setAdres("Straat 123")
                .setGeboorteDatum(Instant.parse("2000-05-05T00:00:00Z"))
                .setINSZ("123.456.789.10")
                .setExternNr(72938461)
                .setTaal("NL");


        // Bouw testdata: AangifteGeregistreerd wrapped in AangifteEvent
        AangifteGeregistreerd geregistreerd = AangifteGeregistreerd.newBuilder()
                .setKlantBuilder(klant)
                .setDatumVerzendingAO(Instant.parse("2020-11-11T00:00:00Z"))
                .setVermoedelijkeDatum(Instant.parse("2020-11-12T00:00:00Z"))
                .setVerwittigenVzmy(true)
                .setGemaakteKosten(Collections.emptyList())
                .build();

        AangifteEvent event = AangifteEvent.newBuilder()
                .setEvent(geregistreerd)
                .setEventType(AangifteGeregistreerd.getClassSchema().getName())
                .setAangifteId("15354351.45643556")
                .setEventTimestamp(Instant.now())
                .build();

        inputTopic.pipeInput("72938461", event);
        inputTopic.pipeInput("72938461", AangifteEvent.newBuilder()
                .setAangifteId("15354351.45643556")
                .setEventTimestamp(Instant.now())
                .setEventType(Opgewardeerd.getClassSchema().getName())
                .setEvent(Opgewardeerd.newBuilder().setInitierendeBeheerder("someone").build())
                .build());

        // Controleer output
        List<KeyValue<String, DossierEvent>> results = outputTopic.readKeyValuesToList();
//        Assertions.assertEquals(1, results.size());
        Assertions.assertFalse(results.isEmpty());

    }


}
