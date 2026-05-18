package ca.uhn.fhir.jpa.starter.operator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
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

    /** ID del ValueSet contained che elenca i resourceType da recuperare. */
    private static final String RESOURCE_TYPES_VALUESET_ID = "vs-resource-types";

    /**
     * Suffisso convenzionale dei ValueSet con i codici di filtro per resourceType
     * (es. "Observation-code").
     */
    private static final String CODE_VALUESET_SUFFIX = "-code";

    /**
     * Mapping resourceType → search parameter usato per filtrare sui codici.
     */
    private static final Map<String, String> CODE_SEARCH_PARAM_BY_TYPE = Map.of(
            "Encounter", "type");

    @Autowired
    private DaoRegistry daoRegistry;

    @Override
    public Class<OperationDefinition> getResourceType() {
        return OperationDefinition.class;
    }

    public Bundle getBundle(String nomeOperationCustom,
            String publisher,
            String codiceFiscale,
            String patientIdentifierSystem) {

        // 1. Trova l'OperationDefinition per name + publisher
        OperationDefinition opDef = searchOperationDefinition(nomeOperationCustom, publisher);

        // 2. Estrai la lista di resourceType dal ValueSet contained "vs-resource-types"
        List<String> resourceTypes = estraiCodesFromValueSetContained(opDef, RESOURCE_TYPES_VALUESET_ID);
        log.info("ResourceType richiesti dall'OperationDefinition: {}", resourceTypes);

        if (resourceTypes.isEmpty()) {
            throw new ResourceNotFoundException(
                    "L'OperationDefinition non contiene un ValueSet '" + RESOURCE_TYPES_VALUESET_ID + "' valorizzato.");
        }

        // 3. Risolvi il Patient tramite identifier (CF dell'assistito)
        String patientId = findPatientIdByIdentifier(patientIdentifierSystem, codiceFiscale);
        log.info("Patient HAPI ID: {}", patientId);

        List<Resource> allResources = new ArrayList<>();

        // 4. Per ogni resourceType: cerca il ValueSet "<resourceType>-code"
        // ed esegui la search filtrata per quei codici sul Patient.
        for (String resourceType : resourceTypes) {
            String codeValueSetId = resourceType + CODE_VALUESET_SUFFIX;
            List<Coding> codes = estraiCodingsFromValueSetContained(opDef, codeValueSetId);

            if (codes.isEmpty()) {
                // Fallback: nessun ValueSet di codici → prendi l'ultima risorsa di
                // quel tipo per il paziente
                log.warn("Nessun ValueSet '{}' trovato nell'OperationDefinition. " +
                        "Fallback: recupero ultima risorsa tipo={} per patientId={}",
                        codeValueSetId, resourceType, patientId);
                Resource last = fetchLastResourceByPatient(resourceType, patientId);
                if (last != null) {
                    allResources.add(last);
                }
                continue;
            }

            List<Resource> resources = fetchResourcesByCodesAndPatient(resourceType, patientId, codes);
            log.info("Recuperate {} risorse di tipo {} per patientId={} (codici filtrati={})",
                    resources.size(), resourceType, patientId, codes.size());
            allResources.addAll(resources);
        }

        // 5. Costruisci Bundle searchset
        return buildBundle(allResources);
    }

    // =========================================================================
    // Risoluzione Patient: identifier (system|value) → HAPI logical ID
    // =========================================================================

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
    // Fetch ultima risorsa (fallback) per patient
    // =========================================================================

    /**
     * Recupera l'ultima risorsa del tipo specificato associata al Patient,
     * ordinando per _lastUpdated DESC. Usato come fallback quando non esiste
     * un ValueSet "<resourceType>-code" nell'OperationDefinition.
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
    // Fetch risorse per codici + patient (generico per qualsiasi resourceType)
    // =========================================================================

    /**
     * Esegue la search del resourceType indicato filtrando per Patient e per
     * la OR-list di codici. Il search parameter usato per i codici è "code"
     * di default; le eccezioni sono dichiarate in CODE_SEARCH_PARAM_BY_TYPE.
     */
    public List<Resource> fetchResourcesByCodesAndPatient(String resourceType,
            String patientId,
            List<Coding> codes) {
        IFhirResourceDao<?> dao = daoRegistry.getResourceDao(resourceType);
        String codeSearchParam = CODE_SEARCH_PARAM_BY_TYPE.getOrDefault(resourceType, "code");

        SearchParameterMap params = new SearchParameterMap();
        params.add("patient", new ReferenceParam("Patient/" + patientId));

        TokenOrListParam codeOrList = new TokenOrListParam();
        for (Coding coding : codes) {
            codeOrList.addOr(new TokenParam(coding.getSystem(), coding.getCode()));
        }
        params.add(codeSearchParam, codeOrList);
        params.setCount(1000);

        IBundleProvider results = dao.search(params);

        List<Resource> out = new ArrayList<>();
        int fromIndex = 0;
        int pageSize = 100;

        while (true) {
            List<IBaseResource> page = results.getResources(fromIndex, fromIndex + pageSize);
            if (page == null || page.isEmpty())
                break;
            page.forEach(r -> out.add((Resource) r));
            fromIndex += pageSize;
        }

        return out;
    }

    // =========================================================================
    // Estrazione ValueSet contenuti nella OperationDefinition
    // =========================================================================

    /**
     * Estrae i codici (String) da un ValueSet contained. Usato per
     * "vs-resource-types".
     */
    private List<String> estraiCodesFromValueSetContained(OperationDefinition opDef, String internalId) {
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
     * Estrae Coding completi (system + code + display) da un ValueSet contained.
     * Usato per i ValueSet "<resourceType>-code" per costruire la TokenOrListParam
     * con il system corretto (es. http://loinc.org).
     */
    private List<Coding> estraiCodingsFromValueSetContained(OperationDefinition opDef, String internalId) {
        String targetId = normalizeId(internalId);

        return opDef.getContained().stream()
                .filter(r -> r instanceof ValueSet)
                .map(r -> (ValueSet) r)
                .filter(vs -> targetId.equals(normalizeId(vs.getIdElement().getIdPart())))
                .flatMap(vs -> {
                    Stream<Coding> fromCompose = vs.getCompose().getInclude().stream()
                            .flatMap(inc -> inc.getConcept().stream()
                                    .map(concept -> new Coding()
                                            .setSystem(inc.getSystem())
                                            .setCode(concept.getCode())
                                            .setDisplay(concept.getDisplay())));

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

    private OperationDefinition searchOperationDefinition(String name, String publisher) {
        IFhirResourceDao<OperationDefinition> opDefDao = daoRegistry.getResourceDao(OperationDefinition.class);

        SearchParameterMap map = new SearchParameterMap();
        map.add(OperationDefinition.SP_CODE, new TokenParam(name));

        if (publisher != null && !publisher.isEmpty()) {
            map.add(OperationDefinition.SP_PUBLISHER, new StringParam(publisher));
        }

        // Prendiamo sempre la versione più recente
        map.setSort(new SortSpec("_lastUpdated", SortOrderEnum.DESC));

        IBundleProvider result = opDefDao.search(map);

        if (result.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Nessuna OperationDefinition trovata con nome: " + name +
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

    /** Rimuove il '#' iniziale dall'ID per confronto uniforme. */
    private String normalizeId(String id) {
        if (id == null)
            return null;
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