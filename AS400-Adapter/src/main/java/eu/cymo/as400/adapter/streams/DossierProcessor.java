package eu.cymo.as400.adapter.streams;

import eu.cymo.as400.v2.DossierEvent;
import eu.cymo.as400.v2.events.*;
import eu.cymo.jade.v2.AangifteEvent;
import eu.cymo.jade.v2.events.AangifteGeregistreerd;
import eu.cymo.jade.v2.events.BeheerderToegevoegd;
import eu.cymo.jade.v2.events.VerklaringGetekend;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


public class DossierProcessor implements Processor<String, AangifteEvent, String, DossierEvent> {

    private static final Logger logger = LogManager.getLogger(DossierProcessor.class);


    private ProcessorContext<String, DossierEvent> context;
    private KeyValueStore<String, DossierEvent> dossierStore;


    @Override
    public void init(ProcessorContext<String, DossierEvent> context) {
        Processor.super.init(context);
        this.context = context;
        this.dossierStore = context.getStateStore("dossier-store");
    }

    @Override
    public void process(Record<String, AangifteEvent> record) {
        logger.info("Processing record: {}", record.key());



        AangifteEvent aangifteEvent = record.value();
        if (isNewDossier(aangifteEvent)) {

            AangifteGeregistreerd geregistreerd = (AangifteGeregistreerd) aangifteEvent.getEvent();
            String dossierId = UUID.randomUUID().toString();

            var klantb = Klant.newBuilder()
                    .setAdres(geregistreerd.getKlant().getAdres())
                    .setExternNr(geregistreerd.getKlant().getExternNr())
                    .setGeboorteDatum(geregistreerd.getKlant().getGeboorteDatum())
                    .setINSZ(geregistreerd.getKlant().getINSZ())
                    .setNaam(geregistreerd.getKlant().getNaam())
                    .setTaal(geregistreerd.getKlant().getTaal())
                    .setVoornaam(geregistreerd.getKlant().getVoornaam());

            // Bouw een DossierAangemaakt event
            DossierAangemaakt dossierAangemaakt = DossierAangemaakt.newBuilder()
                    .setKlantBuilder(klantb)
                    .setDatumVerzendingAO(geregistreerd.getDatumVerzendingAO())
                    .setVermoedelijkeDatum(geregistreerd.getVermoedelijkeDatum())
                    .setVerwittigenVzmy(geregistreerd.getVerwittigenVzmy())
                    .build();

            DossierEvent dossierEvent = DossierEvent.newBuilder()
                    .setDossierId(dossierId)
                    .setEvent(dossierAangemaakt)
                    .setEventTimestamp(Instant.now())
                    .setEventType(DossierAangemaakt.getClassSchema().getName())
                    .build();

            dossierStore.put(record.key(), dossierEvent);
            context.forward(new Record<>(record.key(), dossierEvent, record.timestamp()));
            logger.info("Nieuw DossierAangemaakt-event gecreëerd en geforward: {}", record.key());
        } else {
            logger.info("Event is geen AangifteGeregistreerd, geen dossier wordt aangemaakt.");
            logger.info("fgdfgdfgdfgdfgdfgdfgdgdgdfdfgdfdfg");
            return;
        }

        DossierEvent origineleDossier = dossierStore.get(record.key());
        DossierEvent dossier = dossierStore.get(record.key());
        logger.info("checking what is in dossier: {}", dossier);

        if (origineleDossier == null) {
            logger.warn("Geen dossier gevonden in store voor key {}", record.key());
            return;
        }

        if(HasNoBetwisting(origineleDossier)) {
            try {
                logger.info("Adding betwisting  for key: {}", record.key());

                DossierEvent updated = BetwistingVanDossier(origineleDossier);

                if(updated.getEvent() instanceof DossierBetwist BetwistingEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                            .setDossierId(UUID.randomUUID().toString())
                            .setEventTimestamp(Instant.now())
                            .setEvent(
                                    DossierBetwist.newBuilder()
                                            .setRedenBetwisting(BetwistingEvent.getRedenBetwisting())
                                            .build()
                            )
                            .setEventType(DossierBetwist.getClassSchema().getName())
                            .build();
                    dossierStore.put(record.key(),  event);
                    context.forward(new Record<>(event.getDossierId().toString(), event , record.timestamp()));
                }else {
                    logger.warn("Expected RedenBetwisting, but got: {} ", updated.getEvent().getClass().getSimpleName());
                }

            }catch (Exception ex){
                logger.error("FOUT bij toevoegen betwisting voor key" + record.key(), ex);
            }
        }

        if(hasNoVerzekeringMaatschappijDerdeVerwittigd(origineleDossier)){
            try{

                logger.info("adding vzmij verwittigd for key: {}", record.key());

                DossierEvent updated = VerzekeringMaatschappijDerdeVerwittigd(origineleDossier);

                if(updated.getEvent() instanceof VerzekeringsmaatschappijDerdeVerwittigd VerwittigdEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                            .setDossierId(UUID.randomUUID().toString())
                            .setEventTimestamp(Instant.now())
                            .setEvent(
                                    VerzekeringsmaatschappijDerdeVerwittigd.newBuilder()
                                            .setVerzekeraarNaam(VerwittigdEvent.getVerzekeraarNaam())
                                            .setPolisNummer(VerwittigdEvent.getPolisNummer())
                                            .build()
                            )
                            .setEventType(VerzekeringsmaatschappijDerdeVerwittigd.getClassSchema().getName())
                            .build();
                    dossierStore.put(record.key(), event);
                    context.forward((new Record<>(event.getDossierId().toString(), event, record.timestamp())));

                }else {
                logger.warn("Expected VerzekeringMaatschappijDerdeVerwittigd, but got: {} ", updated.getEvent().getClass().getSimpleName());
                }
            }catch (Exception ex){
            logger.error("FOUT bij toevoegen van vzmij verwittigd voor key" + record.key(), ex);
             }
        }

        if(hasNoInfoOpgevraagd(origineleDossier)){
            try{
                logger.info("add extra info opgevraagd for key: {}", record.key());
                DossierEvent updated = InfoOpgevraagd(origineleDossier);

                if(updated.getEvent() instanceof InfoOpgevraagd InfoEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                                    .setDossierId(UUID.randomUUID().toString())
                                    .setEventTimestamp(Instant.now())
                                    .setEvent(
                                                            InfoOpgevraagd.newBuilder()
                                                            .setInfoType(InfoEvent.getInfoType())
                                                            .setOpgevraagdBij(InfoEvent.getOpgevraagdBij())
                                                            .build()
                                    )
                            .setEventType(InfoOpgevraagd.getClassSchema().getName())
                            .build();

                dossierStore.put(record.key(), event);
                context.forward((new Record<>(event.getDossierId().toString(), event, record.timestamp())));

            }else {
                logger.warn("Expected InfoOpgevraagd, but got: {} ", updated.getEvent().getClass().getSimpleName());
            }
        }catch (Exception ex){
            logger.error("FOUT bij toevoegen van opgevraagde info voor key" + record.key(), ex);
        }
        }

        if(hasNoDossierIngekeken(origineleDossier)){
            try{
                logger.info("add dossier ingekeken for key: {}", record.key());
                DossierEvent updated = DossierIngekeken(origineleDossier);

                if(updated.getEvent() instanceof DossierIngekeken IngekekenEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                                    .setDossierId(UUID.randomUUID().toString())
                                            .setEventTimestamp(Instant.now())
                                                    .setEvent(
                                                            DossierIngekeken.newBuilder()
                                                                    .setIngezienDoor(IngekekenEvent.getIngezienDoor())
                                                                    .setInkijkdatum(IngekekenEvent.getInkijkdatum())
                                                                    .build()
                                                    )
                            .setEventType(DossierIngekeken.getClassSchema().getName())
                            .build();


                dossierStore.put(record.key(), event);
                context.forward((new Record<>(event.getDossierId().toString(), event, record.timestamp())));

            }else {
                logger.warn("Expected DossierIngekeken, but got: {} ", updated.getEvent().getClass().getSimpleName());
            }
        }catch (Exception ex){
            logger.error("FOUT bij toevoegen van ingekeken dossier door persoon voor key" + record.key(), ex);
        }
        }

        if(hasNoDossierAfgesloten(origineleDossier)){
            try{
                logger.info("add dossier afgesloten for key: {}", record.key());
                DossierEvent updated = DossierAfgesloten(origineleDossier);

                if (updated.getEvent() instanceof DossierAfgesloten AfgeslotenEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                                    .setDossierId(UUID.randomUUID().toString())
                                    .setEventTimestamp(Instant.now())
                                            .setEvent(
                                                    DossierAfgesloten.newBuilder()
                                                            .setRedenAfsluiting(AfgeslotenEvent.getRedenAfsluiting())
                                                            .build()
                                            )
                            .setEventType(DossierAfgesloten.getClassSchema().getName())
                            .build();



                dossierStore.put(record.key(), event);
                context.forward((new Record<>(event.getDossierId().toString(), event, record.timestamp())));

            }else {
                logger.warn("Expected Dossierafgesloten, but got: {} ", updated.getEvent().getClass().getSimpleName());
            }
        }catch (Exception ex){
            logger.error("FOUT bij toevoegen van afgesloten dossier door persoon voor key" + record.key(), ex);
        }
        }

        if(hasNoDossierGelinkt(origineleDossier)){
            try{
                logger.info("add dossier gelinkt for key: {}", record.key());
                DossierEvent updated = DossierGelinkt(origineleDossier);

                if (updated.getEvent() instanceof DossierGelinkt GelinktEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                            .setDossierId(UUID.randomUUID().toString())
                            .setEventTimestamp(Instant.now())
                            .setEvent(
                                    DossierGelinkt.newBuilder()
                                            .setGekoppeldAanDossierId(GelinktEvent.getGekoppeldAanDossierId())
                                            .setReden(GelinktEvent.getReden())
                                            .build()
                            )
                            .setEventType(DossierGelinkt.getClassSchema().getName())
                            .build();



                    dossierStore.put(record.key(), event);
                    context.forward((new Record<>(event.getDossierId().toString(), event, record.timestamp())));

                }else {
                    logger.warn("Expected DossierGelinkt, but got: {} ", updated.getEvent().getClass().getSimpleName());
                }
            }catch (Exception ex){
                logger.error("FOUT bij toevoegen van gelinkte dossier door persoon voor key" + record.key(), ex);
            }
        }

        if(hasNoUitgavenstaatVerstuurd(origineleDossier)){
            try{
                logger.info("add dossier uitgavenstaatverstuurd for key: {}", record.key());
                DossierEvent updated = UitgavenstaatVerstuurd(origineleDossier);

                if (updated.getEvent() instanceof UitgavenstaatVerstuurd UitgavenEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                            .setDossierId(UUID.randomUUID().toString())
                            .setEventTimestamp(Instant.now())
                            .setEvent(
                                    UitgavenstaatVerstuurd.newBuilder()
                                            .setBedragOpgevraagd(UitgavenEvent.getBedragOpgevraagd())
                                            .setOntvanger(UitgavenEvent.getOntvanger())
                                            .build()
                            )
                            .setEventType(UitgavenstaatVerstuurd.getClassSchema().getName())
                            .build();



                    dossierStore.put(record.key(), event);
                    context.forward((new Record<>(event.getDossierId().toString(), event, record.timestamp())));

                }else {
                    logger.warn("Expected UitgavenStaatVerstuurd, but got: {} ", updated.getEvent().getClass().getSimpleName());
                }
            }catch (Exception ex){
                logger.error("FOUT bij toevoegen van Uitgavenerstuurd dossier door persoon voor key" + record.key(), ex);
            }
        }

        if(HasNoGeldOntvangen(origineleDossier)){
            try{
                logger.info("add dossier Geld Ontvangen for key: {}", record.key());
                DossierEvent updated = GeldOntvangen(origineleDossier);

                if (updated.getEvent() instanceof GeldOntvangen GeldEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                            .setDossierId(UUID.randomUUID().toString())
                            .setEventTimestamp(Instant.now())
                            .setEvent(
                                    GeldOntvangen.newBuilder()
                                            .setBedrag(GeldEvent.getBedrag())
                                            .setValuta(GeldEvent.getValuta())
                                            .setRekeningnummerVan(GeldEvent.getRekeningnummerVan())
                                            .setRekeningnummerOntvangenOp(GeldEvent.getRekeningnummerOntvangenOp())
                                            .setVan(GeldEvent.getVan())
                                            .build()
                            )
                            .setEventType(GeldOntvangen.getClassSchema().getName())
                            .build();



                    dossierStore.put(record.key(), event);
                    context.forward((new Record<>(event.getDossierId().toString(), event, record.timestamp())));

                }else {
                    logger.warn("Expected GeldOntvangen event, but got: {} ", updated.getEvent().getClass().getSimpleName());
                }
            }catch (Exception ex){
                logger.error("FOUT bij toevoegen van geld ontvangen dossier door persoon voor key" + record.key(), ex);
            }
        }

        if(HasNoInfoOntvangen(origineleDossier)){
            try{
                logger.info("add extra info for dossier met key: {}", record.key());
                DossierEvent updated = InfoOntvangen(origineleDossier);

                if (updated.getEvent() instanceof InfoOntvangen InfoEvent){
                    DossierEvent event = DossierEvent.newBuilder()
                            .setDossierId(UUID.randomUUID().toString())
                            .setEventTimestamp(Instant.now())
                            .setEvent(
                                    InfoOntvangen.newBuilder()
                                            .setAfzender(InfoEvent.getAfzender())
                                            .setBeschrijving(InfoEvent.getBeschrijving())
                                            .setInfoType(InfoEvent.getInfoType())
                                            .build()
                            )
                            .setEventType(InfoOntvangen.getClassSchema().getName())
                            .build();



                    dossierStore.put(record.key(), event);
                    context.forward((new Record<>(event.getDossierId().toString(), event, record.timestamp())));

                }else {
                    logger.warn("Expected DossierIngekeken, but got: {} ", updated.getEvent().getClass().getSimpleName());
                }
            }catch (Exception ex){
                logger.error("FOUT bij toevoegen van ingekeken dossier door persoon voor key" + record.key(), ex);
            }
        }

    }





