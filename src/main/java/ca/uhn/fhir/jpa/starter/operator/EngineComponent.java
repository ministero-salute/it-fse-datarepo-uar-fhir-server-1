package ca.uhn.fhir.jpa.starter.operator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationDefinition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.api.SortOrderEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;

@Component
public class EngineComponent implements IResourceProvider {

    private static final Logger log = LoggerFactory.getLogger(EngineComponent.class);

    @Autowired
    private DaoRegistry daoRegistry;

    @Override
    public Class<OperationDefinition> getResourceType() {
        return OperationDefinition.class;
    }

    /**
     * Entry point principale.
     *
     * @param nomeOperationCustom  nome della OperationDefinition (es. "GRAVIDANZA")
     * @param codiceFiscale        CF del medico/publisher (usato sia per trovare la OpDef
     *                             sia come identifier del Patient)
     * @param patientIdentifierSystem  system dell'identifier del Patient (es. "http://hl7.it/sid/cf")
     */
    /**
     * @param nomeOperationCustom   nome della OperationDefinition (es. "BASE", "GRAVIDANZA")
     * @param publisher             codice regione (es. "120") — usato SOLO per trovare la OpDef
     * @param codiceFiscale         CF dell'assistito — usato per risolvere il Patient e fetchare le risorse
     * @param patientIdentifierSystem  system FHIR del CF (es. "http://hl7.it/sid/cf")
     */
    public Bundle getBundle(String nomeOperationCustom, String publisher, String codiceFiscale, String patientIdentifierSystem) {

        // 1. Trova OpDef tramite nome + publisher (codice regione)
        OperationDefinition opDef = cercaOperationDefinition(nomeOperationCustom, publisher);

        // 2. Estrai ValueSet dalla OpDef
        List<String> valuesetResourceTypes     = estraiValueFromValueSetContained(opDef, "#vs-resources");
        List<Coding> valuesetObservationCodes  = estraiCodingsFromValueSetContained(opDef, "#vs-observations");

        log.info("Tipi risorsa: {} | Codici Observation: {}", valuesetResourceTypes, valuesetObservationCodes.size());

        // 3. Risolvi il Patient tramite CF dell'assistito (NON il publisher/regione)
        String patientId = findPatientIdByIdentifier(patientIdentifierSystem, codiceFiscale);
        log.info("Patient HAPI ID: {}", patientId);

        List<Resource> allResources = new ArrayList<>();

        // 4. Fetch risorse
        for (String resourceType : valuesetResourceTypes) {
            if ("Observation".equalsIgnoreCase(resourceType)) {
                List<Resource> observations = fetchObservationsByCodesAndPatient(patientId, valuesetObservationCodes);
                log.info("Trovate {} Observation per patientId={}", observations.size(), patientId);
                allResources.addAll(observations);
            } else {
                Resource last = fetchLastResourceByPatient(resourceType, patientId);
                if (last != null) {
                    log.info("Ultima risorsa tipo={} per patientId={}", resourceType, patientId);
                    allResources.add(last);
                }
            }
        }

        // 5. Costruisci Bundle
        return buildBundle(allResources);
    }
     

    // =========================================================================
    // Risoluzione Patient: identifier (system|value) → HAPI logical ID
    // =========================================================================

    /**
     * Cerca il Patient sul server HAPI tramite il suo identifier.
     * Lancia ResourceNotFoundException se non trovato.
     */
    private String findPatientIdByIdentifier(String system, String value) {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);

        SearchParameterMap params = new SearchParameterMap();
        params.add(Patient.SP_IDENTIFIER, new TokenParam(system, value));
        params.setCount(1);

        IBundleProvider result = patientDao.search(params);

        if (result.isEmpty()) {
            throw new ResourceNotFoundException(
                "Nessun Patient trovato con identifier " + system + "|" + value);
        }

