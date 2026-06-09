package ca.uhn.fhir.jpa.starter.operator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.OperationDefinition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.context.RuntimeSearchParam;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.api.SortOrderEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.StringParam;
import ca.uhn.fhir.rest.param.TokenOrListParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.IResourceProvider;
import ca.uhn.fhir.rest.server.exceptions.ResourceNotFoundException;
import ca.uhn.fhir.rest.server.util.ISearchParamRegistry;
import ca.uhn.fhir.rest.server.util.ResourceSearchParams;

@Component
public class EngineComponent implements IResourceProvider {
	
	@Autowired
	private ISearchParamRegistry searchParamRegistry;

	private static final List<String> CODE_PARAM_CANDIDATES = List.of(
	    "code", "vaccine-code", "type", "category", "modality", "morphology",
	    "reason-code", "medication", "finding-code"
	);


    private static final Logger log = LoggerFactory.getLogger(EngineComponent.class);

    /** ID del ValueSet contained che elenca i resourceType da recuperare. */
    private static final String RESOURCE_TYPES_VALUESET_ID = "vs-resource-types";

    /**
     * Suffisso convenzionale dei ValueSet con i codici di filtro per resourceType
     * (es. "Observation-code").
     */
    private static final String CODE_VALUESET_SUFFIX = "-code";

    @Autowired
    private DaoRegistry daoRegistry;

    @Override
    public Class<OperationDefinition> getResourceType() {
        return OperationDefinition.class;
    }

    public Bundle getBundle(String codeOperation,
            String publisher,
            String patientIdentifierValue,
            String patientIdentifierSystem, RequestDetails theRequestDetails) {

        // 1. Trova l'OperationDefinition per name + publisher
        OperationDefinition opDef = searchOperationDefinition(codeOperation, publisher);

        // 2. Estrai la lista di resourceType dal ValueSet contained "vs-resource-types"
        List<String> resourceTypes = estraiCodesFromValueSetContained(opDef, RESOURCE_TYPES_VALUESET_ID);
        log.info("ResourceType richiesti dall'OperationDefinition: {}", resourceTypes);

        if (resourceTypes.isEmpty()) {
            throw new ResourceNotFoundException(
                    "L'OperationDefinition non contiene un ValueSet '" + RESOURCE_TYPES_VALUESET_ID + "' valorizzato.");
        }

        // 3. Risolvi il Patient tramite identifier (CF dell'assistito)
        String patientId = findPatientIdByIdentifier(patientIdentifierSystem, patientIdentifierValue, theRequestDetails);
        log.info("Patient HAPI ID: {}", patientId);

        // Use LinkedHashMap for automatic deduplication
        Map<String, Resource> collected = new LinkedHashMap<>();

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
                Resource last = fetchLastResourceByPatient(resourceType, patientId, theRequestDetails, collected);
                if (last != null) {
                    log.info("Found last resource for type {}", resourceType);
                }
                continue;
            }