    @Override
    public void close() {
        Processor.super.close();
    }



    private static final Random RANDOM = new Random();
    public static long NumberGenerator() {
        Random random = new Random();

        int aantal = random.nextInt(7) + 6;

        long min = (long) Math.pow(10, aantal - 1);
        long max = (long) Math.pow(10, aantal) - 1;

        return min + ((long)(random.nextDouble() * (max - min + 1)));
    }


    public static boolean isNewDossier(AangifteEvent a) {
        return a.getEvent() instanceof AangifteGeregistreerd;
    }


    public static boolean HasNoBetwisting(DossierEvent d) {

        if (d == null) {
            logger.info("Dossier is null, dus geen betwisting.");
            return true;  // Of false, afhankelijk van je logica
        }

        if (d.getEvent() instanceof DossierAangemaakt aangemaakt) {
            int externNr = aangemaakt.getKlant().getExternNr();

            if (externNr == 81000128 || externNr == 95432680 || externNr == 84321975 || externNr == 72938461) {
                logger.info("Het dossier heeft geen betwisting.");
                return false;
            } else {
                logger.info("d.getEvent() = {}", d.getEvent());
                return !(d.getEvent() instanceof DossierBetwist);
            }
        } else {
            logger.warn("Dossier event is niet van het type DossierAangemaakt maar van: {}", d.getEvent().getClass().getSimpleName());
            return true;
        }

    }
    public static DossierEvent BetwistingVanDossier(DossierEvent origineel){
        DossierBetwist db = new DossierBetwist();

        DossierAangemaakt dg = (DossierAangemaakt) origineel.getEvent();
        int externNr = dg.getKlant().getExternNr();

        if( externNr == 64298564 ){
            db.setRedenBetwisting("We gaan niet akkoord. De oorzaak van het ongeval is het opzettelijke sabotage door een derde persoon. Dit heeft niets te maken met een arbeidsongeval.");
        }

        return DossierEvent.newBuilder(origineel)
                .setEvent(db)
                .build();

    }

