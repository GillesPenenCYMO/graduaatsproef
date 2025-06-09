package eu.cymo.as400.adapter.streams;


import eu.cymo.as400.v2.DossierEvent;
import eu.cymo.as400.v2.DossierState;
import eu.cymo.as400.v2.events.*;
import eu.cymo.jade.v2.AangifteEvent;
import eu.cymo.jade.v2.events.AangifteGeregistreerd;
import eu.cymo.jade.v2.events.Opgewardeerd;
import eu.cymo.jade.v2.events.VerklaringGetekend;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;


public class DossierProcessorV2 implements Processor<String, AangifteEvent, String, DossierEvent> {

    private static final Logger logger = LogManager.getLogger(DossierProcessorV2.class);


    private ProcessorContext<String, DossierEvent> context;
    private KeyValueStore<String, eu.cymo.as400.v2.DossierState> dossierStore;

    @Override
    public void init(ProcessorContext<String, DossierEvent> context) {
        Processor.super.init(context);
        this.context = context;
        this.dossierStore = context.getStateStore("dossier-store");
    }

    @Override
    public void process(Record<String, AangifteEvent> record) {
        logger.info("Processing record: {}", record.key());

        if (String.valueOf(record.value().getEventType()).equals(AangifteGeregistreerd.getClassSchema().getName())) {
            dossierStore.put(record.key(), dossierStateFrom(String.valueOf(record.value().getAangifteId()), (AangifteGeregistreerd) record.value().getEvent()));
        } else if (String.valueOf(record.value().getEventType()).equals(Opgewardeerd.getClassSchema().getName())) {
            var dossier = dossierStore.get(record.key());
            if (Optional.ofNullable(dossier).isEmpty()){
                logger.info("Dossier {} not found", record.key());
                return;
            }
            var dossierIdAsString = String.valueOf(dossier.getId());
            var aangemaakt = DossierEvent.newBuilder()
                    .setDossierId(dossierIdAsString)
                    .setEventTimestamp(Instant.now())
                    .setEventType(DossierAangemaakt.getClassSchema().getName())
                    .setEvent(DossierAangemaakt.newBuilder()
                            .setKlant(dossier.getKlant())
                            .setVermoedelijkeDatum(dossier.getVermoedelijkeDatum())
                            .setDatumVerzendingAO(dossier.getDatumVerzendingAO())
                            .setVerwittigenVzmy(dossier.getVerwittigenVzmy())
                            .build())
                    .build();
            context.forward(new Record<>(dossierIdAsString, aangemaakt, record.timestamp()));

            int costCount = random(1,40);
            for (int i = 0; i < costCount; i++) {
                var bedrag = 5 + Math.random() * (5000 - 5);
                var costTs = Instant.now().plus(random(1,14), ChronoUnit.DAYS);
                var kost = DossierEvent.newBuilder()
                        .setEventTimestamp(costTs)
                        .setEvent(KostGemaakt.newBuilder()
                                .setDatum(costTs)
                                .setBedrag(bedrag)
                                .setOmschrijving("Medicamenten")
                                .build()
                        )
                        .setEventType(KostGemaakt.class.getName())
                        .setDossierId(dossierIdAsString)
                        .build();
                context.forward(new Record<>(dossierIdAsString,kost,record.timestamp()+ Duration.of(i, ChronoUnit.DAYS).getSeconds()));
                dossier = new eu.cymo.as400.v2.DossierState(dossierIdAsString,dossier.getKlant(),dossier.getDatumVerzendingAO(), dossier.getVermoedelijkeDatum(), dossier.getVerwittigenVzmy(), dossier.getOpenstaandBedrag()+bedrag);
                dossierStore.put(dossierIdAsString, dossier);

                if(i % 10 == 0) {
                    var opvraging = DossierEvent.newBuilder()
                            .setEventTimestamp(Instant.ofEpochMilli(record.timestamp()+ Duration.of(i, ChronoUnit.DAYS).getSeconds()*1000))
                            .setEvent(UitgavenstaatVerstuurd.newBuilder()
                                    .setBedragOpgevraagd(((double) random(50, 100)/100) * dossier.getOpenstaandBedrag())
                                    .setOntvanger(dossier.getKlant().getNaam())
                                    .build()
                            )
                            .setEventType(UitgavenstaatVerstuurd.class.getName())
                            .setDossierId(dossierIdAsString)
                            .build();
                    context.forward(new Record<>(dossierIdAsString,opvraging,record.timestamp()+ Duration.of(i, ChronoUnit.DAYS).getSeconds()*1000));
                }
            }
        }
    }

    private static eu.cymo.as400.v2.DossierState dossierStateFrom(String id, AangifteGeregistreerd event){
        var klantb = Klant.newBuilder()
                .setAdres(event.getKlant().getAdres())
                .setExternNr(event.getKlant().getExternNr())
                .setGeboorteDatum(event.getKlant().getGeboorteDatum())
                .setINSZ(event.getKlant().getINSZ())
                .setNaam(event.getKlant().getNaam())
                .setTaal(event.getKlant().getTaal())
                .setVoornaam(event.getKlant().getVoornaam());

        return new eu.cymo.as400.v2.DossierState(id, klantb.build(), event.getDatumVerzendingAO(), event.getVermoedelijkeDatum(), event.getVerwittigenVzmy(),0.0);
    }


    private static int random(int min, int max) {
        return new Random().nextInt(max - min + 1) + min;
    }
}