            List<Resource> resources = fetchResourcesByCodesAndPatient(resourceType, patientId, codes, theRequestDetails, collected);
            log.info("Recuperate {} risorse di tipo {} per patientId={} (codici filtrati={})",
                    resources.size(), resourceType, patientId, codes.size());
        }

        // 5. Costruisci Bundle searchset
        return buildBundle(new ArrayList<>(collected.values()));
    }

    // =========================================================================
    // Risoluzione Patient: identifier (system|value) → HAPI logical ID
    // =========================================================================

    private String findPatientIdByIdentifier(String system, String value, RequestDetails theRequestDetails)  {
        IFhirResourceDao<Patient> patientDao = daoRegistry.getResourceDao(Patient.class);

        SearchParameterMap params = new SearchParameterMap();
        params.add(Patient.SP_IDENTIFIER, new TokenParam(system, value));
        params.setCount(1);

        IBundleProvider result = patientDao.search(params,theRequestDetails);

        if (result.isEmpty()) {
            throw new ResourceNotFoundException(
                    "Nessun Patient trovato con identifier "  + value);
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
    public Resource fetchLastResourceByPatient(String resourceType, String patientId, RequestDetails theRequestDetails, Map<String, Resource> collected) {
        IFhirResourceDao<?> dao = daoRegistry.getResourceDao(resourceType);

        SearchParameterMap params = new SearchParameterMap();
        params.add("patient", new ReferenceParam("Patient/" + patientId));
        params.setSort(new SortSpec("_lastUpdated", SortOrderEnum.DESC));
        params.setCount(1);

        IBundleProvider results = dao.search(params,theRequestDetails);

        if (results.size() != null && results.size() > 0) {
            Resource resource = (Resource) results.getResources(0, 1).get(0);
            collectResourceGraph(resource, patientId, theRequestDetails, collected);
            return resource;
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
//    public List<Resource> fetchResourcesByCodesAndPatient(String resourceType,
//            String patientId,
//            List<Coding> codes, RequestDetails theRequestDetails) {
//        IFhirResourceDao<?> dao = daoRegistry.getResourceDao(resourceType);
//        String codeSearchParam = CODE_SEARCH_PARAM_BY_TYPE.getOrDefault(resourceType, "code");
//
//        SearchParameterMap params = new SearchParameterMap();
//        params.add("patient", new ReferenceParam("Patient/" + patientId));
//
//        TokenOrListParam codeOrList = new TokenOrListParam();
//        for (Coding coding : codes) {
//            codeOrList.addOr(new TokenParam(coding.getSystem(), coding.getCode()));
//        }
//        params.add(codeSearchParam, codeOrList);
//        params.setCount(1000);
//
//        IBundleProvider results = dao.search(params,theRequestDetails);
//
//        List<Resource> out = new ArrayList<>();
//        int fromIndex = 0;
//        int pageSize = 100;
//
//        while (true) {
//            List<IBaseResource> page = results.getResources(fromIndex, fromIndex + pageSize);
//            if (page == null || page.isEmpty())
//                break;
//            page.forEach(r -> out.add((Resource) r));
//            fromIndex += pageSize;
//        }
//
//        return out;
//    }
    
    public List<Resource> fetchResourcesByCodesAndPatient(String resourceType,
            String patientId,
            List<Coding> codes,
            RequestDetails theRequestDetails,
            Map<String, Resource> collected) {

        IFhirResourceDao<?> dao = daoRegistry.getResourceDao(resourceType);

        SearchParameterMap params = new SearchParameterMap();
        params.add("patient", new ReferenceParam("Patient/" + patientId));

        // Risoluzione dinamica del search param per i codici usando la nuova strategia a due livelli
        String codeSearchParam = resolveCanonicalCodeSearchParam(resourceType);
        if (codeSearchParam == null) {
            codeSearchParam = resolveResourceSpecificCodeSearchParam(resourceType);
        }

        if (codeSearchParam == null) {
            log.warn("No code search parameter found for {}. Searching by patient only.", resourceType);
            // Continue with patient-only search
        } else if (codes != null && !codes.isEmpty()) {
            TokenOrListParam codeOrList = new TokenOrListParam();
            for (Coding coding : codes) {
                codeOrList.addOr(new TokenParam(coding.getSystem(), coding.getCode()));
            }
            params.add(codeSearchParam, codeOrList);
            log.info("Ricerca resourceType='{}' con search param='{}', {} codici",
                    resourceType, codeSearchParam, codes.size());
        }

        params.setCount(1000);
        IBundleProvider results = dao.search(params, theRequestDetails);

        List<Resource> out = new ArrayList<>();
        int fromIndex = 0;
        int pageSize = 100;
        int maxResults = 1000;

        // Collect all resources with enrichment
        while (true) {
            List<IBaseResource> page = results.getResources(fromIndex, fromIndex + pageSize);
            if (page == null || page.isEmpty()) break;
            
            for (IBaseResource res : page) {
                if (res instanceof Resource) {
                    Resource resource = (Resource) res;
                    collectResourceGraph(resource, patientId, theRequestDetails, collected);
                    out.add(resource);
                }
            }
            
            fromIndex += pageSize;
            
            if (out.size() >= maxResults) {
                log.info("Reached maximum of {} resources for type {}", maxResults, resourceType);
                break;
            }
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
    
    /**
     * Resolves a canonical code search parameter if configured.
     * This method checks for a standardized search parameter name that can be used
     * across multiple resource types.
     *
     * @param resourceType The FHIR resource type
     * @return The canonical search parameter name, or null if not configured
     */
    private String resolveCanonicalCodeSearchParam(String resourceType) {
        // Check if a canonical parameter like "clinical-code" exists
        // This would be a custom SearchParameter configured on the server
        
        try {
            RuntimeSearchParam searchParam = searchParamRegistry
                .getActiveSearchParam(resourceType, "clinical-code");
            
            if (searchParam != null) {
                log.debug("Using canonical search parameter 'clinical-code' for {}", resourceType);
                return "clinical-code";
            }
        } catch (Exception e) {
            log.debug("Canonical search parameter 'clinical-code' not available for {}", resourceType);
        }
        
        return null;
    }
    
    /**
     * Resolves a resource-specific code search parameter using a priority-based strategy.
     * This method attempts to find the most appropriate search parameter for code-based
     * searches on a given resource type.
     *
     * Priority order:
     * 1. Explicit configuration map (if implemented)
     * 2. Semantically meaningful parameter names
     * 3. Active TOKEN parameters from registry
     *
     * @param resourceType The FHIR resource type
     * @return The search parameter name, or null if none found
     */
    private String resolveResourceSpecificCodeSearchParam(String resourceType) {
        // Priority 1: Check explicit configuration map
        // This could be extended to read from application.yaml in the future
        Map<String, String> explicitMappings = new HashMap<>();
        explicitMappings.put("Observation", "code");
        explicitMappings.put("Condition", "code");
        explicitMappings.put("Procedure", "code");
        explicitMappings.put("DiagnosticReport", "code");
        explicitMappings.put("Immunization", "vaccine-code");
        explicitMappings.put("MedicationRequest", "code");
        explicitMappings.put("MedicationAdministration", "code");
        explicitMappings.put("AllergyIntolerance", "code");
        explicitMappings.put("ServiceRequest", "code");
        explicitMappings.put("CarePlan", "category");
        
        if (explicitMappings.containsKey(resourceType)) {
            String paramName = explicitMappings.get(resourceType);
            // Verify the parameter actually exists
            try {
                RuntimeSearchParam searchParam = searchParamRegistry
                    .getActiveSearchParam(resourceType, paramName);
                if (searchParam != null) {
                    log.debug("Using explicit mapping '{}' for {}", paramName, resourceType);
                    return paramName;
                }
            } catch (Exception e) {
                log.debug("Explicit mapping '{}' not available for {}", paramName, resourceType);
            }
        }
        
        // Priority 2: Try semantically meaningful parameter names in order
        List<String> candidateNames = Arrays.asList(
            "code",
            "vaccine-code",
            "medication",
            "type",
            "reason-code",
            "finding-code",
            "morphology",
            "category",
            "modality"
        );
        
        for (String candidateName : candidateNames) {
            try {
                RuntimeSearchParam searchParam = searchParamRegistry
                    .getActiveSearchParam(resourceType, candidateName);
                
                if (searchParam != null && "token".equals(searchParam.getParamType().name().toLowerCase())) {
                    log.debug("Using candidate parameter '{}' for {}", candidateName, resourceType);
                    return candidateName;
                }
            } catch (Exception e) {
                // Parameter doesn't exist, continue to next candidate
            }
        }
        
        // Priority 3: Search active parameters for TOKEN type with semantic meaning
        try {
            ResourceSearchParams activeParams =
                searchParamRegistry.getActiveSearchParams(resourceType);
            
            // Iterate through all parameter names using values() method
            for (RuntimeSearchParam param : activeParams.values()) {
                String paramName = param.getName();
                
                // Skip special parameters
                if (paramName.startsWith("_")) {
                    continue;
                }
                
                // Only consider TOKEN parameters
                if (!"token".equals(param.getParamType().name().toLowerCase())) {
                    continue;
                }
                
                // Prefer parameters with "code" in the name
                if (paramName.contains("code") || paramName.contains("type")) {
                    log.debug("Using discovered TOKEN parameter '{}' for {}", paramName, resourceType);
                    return paramName;
                }
            }
        } catch (Exception e) {
            log.warn("Error discovering search parameters for {}: {}", resourceType, e.getMessage());
        }
        
        log.warn("No suitable code search parameter found for resource type: {}", resourceType);
        return null;
    }
    
    // =========================================================================
    // Resource Key Generation and Reference Resolution Utilities
    // =========================================================================
    
    /**
     * Generates a unique key for a resource based on its type and ID.
     * Used for deduplication in the resource collection map.
     * 
     * @param resource The FHIR resource
     * @return A unique key in format "ResourceType/id"
     */
    private String resourceKey(Resource resource) {
        return resource.getResourceType().name() + "/" + resource.getIdElement().getIdPart();
    }
    
    /**
     * Reads a FHIR resource from a Reference.
     * Handles null references and missing resources gracefully.
     * 
     * @param reference The FHIR Reference to resolve
     * @param requestDetails The request context
     * @return The resolved resource, or null if not found or reference is invalid
     */
    private Resource readReference(Reference reference, RequestDetails requestDetails) {
        if (reference == null || !reference.hasReference()) {
            return null;
        }
        
        try {
            String refString = reference.getReference();
            // Handle format: ResourceType/id
            if (refString.contains("/")) {
                String[] parts = refString.split("/", 2);
                if (parts.length == 2) {
                    String resourceType = parts[0];
                    String id = parts[1];
                    
                    IFhirResourceDao<? extends IBaseResource> dao = daoRegistry.getResourceDao(resourceType);
                    IIdType idType = new IdType(resourceType, id);
                    return (Resource) dao.read(idType, requestDetails);
                }
            }
        } catch (ResourceNotFoundException e) {
            log.debug("Referenced resource not found: {}", reference.getReference());
        } catch (Exception e) {
            log.warn("Error reading reference {}: {}", reference.getReference(), e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extracts the Encounter reference from a clinical resource.
     * Supports common resource types that reference encounters.
     * 
     * @param resource The clinical resource
     * @return The Encounter reference, or null if not present
     */
    private Reference extractEncounterReference(Resource resource) {
        if (resource == null) {
            return null;
        }
        
        // Handle specific resource types explicitly
        switch (resource.getResourceType()) {
            case Observation:
                Observation obs = (Observation) resource;
                return obs.hasEncounter() ? obs.getEncounter() : null;
                
            case Condition:
                Condition cond = (Condition) resource;
                return cond.hasEncounter() ? cond.getEncounter() : null;
                
            case Procedure:
                Procedure proc = (Procedure) resource;
                return proc.hasEncounter() ? proc.getEncounter() : null;
                
            case DiagnosticReport:
                DiagnosticReport report = (DiagnosticReport) resource;
                return report.hasEncounter() ? report.getEncounter() : null;
                
            case Immunization:
                Immunization imm = (Immunization) resource;
                return imm.hasEncounter() ? imm.getEncounter() : null;
                
            case MedicationRequest:
                MedicationRequest medReq = (MedicationRequest) resource;
                return medReq.hasEncounter() ? medReq.getEncounter() : null;
                
            case MedicationAdministration:
                MedicationAdministration medAdmin = (MedicationAdministration) resource;
                return medAdmin.hasContext() ? medAdmin.getContext() : null;
                
            case AllergyIntolerance:
                AllergyIntolerance allergy = (AllergyIntolerance) resource;
                return allergy.hasEncounter() ? allergy.getEncounter() : null;
                
            default:
                log.debug("Encounter extraction not implemented for resource type: {}",
                         resource.getResourceType());
                return null;
        }
    }
    
    /**
     * Fetches the Encounter resource associated with a clinical resource.
     * 
     * @param resource The clinical resource that may reference an Encounter
     * @param requestDetails The request context
     * @return The Encounter resource, or null if not found or not referenced
     */
    private Encounter fetchAssociatedEncounter(Resource resource, RequestDetails requestDetails) {
        Reference encounterRef = extractEncounterReference(resource);
        if (encounterRef == null) {
            return null;
        }
        
        Resource encounterResource = readReference(encounterRef, requestDetails);
        if (encounterResource instanceof Encounter) {
            return (Encounter) encounterResource;
        }
        
        return null;
    }
    
    /**
     * Fetches all contextual resources related to an Encounter.
     * This includes Location, Organization, Practitioner, and PractitionerRole resources.
     * 
     * @param encounter The Encounter resource
     * @param requestDetails The request context
     * @return List of contextual resources (never null, may be empty)
     */
    private List<Resource> fetchEncounterContext(Encounter encounter, RequestDetails requestDetails) {
        List<Resource> contextResources = new ArrayList<>();
        
        if (encounter == null) {
            return contextResources;
        }
        
        // Add the Encounter itself
        contextResources.add(encounter);
        
        // Fetch Location resources from encounter.location
        if (encounter.hasLocation()) {
            for (Encounter.EncounterLocationComponent locationComp : encounter.getLocation()) {
                if (locationComp.hasLocation()) {
                    Resource location = readReference(locationComp.getLocation(), requestDetails);
                    if (location != null) {
                        contextResources.add(location);
                    }
                }
            }
        }
        
        // Fetch Organization from encounter.serviceProvider
        if (encounter.hasServiceProvider()) {
            Resource organization = readReference(encounter.getServiceProvider(), requestDetails);
            if (organization != null) {
                contextResources.add(organization);
                
                // If it's an Organization, we might want to follow additional references
                // but for now we just add it
            }
        }
        
        // Fetch Practitioner and PractitionerRole from encounter.participant
        if (encounter.hasParticipant()) {
            for (Encounter.EncounterParticipantComponent participant : encounter.getParticipant()) {
                if (participant.hasIndividual()) {
                    Resource individual = readReference(participant.getIndividual(), requestDetails);
                    if (individual != null) {
                        contextResources.add(individual);
                        
                        // If it's a PractitionerRole, follow nested references
                        if (individual instanceof PractitionerRole) {
                            PractitionerRole role = (PractitionerRole) individual;
                            
                            // Fetch the Practitioner referenced by the role
                            if (role.hasPractitioner()) {
                                Resource practitioner = readReference(role.getPractitioner(), requestDetails);
                                if (practitioner != null) {
                                    contextResources.add(practitioner);
                                }
                            }
                            
                            // Fetch the Organization referenced by the role
                            if (role.hasOrganization()) {
                                Resource org = readReference(role.getOrganization(), requestDetails);
                                if (org != null) {
                                    contextResources.add(org);
                                }
                            }
                            
                            // Fetch Location resources referenced by the role
                            if (role.hasLocation()) {
                                for (Reference locRef : role.getLocation()) {
                                    Resource loc = readReference(locRef, requestDetails);
                                    if (loc != null) {
                                        contextResources.add(loc);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return contextResources;
    }
    
    /**
     * Fetches document context (Composition and DocumentReference) for a main resource
     * using _revinclude mechanism.
     * 
     * This method attempts to retrieve:
     * - Composition resources via _revinclude=Composition:entry
     * - DocumentReference resources via _revinclude:iterate=DocumentReference:related
     * 
     * @param mainResource The main clinical resource
     * @param requestDetails The request context
     * @return List of document resources (Composition, DocumentReference), excluding the main resource itself
     */
    private List<Resource> fetchDocumentContextByRevInclude(
            Resource mainResource,
            RequestDetails requestDetails) {
        
        List<Resource> documentResources = new ArrayList<>();
        
        if (mainResource == null || !mainResource.hasId()) {
            return documentResources;
        }
        
        try {
            // Get the DAO for the main resource type
            String resourceType = mainResource.getResourceType().name();
            IFhirResourceDao<? extends IBaseResource> dao = daoRegistry.getResourceDao(resourceType);
            
            // Build search parameters with _id and _revinclude
            SearchParameterMap params = new SearchParameterMap();
            
            // Search for the specific resource by ID
            String resourceId = mainResource.getIdElement().getIdPart();
            params.add("_id", new TokenParam(resourceId));
            
            // Add _revinclude for Composition:entry
            params.addRevInclude(new Include("Composition:entry"));
            
            // Add _revinclude:iterate for DocumentReference:related
            Include docRefInclude = new Include("DocumentReference:related");
            docRefInclude.setRecurse(true);
            params.addRevInclude(docRefInclude);
            
            // Set reasonable limit
            params.setCount(200);
            
            // Execute search
            IBundleProvider bundleProvider = dao.search(params, requestDetails);
            
            // Collect all resources from all pages
            List<IBaseResource> allResources = new ArrayList<>();
            Integer totalSize = bundleProvider.size();
            
            if (totalSize != null && totalSize > 0) {
                // Fetch all pages
                int pageSize = 100;
                for (int offset = 0; offset < totalSize; offset += pageSize) {
                    List<IBaseResource> page = bundleProvider.getResources(offset, offset + pageSize);
                    allResources.addAll(page);
                }
            } else {
                // If size is null, fetch what we can
                allResources.addAll(bundleProvider.getResources(0, 200));
            }
            
            // Filter out the main resource and collect only Composition and DocumentReference
            String mainResourceKey = resourceKey(mainResource);
            for (IBaseResource res : allResources) {
                if (res instanceof Resource) {
                    Resource resource = (Resource) res;
                    String key = resourceKey(resource);
                    
                    // Skip the main resource itself
                    if (key.equals(mainResourceKey)) {
                        continue;
                    }
                    
                    // Only include Composition and DocumentReference
                    if (resource.getResourceType() == ResourceType.Composition ||
                        resource.getResourceType() == ResourceType.DocumentReference) {
                        documentResources.add(resource);
                    }
                }
            }
            
            log.debug("Fetched {} document resources via _revinclude for {}/{}",
                     documentResources.size(), resourceType, resourceId);
            
        } catch (Exception e) {
            log.warn("Error fetching document context via _revinclude for {}: {}",
                    mainResource.getIdElement().getValue(), e.getMessage());
        }
        
        return documentResources;
    }
    
    /**
     * Fallback method to fetch Composition resources when _revinclude doesn't return results.
     * Searches for Compositions by subject (patient) and optionally encounter.
     * 
     * @param mainResource The main clinical resource
     * @param patientId The patient ID
     * @param encounterId The encounter ID (may be null)
     * @param requestDetails The request context
     * @return List of Composition resources
     */
    private List<Resource> fetchRelatedCompositionsFallback(
            Resource mainResource,
            String patientId,
            String encounterId,
            RequestDetails requestDetails) {
        
        List<Resource> compositions = new ArrayList<>();
        
        try {
            IFhirResourceDao<Composition> compositionDao = 
                daoRegistry.getResourceDao(Composition.class);
            
            SearchParameterMap params = new SearchParameterMap();
            
            // Try with encounter first if available
            if (encounterId != null && !encounterId.isEmpty()) {
                params.add("subject", new ReferenceParam("Patient", patientId));
                params.add("encounter", new ReferenceParam("Encounter", encounterId));
                params.setCount(100);
                
                IBundleProvider bundleProvider = compositionDao.search(params, requestDetails);
                List<IBaseResource> results = bundleProvider.getResources(0, 100);
                
                for (IBaseResource res : results) {
                    if (res instanceof Composition) {
                        compositions.add((Composition) res);
                    }
                }
                
                log.debug("Fetched {} Compositions via fallback (patient+encounter)", compositions.size());
            }
            
            // If no results with encounter, or no encounter available, try patient only
            if (compositions.isEmpty()) {
                params = new SearchParameterMap();
                params.add("subject", new ReferenceParam("Patient", patientId));
                params.setCount(100);
                
                IBundleProvider bundleProvider = compositionDao.search(params, requestDetails);
                List<IBaseResource> results = bundleProvider.getResources(0, 100);
                
                for (IBaseResource res : results) {
                    if (res instanceof Composition) {
                        compositions.add((Composition) res);
                    }
                }
                
                log.debug("Fetched {} Compositions via fallback (patient only)", compositions.size());
            }
            
        } catch (Exception e) {
            log.warn("Error in Composition fallback for patient {}: {}", patientId, e.getMessage());
        }
        
        return compositions;
    }
    
    /**
     * Fallback method to fetch DocumentReference resources when _revinclude doesn't return results.
     * Searches for DocumentReferences by subject (patient) and optionally encounter/context.
     * 
     * @param mainResource The main clinical resource
     * @param patientId The patient ID
     * @param encounterId The encounter ID (may be null)
     * @param requestDetails The request context
     * @return List of DocumentReference resources
     */
    private List<Resource> fetchRelatedDocumentReferencesFallback(
            Resource mainResource,
            String patientId,
            String encounterId,
            RequestDetails requestDetails) {
        
        List<Resource> documentReferences = new ArrayList<>();
        
        try {
            IFhirResourceDao<DocumentReference> docRefDao = 
                daoRegistry.getResourceDao(DocumentReference.class);
            
            SearchParameterMap params = new SearchParameterMap();
            
            // Try with encounter/context first if available
            if (encounterId != null && !encounterId.isEmpty()) {
                params.add("subject", new ReferenceParam("Patient", patientId));
                
                // Try 'encounter' parameter first (R4 standard)
                params.add("encounter", new ReferenceParam("Encounter", encounterId));
                params.setCount(100);
                
                try {
                    IBundleProvider bundleProvider = docRefDao.search(params, requestDetails);
                    List<IBaseResource> results = bundleProvider.getResources(0, 100);
                    
                    for (IBaseResource res : results) {
                        if (res instanceof DocumentReference) {
                            documentReferences.add((DocumentReference) res);
                        }
                    }
                    
                    log.debug("Fetched {} DocumentReferences via fallback (patient+encounter)", 
                             documentReferences.size());
                } catch (Exception e) {
                    // If 'encounter' parameter doesn't exist, try 'context' parameter
                    log.debug("'encounter' parameter not available for DocumentReference, trying 'context'");
                    
                    params = new SearchParameterMap();
                    params.add("subject", new ReferenceParam("Patient", patientId));
                    params.add("context", new ReferenceParam("Encounter", encounterId));
                    params.setCount(100);
                    
                    IBundleProvider bundleProvider = docRefDao.search(params, requestDetails);
                    List<IBaseResource> results = bundleProvider.getResources(0, 100);
                    
                    for (IBaseResource res : results) {
                        if (res instanceof DocumentReference) {
                            documentReferences.add((DocumentReference) res);
                        }
                    }
                    
                    log.debug("Fetched {} DocumentReferences via fallback (patient+context)", 
                             documentReferences.size());
                }
            }
            
            // If no results with encounter, or no encounter available, try patient only
            if (documentReferences.isEmpty()) {
                params = new SearchParameterMap();
                params.add("subject", new ReferenceParam("Patient", patientId));
                params.setCount(100);
                
                IBundleProvider bundleProvider = docRefDao.search(params, requestDetails);
                List<IBaseResource> results = bundleProvider.getResources(0, 100);
                
                for (IBaseResource res : results) {
                    if (res instanceof DocumentReference) {
                        documentReferences.add((DocumentReference) res);
                    }
                }
                
                log.debug("Fetched {} DocumentReferences via fallback (patient only)", 
                         documentReferences.size());
            }
            
        } catch (Exception e) {
            log.warn("Error in DocumentReference fallback for patient {}: {}", patientId, e.getMessage());
        }
        
        return documentReferences;
    }
    
    /**
     * Collects a complete resource graph for a main clinical resource.
     * This method enriches the main resource with:
     * - Document context (Composition, DocumentReference) via _revinclude and fallback
     * - Encounter context (Encounter, Location, Organization, Practitioner, PractitionerRole)
     *
     * All resources are deduplicated using the provided collection map.
     *
     * @param mainResource The main clinical resource to enrich
     * @param patientId The patient ID
     * @param requestDetails The request context
     * @param collected The deduplication map (resourceType/id -> Resource)
     */
    private void collectResourceGraph(
            Resource mainResource,
            String patientId,
            RequestDetails requestDetails,
            Map<String, Resource> collected) {
        
        if (mainResource == null) {
            return;
        }
        
        // Add the main resource first
        String mainKey = resourceKey(mainResource);
        collected.put(mainKey, mainResource);
        
        log.debug("Collecting resource graph for {}", mainKey);
        
        // Step 1: Fetch document context via _revinclude
        List<Resource> documentResources = fetchDocumentContextByRevInclude(mainResource, requestDetails);
        
        // Track what we found via _revinclude
        boolean hasComposition = false;
        boolean hasDocumentReference = false;
        
        for (Resource docRes : documentResources) {
            String key = resourceKey(docRes);
            collected.put(key, docRes);
            
            if (docRes.getResourceType() == ResourceType.Composition) {
                hasComposition = true;
            } else if (docRes.getResourceType() == ResourceType.DocumentReference) {
                hasDocumentReference = true;
            }
        }
        
        log.debug("Found {} document resources via _revinclude (Composition: {}, DocumentReference: {})",
                 documentResources.size(), hasComposition, hasDocumentReference);
        
        // Step 2: Extract encounter ID for fallback and context retrieval
        String encounterId = null;
        Encounter encounter = fetchAssociatedEncounter(mainResource, requestDetails);
        if (encounter != null) {
            encounterId = encounter.getIdElement().getIdPart();
        }
        
        // Step 3: Apply fallbacks if _revinclude didn't return sufficient results
        if (!hasComposition) {
            log.debug("No Composition found via _revinclude, trying fallback");
            List<Resource> compositionFallback = fetchRelatedCompositionsFallback(
                mainResource, patientId, encounterId, requestDetails);
            
            for (Resource comp : compositionFallback) {
                String key = resourceKey(comp);
                collected.put(key, comp);
            }
            
            log.debug("Fallback found {} Composition resources", compositionFallback.size());
        }
        
        if (!hasDocumentReference) {
            log.debug("No DocumentReference found via _revinclude, trying fallback");
            List<Resource> docRefFallback = fetchRelatedDocumentReferencesFallback(
                mainResource, patientId, encounterId, requestDetails);
            
            for (Resource docRef : docRefFallback) {
                String key = resourceKey(docRef);
                collected.put(key, docRef);
            }
            
            log.debug("Fallback found {} DocumentReference resources", docRefFallback.size());
        }
        
        // Step 4: Fetch encounter context (Encounter, Location, Organization, Practitioner, PractitionerRole)
        if (encounter != null) {
            log.debug("Fetching encounter context for Encounter/{}", encounterId);
            List<Resource> encounterContext = fetchEncounterContext(encounter, requestDetails);
            
            for (Resource contextRes : encounterContext) {
                String key = resourceKey(contextRes);
                collected.put(key, contextRes);
            }
            
            log.debug("Added {} encounter context resources", encounterContext.size());
        } else {
            log.debug("No encounter associated with {}, skipping encounter context", mainKey);
        }
        
        log.debug("Resource graph collection complete for {}. Total resources: {}",
                 mainKey, collected.size());
    }
}