    public static boolean  hasNoVerzekeringMaatschappijDerdeVerwittigd(DossierEvent d){
        DossierAangemaakt aangemaakt = (DossierAangemaakt) d.getEvent();

        int externNr = aangemaakt.getKlant().getExternNr();
        if( externNr ==72938461 || externNr == 84321975 || externNr == 95432680 ){
            logger.info("Het dossier heeft momenteel geen nood aan de vzmij te verwittigen.");
            return false;
        }else{
            logger.info("d.getEvent() = {}", d.getEvent());
            return !(d.getEvent() instanceof DossierBetwist);
        }
    }
    public static DossierEvent VerzekeringMaatschappijDerdeVerwittigd(DossierEvent origineel){
        VerzekeringsmaatschappijDerdeVerwittigd vzmij = new VerzekeringsmaatschappijDerdeVerwittigd();
        long nummer = NumberGenerator();
        CharSequence polisNummer = String.valueOf(nummer);

        DossierAangemaakt dg = (DossierAangemaakt)  origineel.getEvent();
        int externNr = dg.getKlant().getExternNr();
        switch(externNr){
            case 81000128:
                vzmij.setPolisNummer(polisNummer);
                vzmij.setVerzekeraarNaam("Ethias");
                break;
            case 64298564:
                vzmij.setPolisNummer(polisNummer);
                vzmij.setVerzekeraarNaam("KBC");
                break;
        }
        return DossierEvent.newBuilder(origineel)
                .setEvent(vzmij)
                .build();
    }