        Patient patient = (Patient) result.getResources(0, 1).get(0);
        String patientId = patient.getIdElement().getIdPart();
        log.info("Patient trovato: id={}", patientId);
        return patientId;
    }

    // =========================================================================
    // Fetch ultima risorsa (non-Observation) per patient
    // =========================================================================

    /**
     * Recupera l'ultima risorsa del tipo specificato associata al Patient,
     * ordinando per _lastUpdated DESC e prendendo solo il primo risultato.
     *
     * Nota: usa il search parameter "patient" che è standard per la maggior
     * parte dei resourceType clinici (DiagnosticReport, Condition, ecc.).
     * Se un tipo usa "subject" invece di "patient", aggiungere un branch apposito.
     */
    public Resource fetchLastResourceByPatient(String resourceType, String patientId) {
        IFhirResourceDao<?> dao = daoRegistry.getResourceDao(resourceType);

        SearchParameterMap params = new SearchParameterMap();
        params.add("patient", new ReferenceParam("Patient/" + patientId));
        params.setSort(new SortSpec("_lastUpdated", SortOrderEnum.DESC));
        params.setCount(1);

        IBundleProvider results = dao.search(params);

        if (results.size() != null && results.size() > 0) {
            return (Resource) results.getResources(0, 1).get(0);
        }

        return null;
    }

    // =========================================================================
    // Fetch tutte le Observation per codici + patient
    // =========================================================================

    public List<Resource> fetchObservationsByCodesAndPatient(String patientId, List<Coding> codes) {
        IFhirResourceDao<Observation> dao = daoRegistry.getResourceDao(Observation.class);

        SearchParameterMap params = new SearchParameterMap();

        // Filtro sul subject (Patient)
        params.add("subject", new ReferenceParam("Patient/" + patientId));

        // OR sui codici LOINC estratti dal ValueSet
        TokenOrListParam codeOrList = new TokenOrListParam();
        for (Coding coding : codes) {
            codeOrList.addOr(new TokenParam(coding.getSystem(), coding.getCode()));
        }
        params.add("code", codeOrList);

        params.setCount(1000);

        IBundleProvider results = dao.search(params);

        List<Resource> observations = new ArrayList<>();
        int fromIndex = 0;
        int pageSize  = 100;

        // Paginazione interna per raccogliere tutti i risultati
        while (true) {
            List<IBaseResource> page = results.getResources(fromIndex, fromIndex + pageSize);
            if (page == null || page.isEmpty()) break;
            page.forEach(r -> observations.add((Resource) r));
            fromIndex += pageSize;
        }

        return observations;
    }

    // =========================================================================
    // Estrazione ValueSet contenuti nella OperationDefinition
    // =========================================================================

    /**
     * Estrae i soli codici (String) da un ValueSet contained — usato per i resourceType.
     */
    private List<String> estraiValueFromValueSetContained(OperationDefinition opDef, String internalId) {
        String targetId = normalizeId(internalId);

        return opDef.getContained().stream()
                .filter(r -> r instanceof ValueSet)
                .map(r -> (ValueSet) r)
                .filter(vs -> targetId.equals(normalizeId(vs.getIdElement().getIdPart())))
                .flatMap(vs -> {
                    Stream<String> fromCompose = vs.getCompose().getInclude().stream()
                            .flatMap(inc -> inc.getConcept().stream())
                            .map(ValueSet.ConceptReferenceComponent::getCode);

                    Stream<String> fromExpansion = vs.getExpansion().getContains().stream()
                            .map(ValueSet.ValueSetExpansionContainsComponent::getCode);

                    return Stream.concat(fromCompose, fromExpansion);
                })
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Estrae i Coding completi (system + code) da un ValueSet contained — usato per le Observation.
     * Necessario per costruire la TokenOrListParam con system corretto (es. http://loinc.org).
     */
    private List<Coding> estraiCodingsFromValueSetContained(OperationDefinition opDef, String internalId) {
        String targetId = normalizeId(internalId);

        return opDef.getContained().stream()
                .filter(r -> r instanceof ValueSet)
                .map(r -> (ValueSet) r)
                .filter(vs -> targetId.equals(normalizeId(vs.getIdElement().getIdPart())))
                .flatMap(vs -> {
                    // Dal Compose: manteniamo il system per ogni include
                    Stream<Coding> fromCompose = vs.getCompose().getInclude().stream()
                            .flatMap(inc -> inc.getConcept().stream()
                                    .map(concept -> new Coding()
                                            .setSystem(inc.getSystem())
                                            .setCode(concept.getCode())
                                            .setDisplay(concept.getDisplay())));

                    // Dall'Expansion (se presente)
                    Stream<Coding> fromExpansion = vs.getExpansion().getContains().stream()
                            .map(c -> new Coding()
                                    .setSystem(c.getSystem())
                                    .setCode(c.getCode())
                                    .setDisplay(c.getDisplay()));

                    return Stream.concat(fromCompose, fromExpansion);
                })
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Ricerca OperationDefinition
    // =========================================================================

    private OperationDefinition cercaOperationDefinition(String nome, String publisher) {
        IFhirResourceDao<OperationDefinition> opDefDao =
                daoRegistry.getResourceDao(OperationDefinition.class);

        SearchParameterMap map = new SearchParameterMap();
        map.add(OperationDefinition.SP_NAME, new StringParam(nome).setExact(true));

        if (publisher != null && !publisher.isEmpty()) {
            map.add(OperationDefinition.SP_PUBLISHER, new StringParam(publisher));
        }

        // Ordiniamo per _lastUpdated DESC così prendiamo sempre la versione più recente
        map.setSort(new SortSpec("_lastUpdated", SortOrderEnum.DESC));

        IBundleProvider result = opDefDao.search(map);

        if (result.isEmpty()) {
            throw new ResourceNotFoundException(
                "Nessuna OperationDefinition trovata con nome: " + nome +
                (publisher != null ? " e publisher: " + publisher : ""));
        }

        OperationDefinition opDef = (OperationDefinition) result.getResources(0, 1).get(0);
        log.info("OperationDefinition trovata: id={}, publisher={}, contained={}",
                opDef.getIdElement().getIdPart(),
                opDef.getPublisher(),
                opDef.getContained().size());

        return opDef;
    }

    // =========================================================================
    // Utility
    // =========================================================================

    /** Rimuove il '#' iniziale dall'ID per confronto uniforme */
    private String normalizeId(String id) {
        if (id == null) return null;
        return id.startsWith("#") ? id.substring(1) : id;
    }

    // =========================================================================
    // Costruzione Bundle searchset
    // =========================================================================

    private Bundle buildBundle(List<Resource> resources) {
        Bundle bundle = new Bundle();
        bundle.setId(UUID.randomUUID().toString());
        bundle.setType(Bundle.BundleType.SEARCHSET);
        bundle.setTotal(resources.size());
        bundle.getMeta().setLastUpdated(new Date());

        for (Resource resource : resources) {
            bundle.addEntry()
                    .setResource(resource)
                    .getSearch().setMode(Bundle.SearchEntryMode.MATCH);
        }

        return bundle;
    }
}