    public static boolean hasNoDossierIngekeken(DossierEvent d){
        if(d == null){
            logger.error("geen dossier aangeduid.");
            return false;
        }
        else{
            logger.info("d.getEvent() = {}", d.getEvent());
            return !(d.getEvent() instanceof DossierIngekeken);
        }
    }
    public static DossierEvent DossierIngekeken(DossierEvent origineel){
        DossierIngekeken i = new DossierIngekeken();



        DossierAangemaakt dg = (DossierAangemaakt)  origineel.getEvent();
        int externNr = dg.getKlant().getExternNr();
        switch(externNr){
            case 81000128:
               i.setIngezienDoor("Lynn Vandecasteele");
               i.setInkijkdatum(Instant.parse("2023-10-10T00:00:00Z"));
                break;
            case 95432680:
                i.setIngezienDoor("Bart Jacobs");
                i.setInkijkdatum(Instant.parse("2025-09-16T00:00:00Z"));
                break;
            case 64298564:
                i.setIngezienDoor("Eva De Neve");
                i.setInkijkdatum(Instant.parse("2024-11-16T00:00:00Z"));
                break;
            case 84321975:
                i.setIngezienDoor("Thomas Peeters");
                i.setInkijkdatum(Instant.parse("2023-09-09T00:00:00Z"));
                break;
            case 72938461:
                i.setIngezienDoor("Sofie De Bruyn");
                i.setInkijkdatum(Instant.parse("2024-05-23T00:00:00Z"));
                break;
        }
        return DossierEvent.newBuilder(origineel)
                .setEvent(i)
                .build();
    }

    public  static boolean hasNoDossierAfgesloten(DossierEvent d){
        DossierAangemaakt aangemaakt = (DossierAangemaakt) d.getEvent();

        int externNr = aangemaakt.getKlant().getExternNr();
        if( externNr ==72938461 || externNr == 84321975 || externNr == 95432680 || externNr == 64298564) {
            logger.info("Dossier is nog niet afgesloten.");
            return false;
        }else{
            logger.info("d.getEvent() = {}", d.getEvent());
            return !(d.getEvent() instanceof DossierBetwist);
        }
    }
    public static DossierEvent DossierAfgesloten(DossierEvent origineel){
        DossierAfgesloten da = new DossierAfgesloten();

        DossierAangemaakt dg = (DossierAangemaakt) origineel.getEvent();
        int externNr = dg.getKlant().getExternNr();
        da.setRedenAfsluiting("Geen geld meer op te halen.");

        return DossierEvent.newBuilder(origineel)
                .setEvent(da)
                .build();

    }


    public static boolean hasNoInfoOpgevraagd(DossierEvent d){
        if (d == null) {
            logger.error("geen dossier aangeduid.");
            return false;
        } else {
            logger.info("d.getEvent() = {}", d.getEvent());
            return !(d.getEvent() instanceof VerklaringGetekend);
        }
    }
    public static DossierEvent InfoOpgevraagd(DossierEvent origineel){
       InfoOpgevraagd io = new InfoOpgevraagd();

        DossierAangemaakt dg = (DossierAangemaakt)  origineel.getEvent();
        int externNr = dg.getKlant().getExternNr();
        switch(externNr){
            case 81000128:
                io.setInfoType("medisch");
                io.setOpgevraagdBij("Dokter van klant");
                break;
            case 95432680:
                io.setInfoType("expert");
                io.setOpgevraagdBij("Dokter van klant");
                break;
            case 64298564:
                io.setInfoType("schade");
                io.setOpgevraagdBij("Garagist van de klant");
                break;
            case 84321975:
            case 72938461:
                io.setInfoType("medisch");
                io.setOpgevraagdBij("Chirurg van klant");
                break;
        }
        return DossierEvent.newBuilder(origineel)
                .setEvent(io)
                .build();

    }


    public static boolean hasNoDossierGelinkt(DossierEvent d){
        DossierAangemaakt aangemaakt = (DossierAangemaakt) d.getEvent();

        int externNr = aangemaakt.getKlant().getExternNr();
        if( externNr ==72938461 || externNr == 84321975 || externNr == 64298564) {
            logger.info("Dossier heeft geen link met andere dossieren.");
            return false;
        }else{
            logger.info("d.getEvent() = {}", d.getEvent());
            return !(d.getEvent() instanceof DossierBetwist);
        }
    }
    public static DossierEvent DossierGelinkt(DossierEvent origineel){
      DossierGelinkt dg = new DossierGelinkt();

      DossierAangemaakt da = (DossierAangemaakt) origineel.getEvent();
      int externNr = da.getKlant().getExternNr();
        switch(externNr){
            case 81000128:
                dg.setGekoppeldAanDossierId("DOS-" + (10000 + RANDOM.nextInt(90000)));
                dg.setReden("verwante schade");
                break;
            case 95432680:
                dg.setGekoppeldAanDossierId("DOS-" + (10000 + RANDOM.nextInt(90000)));
                dg.setReden("Verwante betrokkene of schadegeval");
                break;

        }
        return DossierEvent.newBuilder(origineel)
                .setEvent(dg)
                .build();

    }


    public static boolean hasNoUitgavenstaatVerstuurd(DossierEvent d){
        if (d == null) {
            logger.error("geen dossier aangeduid.");
            return false;
        } else {
            logger.info("d.getEvent() = {}", d.getEvent());
            return !(d.getEvent() instanceof VerklaringGetekend);
        }
    }
    public static DossierEvent UitgavenstaatVerstuurd(DossierEvent origineel){
        UitgavenstaatVerstuurd uv = new UitgavenstaatVerstuurd();



        DossierAangemaakt dg = (DossierAangemaakt)  origineel.getEvent();
        double bedrag = 50 + (RANDOM.nextDouble() * 950);


        int externNr = dg.getKlant().getExternNr();
        switch(externNr){
            case 81000128:
            case 95432680:
                uv.setOntvanger("klant");
                uv.setBedragOpgevraagd(bedrag);
                break;
            case 64298564:
            case 84321975:
                uv.setOntvanger("Verzekeringmaatschapij KBC");
                uv.setBedragOpgevraagd(bedrag);
                break;
            case 72938461:
                uv.setOntvanger("Derde partij");
                uv.setBedragOpgevraagd(bedrag);
                break;
        }
       return  DossierEvent.newBuilder(origineel)
               .setEvent(uv)
               .build();


    }


    public static boolean HasNoGeldOntvangen(DossierEvent d){
        if (d == null) {
            logger.error("geen dossier aangeduid.");
            return false;
        } else {
            logger.info("d.getEvent() = {}", d.getEvent());
            return !(d.getEvent() instanceof VerklaringGetekend);
        }
    }
    public static DossierEvent GeldOntvangen(DossierEvent origineel){
        GeldOntvangen go = new GeldOntvangen();



        DossierAangemaakt dg = (DossierAangemaakt)  origineel.getEvent();
        double bedrag = 50 + (RANDOM.nextDouble() * 950); // tussen 50 en 1000 EUR
        String[] rekeningnummers = {"BE12 3456 7890 1234", "BE98 7654 3210 9876", "BE00 1111 2222 3333"};
        String rekeningVan = rekeningnummers[RANDOM.nextInt(rekeningnummers.length)];

        int externNr = dg.getKlant().getExternNr();
        switch(externNr){
            case 81000128:
                go.setBedrag(bedrag);
                go.setValuta("Euro");
                go.setRekeningnummerOntvangenOp("BE88 2156 4538 9754");
                go.setRekeningnummerVan(rekeningVan);
                go.setVan("Vzmij");
                break;

            case 95432680:
                go.setBedrag(bedrag);
                go.setValuta("Euro");
                go.setRekeningnummerOntvangenOp("BE88 2136 3456 4567");
                go.setRekeningnummerVan(rekeningVan);
                go.setVan("Vzmij");
                break;

            case 64298564:
                go.setBedrag(bedrag);
                go.setValuta("Euro");
                go.setRekeningnummerOntvangenOp("BE88 4654 4354 9743");
                go.setRekeningnummerVan(rekeningVan);
                go.setVan("Derde Persoon");
                break;

            case 84321975:
                go.setBedrag(bedrag);
                go.setValuta("Euro");
                go.setRekeningnummerOntvangenOp("BE88 0001 2345 6789");
                go.setRekeningnummerVan(rekeningVan);
                go.setVan("Vzmij");
                break;

            case 72938461:
                go.setBedrag(bedrag);
                go.setValuta("Euro");
                go.setRekeningnummerOntvangenOp("BE88 4512 1234 3976");
                go.setRekeningnummerVan(rekeningVan);
                go.setVan("Vzmij");
                break;
        }
        return  DossierEvent.newBuilder(origineel)
                .setEvent(go)
                .build();
    }


    public  static boolean HasNoInfoOntvangen(DossierEvent d){
        if (d == null) {
            logger.error("geen dossier aangeduid.");
            return false;
        } else {
            logger.info("d.getEvent() = {}", d.getEvent());
            return !(d.getEvent() instanceof VerklaringGetekend);
        }
    }
    public static DossierEvent InfoOntvangen(DossierEvent origineel){

        logger.info("FHSDHFSB?S?W%%KSROHQNLKHW?GDHQBDLHHQJDFGJQELH");
        if (!(origineel.getEvent() instanceof DossierAangemaakt)) {
            throw new IllegalStateException("Kan InfoOntvangen niet genereren: event is geen DossierAangemaakt, maar " + origineel.getEvent().getClass().getSimpleName());
        }

        DossierAangemaakt dg = (DossierAangemaakt)  origineel.getEvent();

        InfoOntvangen io = new InfoOntvangen();

        String[] types = {"medisch", "tegenpartij", "bijkomende inlichtingen"};
        String[] afzenders = {"Klant", "Ziekenhuis", "Expert NV", "Advocaat Janssens"};

        String type = types[RANDOM.nextInt(types.length)];
        String afzender = afzenders[RANDOM.nextInt(afzenders.length)];
        String beschrijving = "Ontvangen " + type + " info van " + afzender;
        int externNr = dg.getKlant().getExternNr();
        switch(externNr){
            case 81000128:
            case 95432680:
            case 64298564:
            case 84321975:
            case 72938461:
                io.setAfzender(afzender);
                io.setInfoType(type);
                io.setBeschrijving(beschrijving);
                break;
        }
        return  DossierEvent.newBuilder(origineel)
                .setEvent(io)
                .build();


    }



}

