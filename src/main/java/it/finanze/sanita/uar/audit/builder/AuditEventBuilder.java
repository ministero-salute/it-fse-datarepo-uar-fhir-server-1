/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.builder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.hl7.fhir.r4.model.DocumentReference;

import ca.uhn.fhir.rest.api.RequestTypeEnum;
import it.finanze.sanita.uar.audit.enums.AuditEntityTypeEnum;
import it.finanze.sanita.uar.audit.enums.AuditEventSubtypeEnum;
import it.finanze.sanita.uar.audit.enums.AuditEventTypeEnum;
import it.finanze.sanita.uar.audit.enums.AuditPurposeOfEventEnum;
import it.finanze.sanita.uar.audit.enums.ClinicalDocumentPermission;

/**
 * Builder per la creazione di AuditEvent FHIR secondo le specifiche FSE 2.0
 * Supporta 4 scenari: CREATE, READ, UPDATE, DELETE
 */
public class AuditEventBuilder {

    private static final Logger log = LoggerFactory.getLogger(AuditEventBuilder.class);
    private final AuditEvent auditEvent;

    /**
     * Costruttore privato - usa i factory methods
     */
    private AuditEventBuilder() {
        this.auditEvent = new AuditEvent();
        this.auditEvent.setRecorded(new Date());
    }

    // ============ FACTORY METHODS ============
    // qui andrebbe la logica di costruzione audit event che non è comune a tuti i
    // flussi e unica solo a tipologie di flussi specifici

    /**
     * Crea builder per consultazione dati (READ)
     * 
     * @return Builder configurato per READ
     */
    public static AuditEventBuilder forDataConsultation() {
        AuditEventBuilder builder = new AuditEventBuilder();
        builder.auditEvent.setAction(AuditEvent.AuditEventAction.R);
        builder.withType(AuditEventTypeEnum.DATA_CONSULTATION);
        return builder;
    }

    /**
     * Crea builder per creazione dati (CREATE)
     * 
     * @return Builder configurato per CREATE
     */
    public static AuditEventBuilder forDataCreation() {
        AuditEventBuilder builder = new AuditEventBuilder();
        builder.auditEvent.setAction(AuditEvent.AuditEventAction.C);
        builder.withType(AuditEventTypeEnum.DATA_CREATION);
        return builder;
    }

    /**
     * Crea builder per aggiornamento/oscuramento dati (UPDATE)
     * 
     * @return Builder configurato per UPDATE
     */
    public static AuditEventBuilder forDataUpdate() {
        AuditEventBuilder builder = new AuditEventBuilder();
        builder.auditEvent.setAction(AuditEvent.AuditEventAction.U);
        builder.withType(AuditEventTypeEnum.DATA_OBFUSCATION);
        return builder;
    }

    /**
     * Crea builder per cancellazione dati (DELETE)
     * 
     * @return Builder configurato per DELETE
     */
    public static AuditEventBuilder forDataDeletion() {
        AuditEventBuilder builder = new AuditEventBuilder();
        builder.auditEvent.setAction(AuditEvent.AuditEventAction.D);
        builder.withType(AuditEventTypeEnum.DATA_OBFUSCATION);
        return builder;
    }

    /**
     * Crea builder appropriato basandosi sul tipo di richiesta HTTP.
     * Factory method che determina automaticamente il tipo di operazione.
     * 
     * @param requestMethod Tipo di richiesta HTTP (GET, POST, PUT, DELETE)
     * @return Builder configurato per il tipo di operazione, o null se requestMethod è null
     */
    public static AuditEventBuilder forRequestMethod(RequestTypeEnum requestMethod) {
        if (requestMethod == null) {
            return null;
        }

        switch (requestMethod) {
            case POST:
                return forDataCreation();
            case PUT:
                return forDataUpdate();
            case DELETE:
                return forDataDeletion();
            case GET:
                return forDataConsultation();
            default:
                return null;
        }
    }

    // ============ CONFIGURATION METHODS ============

    /**
     * Imposta la data di registrazione dell'evento
     * 
     * @param date Data di registrazione
     * @return Builder per method chaining
     */
    public AuditEventBuilder withRecordedDate(Date date) {
        if (date != null) {
            this.auditEvent.setRecorded(date);
        }
        return this;
    }

    /**
     * Imposta il type dell'AuditEvent
     * 
     * @param type Tipo di evento
     * @return Builder per method chaining
     */
    public AuditEventBuilder withType(AuditEventTypeEnum type) {
        if (type != null) {
            Coding coding = new Coding()
                    .setCode(type.getCode())
                    .setSystem(type.getSystem())
                    .setDisplay(type.getDisplay());
            this.auditEvent.setType(coding);
        }
        return this;
    }

    /**
     * Imposta il subtype dell'AuditEvent
     * 
     * @param subtype Sottotipo di evento
     * @return Builder per method chaining
     */
    public AuditEventBuilder withSubtype(AuditEventSubtypeEnum subtype) {
        if (subtype != null) {
            Coding coding = new Coding()
                    .setCode(subtype.getCode())
                    .setSystem(subtype.getSystem())
                    .setDisplay(subtype.getDisplay());
            this.auditEvent.addSubtype(coding);
        }
        return this;
    }

    /**
     * Imposta il purposeOfEvent dell'AuditEvent
     * 
     * @param purpose Scopo dell'evento (BTG, OSC, DE_OSC)
     * @return Builder per method chaining
     */
    public AuditEventBuilder withPurposeOfEvent(AuditPurposeOfEventEnum purpose) {
        if (purpose != null) {
            CodeableConcept cc = new CodeableConcept();
            cc.addCoding()
                    .setCode(purpose.getCode())
                    .setSystem(purpose.getSystem())
                    .setDisplay(purpose.getDisplay());
            this.auditEvent.addPurposeOfEvent(cc);
        }
        return this;
    }

    /**
     * Shortcut per aggiungere accesso in emergenza (Break The Glass)
     * 
     * @return Builder per method chaining
     */
    public AuditEventBuilder withEmergencyAccess() {
        return withPurposeOfEvent(AuditPurposeOfEventEnum.EMERGENCY_ACCESS);
    }

    /**
     * Aggiunge la query in Base64 come entity
     * 
     * @param queryBase64 Query FHIR codificata in Base64
     * @return Builder per method chaining
     */
    public AuditEventBuilder withQueryBase64(String queryBase64) {
        if (queryBase64 != null && !queryBase64.isEmpty()) {
            try {
                // Decodifica da Base64 per ottenere i byte
                byte[] queryBytes = Base64.getDecoder().decode(queryBase64.getBytes(StandardCharsets.UTF_8));

                // Crea o recupera la prima entity
                AuditEvent.AuditEventEntityComponent entity;
                if (this.auditEvent.hasEntity()) {
                    entity = this.auditEvent.getEntityFirstRep();
                } else {
                    entity = new AuditEvent.AuditEventEntityComponent();
                    this.auditEvent.addEntity(entity);
                }

                // Imposta la query
                entity.setQuery(queryBytes);
            } catch (Exception e) {
                log.error("Error decoding Base64 query: {}", queryBase64, e);
            }
        }
        return this;
    }

    /**
     * Imposta il tipo di servizio consultato (entity.type)
     * 
     * @param entityType Tipo di servizio (SERV_ASS_* o SERV_PRO_*)
     * @return Builder per method chaining
     */
    public AuditEventBuilder withEntityType(AuditEntityTypeEnum entityType) {
        if (entityType != null) {
            // Crea o recupera la prima entity
            AuditEvent.AuditEventEntityComponent entity;
            if (this.auditEvent.hasEntity()) {
                entity = this.auditEvent.getEntityFirstRep();
            } else {
                entity = new AuditEvent.AuditEventEntityComponent();
                this.auditEvent.addEntity(entity);
            }

            Coding type = new Coding()
                    .setCode(entityType.getCode())
                    .setSystem(entityType.getSystem())
                    .setDisplay(entityType.getDisplay());
            entity.setType(type);
        }
        return this;
    }

    /**
     * Aggiunge un reference a una risorsa con versione (_history)
     * 
     * @param resourceType Tipo risorsa (es. "Patient", "Observation")
     * @param resourceId   ID della risorsa
     * @param version      Versione della risorsa
     * @return Builder per method chaining
     */
    public AuditEventBuilder withResourceReference(String resourceType, String resourceId, String version) {
        if (resourceType != null && resourceId != null) {
            AuditEvent.AuditEventEntityComponent entity = new AuditEvent.AuditEventEntityComponent();

            // Costruisce il reference con _history
            String reference = resourceType + "/" + resourceId;
            if (version != null && !version.isEmpty()) {
                reference += "/_history/" + version;
            }

            entity.setWhat(new Reference(reference));
            this.auditEvent.addEntity(entity);
        }
        return this;
    }

    /**
     * Aggiunge entity references dal request Bundle usando client IDs/fullUrls.
     * Questo è il metodo per pre-transaction audit generation.
     * 
     * @param requestBundle Bundle di richiesta con client IDs
     * @return Builder per method chaining
     */
    public AuditEventBuilder withResourcesFromRequestBundle(Bundle requestBundle) {
        if (requestBundle == null || !requestBundle.hasEntry()) {
            return this;
        }

        requestBundle.getEntry().stream()
                .filter(entry -> entry.getResource() != null)
                .filter(entry -> !isAdministrativeResource(entry.getResource().fhirType()))
                .forEach(entry -> {
                    String clientId = null;

                    // Prefer fullUrl if present
                    if (entry.hasFullUrl()) {
                        clientId = entry.getFullUrl();
                    } else if (entry.getResource().hasId()) {
                        // Use resource ID as fallback
                        clientId = entry.getResource().getId();
                    }

                    if (clientId != null) {

                        AuditEvent.AuditEventEntityComponent entity = new AuditEvent.AuditEventEntityComponent();
                        Reference what = new Reference();
                        // Client ID attached to the Reference
                        Identifier identifier = new Identifier();
                        identifier.setValue(clientId);
                        what.setIdentifier(identifier);
                        what.setReference(clientId);
                        entity.setWhat(what);
                        this.auditEvent.addEntity(entity);
                        log.debug("Added AuditEvent entity - clientId={}", clientId);
                    }
                });

        return this;
    }

    public AuditEventBuilder withResourcesFromResponseBundle(Bundle bundle) {

        if (bundle == null || !bundle.hasEntry()) {
            return this;
        }

        bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResponse)
                .filter(Objects::nonNull)
                .map(Bundle.BundleEntryResponseComponent::getLocation)
                .filter(Objects::nonNull)
                .forEach(location -> {
                    AuditEvent.AuditEventEntityComponent entity = new AuditEvent.AuditEventEntityComponent();
                    Identifier identifier = new Identifier();
                    identifier.setValue(location);
                    entity.setWhat((new Reference().setIdentifier(identifier)));
                    this.auditEvent.addEntity(entity);
                });

        return this;
    }

    /**
     * Costruisce un reference FHIR con versioning per l'AuditEvent.entity.what
     * Formato: ResourceType/id/_history/version
     * 
     * @param resource Risorsa FHIR
     * @param entry    Entry del Bundle contenente la risorsa
     * @return Reference con versioning o null se non può essere costruito
     */
    private String buildReferenceWithVersion(Resource resource, Bundle.BundleEntryComponent entry) {
        String resourceType = resource.fhirType();
        String resourceId = resource.getIdElement().getIdPart();

        // Fallback: estrai ID dalla request URL se non presente nella risorsa
        if (resourceId == null || resourceId.isEmpty()) {
            if (entry.hasRequest() && entry.getRequest().hasUrl()) {
                String url = entry.getRequest().getUrl();
                // Formato: "ResourceType/id" o "ResourceType/id?params"
                int slashIndex = url.indexOf('/');
                if (slashIndex > 0 && slashIndex < url.length() - 1) {
                    resourceId = url.substring(slashIndex + 1);
                    // Rimuovi parametri query
                    int queryIndex = resourceId.indexOf('?');
                    if (queryIndex > 0) {
                        resourceId = resourceId.substring(0, queryIndex);
                    }
                }
            }
        }

        if (resourceId == null || resourceId.isEmpty()) {
            log.warn("Could not extract ID for resource type: {}", resourceType);
            return null;
        }

        // Usa versioning placeholder per tutte le risorse
        // Il server FHIR assegnerà la versione corretta durante la transazione
        String version = "1";

        return resourceType + "/" + resourceId + "/_history/" + version;
    }

    /**
     * Aggiunge un agent per il paziente (titolare dei dati) con ruolo
     * personalizzato.
     * Usare questo metodo solo se si vuole sovrascrivere il ruolo standard.
     * 
     * @param patientRef Reference al Patient con versione
     * @param customRole Ruolo personalizzato (opzionale, se null usa "PAT")
     * @return Builder per method chaining
     */
    public AuditEventBuilder withPatientAgent(String patientRef) {
        if (patientRef != null && !patientRef.isEmpty()) {
            AuditEvent.AuditEventAgentComponent agent = new AuditEvent.AuditEventAgentComponent();
            agent.setWho(new Reference(patientRef));
            agent.setRequestor(false);
            this.auditEvent.addAgent(agent);
            log.debug("Added Patient agent: {} ", patientRef);
        }
        return this;
    }

    /**
     * Aggiunge un agent per il paziente (titolare dei dati)
     * 
     * @param patientRef Reference al Patient (es. "Patient/123")
     * @param role       Ruolo (opzionale)
     * @return Builder per method chaining
     */
    public AuditEventBuilder withPatientAgent(String patientRef, String role) {
        if (patientRef != null && !patientRef.isEmpty()) {
            AuditEvent.AuditEventAgentComponent agent = new AuditEvent.AuditEventAgentComponent();
            agent.setWho(new Reference(patientRef));
            agent.setRequestor(false); // Il paziente è il titolare, non il richiedente

            if (role != null && !role.isEmpty()) {
                CodeableConcept roleCC = new CodeableConcept();
                roleCC.addCoding().setCode(role);
                agent.addRole(roleCC);
            }

            this.auditEvent.addAgent(agent);
        }
        return this;
    }

    /**
     * Aggiunge un agent per il professionista (operatore sanitario)
     * 
     * @param practitionerRef Reference al Practitioner/PractitionerRole
     * @param role            Ruolo (opzionale)
     * @return Builder per method chaining
     */
    public AuditEventBuilder withPractitionerAgent(String practitionerRef, String role) {
        if (practitionerRef != null && !practitionerRef.isEmpty()) {
            AuditEvent.AuditEventAgentComponent agent = new AuditEvent.AuditEventAgentComponent();
            agent.setWho(new Reference(practitionerRef));
            agent.setRequestor(true); // Il professionista è il richiedente

            if (role != null && !role.isEmpty()) {
                CodeableConcept roleCC = new CodeableConcept();
                roleCC.addCoding().setCode(role);
                agent.addRole(roleCC);
            }

            this.auditEvent.addAgent(agent);
        }
        return this;
    }

    /**
     * Aggiunge SEMPRE il paziente come agent (proprietario dei dati)
     * e aggiunge il richiedente effettivo (paziente stesso, medico o delegato).
     *
     * @param patientRef    Reference al Patient (es. "Patient/123")
     * @param requesterRef  Reference al richiedente (Patient, Practitioner,
     *                      RelatedPerson)
     * @param requesterRole Ruolo del richiedente (ASS, APR, TUT, etc.)
     * @return Builder per method chaining
     */
    public AuditEventBuilder withAgentRolePatientAndRequestor(
            String patientRef,
            String requesterRef,
            String requesterRole) {

        if (requesterRole != null && !requesterRole.isBlank()) {
            AuditEvent.AuditEventAgentComponent roleAgent = new AuditEvent.AuditEventAgentComponent();
            roleAgent.addRole(buildRole(requesterRole));
            this.auditEvent.addAgent(roleAgent);
        }

        AuditEvent.AuditEventAgentComponent patientAgent = null;
        /* 1. Paziente = proprietario del dato (sempre presente) */
        if (patientRef != null && !patientRef.isBlank()) {
            patientAgent = new AuditEvent.AuditEventAgentComponent();
            Reference whoPatient = new Reference();
            whoPatient.setReference(patientRef);
            patientAgent.setWho(whoPatient);
            patientAgent.setRequestor(false);
            this.auditEvent.addAgent(patientAgent);
            log.debug("Added Patient as data owner: {}", patientRef);
        }

        /* 2. Richiedente effettivo */
        if (requesterRef != null && !requesterRef.isBlank()) {
            boolean sameSubject = patientRef != null &&
                    normalizeRef(patientRef).equals(normalizeRef(requesterRef));
            if (sameSubject) {
                /* Il paziente accede ai propri dati */
                if (patientAgent != null) {
                    patientAgent.setRequestor(true);
                    log.debug("Patient is both data owner and requestor: {}", patientRef);
                }

            } else {
                /* Richiedente diverso dal paziente (medico, delegato, sistema) */
                AuditEvent.AuditEventAgentComponent requesterAgent = new AuditEvent.AuditEventAgentComponent();
                Reference who = new Reference();
                who.setReference(requesterRef);
                requesterAgent.setWho(who);
                requesterAgent.setRequestor(true);
                this.auditEvent.addAgent(requesterAgent);
                log.debug("Added separate agent requester: {} with role: {}",
                        requesterRef,
                        requesterRole);
            }
        }

        return this;
    }


    private String normalizeRef(String ref) {
        if (ref == null) {
            return null;
        }
        int historyIdx = ref.indexOf("/_history/");
        return historyIdx > 0 ? ref.substring(0, historyIdx) : ref;
    }

    private CodeableConcept buildRole(String roleCode) {
        CodeableConcept cc = new CodeableConcept();
        Coding coding = cc.addCoding();
        coding.setCode(roleCode);
        // Imposta il system per i ruoli FSE 2.0
        coding.setSystem("http://terminology.hl7.org/CodeSystem/extra-security-role-type");
        // Imposta il display basato sul roleCode
        String display = getDisplayForRole(roleCode);
        if (display != null && !display.isEmpty()) {
            coding.setDisplay(display);
        }

        return cc;
    }

    private String getDisplayForRole(String roleCode) {
        if (roleCode == null || roleCode.isEmpty()) {
            return null;
        }

        String category = ClinicalDocumentPermission.getCategory(roleCode);
        if (category == null) {
            return roleCode;
        }

        switch (category) {
            case "MEDICO":
                return "Medical Professional";
            case "INFERMIERE_OSTETRICA":
                return "Nurse/Midwife";
            case "FARMACISTA":
                return "Pharmacist";
            case "ASSISTITO":
                return "Patient/Guardian";
            default:
                return roleCode;
        }
    }

    /**
     * Imposta l'observer (luogo di accesso) come Organization
     * 
     * @param organizationRef Reference all'Organization
     * @return Builder per method chaining
     */
    public AuditEventBuilder withObserverOrganization(String organizationRef) {
        if (organizationRef != null && !organizationRef.isEmpty()) {
            AuditEvent.AuditEventSourceComponent source = new AuditEvent.AuditEventSourceComponent();
            Reference sourceReference = new Reference();
            sourceReference.setReference(organizationRef);
            source.setObserver(sourceReference);
            this.auditEvent.setSource(source);
        }
        return this;
    }

    /**
     * Imposta l'outcome dell'evento (default: success)
     * 
     * @param outcome Outcome (0=Success, 4=Minor failure, 8=Serious failure,
     *                12=Major failure)
     * @return Builder per method chaining
     */
    public AuditEventBuilder withOutcome(AuditEvent.AuditEventOutcome outcome) {
        if (outcome != null) {
            this.auditEvent.setOutcome(outcome);
        }
        return this;
    }

    /**
     * Costruisce e restituisce l'AuditEvent finale
     * 
     * @return AuditEvent configurato
     * @throws IllegalStateException se mancano campi obbligatori
     */
    public AuditEvent build() {
        // Validazioni minime
        if (!this.auditEvent.hasRecorded()) {
            throw new IllegalStateException("AuditEvent must have recorded date");
        }

        if (!this.auditEvent.hasAction()) {
            throw new IllegalStateException("AuditEvent must have action");
        }

        // Imposta outcome di default se non specificato
        if (!this.auditEvent.hasOutcome()) {
            this.auditEvent.setOutcome(AuditEvent.AuditEventOutcome._0); // Success
        }

        return this.auditEvent;
    }

    // ============ PRIVATE HELPER METHODS ============

    /**
     * Estrae la versione da una risorsa FHIR
     * 
     * @param resource Risorsa FHIR
     * @return Versione della risorsa o "1" come default
     */
    private String extractVersion(Resource resource) {
        if (resource == null) {
            return "1";
        }

        if (resource.hasMeta() && resource.getMeta().hasVersionId()) {
            return resource.getMeta().getVersionId();
        }

        return "1";
    }

    /**
     * Verifica se una risorsa è di tipo amministrativo e non deve essere
     * inclusa nelle entity dell'AuditEvent.
     * 
     * Le risorse amministrative vengono usate come agent o observer,
     * non come risorse consultate (entity.what).
     * 
     * @param resourceType Tipo di risorsa FHIR
     * @return true se è una risorsa amministrativa
     */
    private boolean isAdministrativeResource(String resourceType) {
        return "Patient".equals(resourceType) ||
                "Practitioner".equals(resourceType) ||
                "PractitionerRole".equals(resourceType) ||
                "Location".equals(resourceType);
    }

    /**
     * Determina automaticamente il tipo di servizio consultato (entity.type)
     * basandosi sui tipi di risorse presenti nel bundle.
     * 
     * Per ora assume sempre profilo ASSISTITO (SERV_ASS_*).
     * In futuro verrà esteso per supportare anche PROFESSIONISTA via JWT.
     * 
     * @param resourceTypes Lista dei tipi di risorse cliniche presenti
     * @return AuditEntityTypeEnum appropriato
     */
    private AuditEntityTypeEnum determineEntityTypeFromResources(List<String> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            return AuditEntityTypeEnum.SERV_ASS_01; // Default: dati di sintesi
        }

        // Controllo per dossier farmaceutico
        if (resourceTypes.contains("MedicationDispense") ||
                resourceTypes.contains("MedicationStatement")) {
            return AuditEntityTypeEnum.SERV_ASS_03; // Dossier farmaceutico
        }

        // Controllo per percorso di cura
        if (resourceTypes.contains("CarePlan")) {
            return AuditEntityTypeEnum.SERV_ASS_05; // Percorso di cura
        }

        // Controllo per dati di sintesi (Composition)
        if (resourceTypes.contains("Composition")) {
            return AuditEntityTypeEnum.SERV_ASS_01; // Dati di sintesi
        }

        // Controllo per documento FSE
        if (resourceTypes.contains("DocumentReference")) {
            return AuditEntityTypeEnum.SERV_ASS_06; // Documento FSE
        }

        // Dati clinici generici (Observation, DiagnosticReport, Condition, ecc.)
        if (resourceTypes.contains("Observation") ||
                resourceTypes.contains("DiagnosticReport") ||
                resourceTypes.contains("Condition") ||
                resourceTypes.contains("AllergyIntolerance") ||
                resourceTypes.contains("Immunization") ||
                resourceTypes.contains("Procedure") ||
                resourceTypes.contains("Encounter")) {
            return AuditEntityTypeEnum.SERV_ASS_02; // Dati clinici
        }

        // Default: dati di sintesi
        return AuditEntityTypeEnum.SERV_ASS_01;
    }

    /**
     * Aggiunge la query string come entity.query in formato Base64.
     * 
     * @param queryString Query FHIR non codificata (es.
     *                    "Observation?patient=Patient/123")
     * @return Builder per method chaining
     */
    public AuditEventBuilder withQueryString(String queryString) {
        if (queryString != null && !queryString.isEmpty()) {
            try {
                // Codifica in Base64
                byte[] queryBytes = queryString.getBytes(StandardCharsets.UTF_8);
                byte[] base64Bytes = Base64.getEncoder().encode(queryBytes);

                // Crea o recupera la prima entity
                AuditEvent.AuditEventEntityComponent entity;
                if (this.auditEvent.hasEntity()) {
                    entity = this.auditEvent.getEntityFirstRep();
                } else {
                    entity = new AuditEvent.AuditEventEntityComponent();
                    this.auditEvent.addEntity(entity);
                }

                // Imposta la query
                entity.setQuery(base64Bytes);
                log.debug("Added query to AuditEvent entity: {}", queryString);
            } catch (Exception e) {
                log.error("Error encoding query string to Base64: {}", queryString, e);
            }
        }
        return this;
    }

    /**
     * Estrae e aggiunge la query dal Bundle response.
     * Cerca nei link self del bundle.
     * 
     * @param responseBundle Bundle di risposta FHIR
     * @return Builder per method chaining
     */
    public AuditEventBuilder withQueryFromBundle(Bundle responseBundle) {
        if (responseBundle != null && responseBundle.hasLink()) {
            for (Bundle.BundleLinkComponent link : responseBundle.getLink()) {
                if ("self".equals(link.getRelation()) && link.hasUrl()) {
                    String url = link.getUrl();
                    // Estrae la query dalla URL (parte dopo il '?')
                    int queryStart = url.indexOf('?');
                    if (queryStart > 0 && queryStart < url.length() - 1) {
                        String query = url.substring(queryStart + 1);
                        return withQueryString(query);
                    }
                }
            }
        }
        return this;
    }

    /**
     * Aggiunge un reference FHIR usando direttamente la stringa location
     * dal response bundle (già completa e corretta dal server).
     * 
     * @param location Location header dal response bundle (es.
     *                 "Observation/5/_history/1")
     * @return Builder per method chaining
     */
    private AuditEventBuilder withResourceReferenceFromLocation(String location) {
        if (location != null && !location.isEmpty()) {
            AuditEvent.AuditEventEntityComponent entity = new AuditEvent.AuditEventEntityComponent();
            entity.setWhat(new Reference(location));
            this.auditEvent.addEntity(entity);
        }
        return this;
    }

    /**
     * Estrae il resourceType da una stringa location.
     * Es: "Observation/5/_history/1" → "Observation"
     * 
     * @param location Location header dal response bundle
     * @return ResourceType o null se non valido
     */
    private String extractResourceTypeFromLocation(String location) {
        if (location == null || location.isEmpty()) {
            return null;
        }

        try {
            int firstSlash = location.indexOf('/');
            if (firstSlash > 0) {
                return location.substring(0, firstSlash);
            }
        } catch (Exception e) {
            log.warn("Failed to extract resource type from location: {}", location, e);
        }

        return null;
    }

    // ============ OBSCURATION HANDLING ============

    /**
     * Analizza il bundle per gestire automaticamente oscuramento/de-oscuramento.
     * 
     * Se il bundle contiene un DocumentReference:
     * - Con securityLabel P99 → Aggiunge purposeOfEvent=OSC e subtype=110129
     * - Senza securityLabel P99 → Aggiunge purposeOfEvent=DE_OSC (no subtype)
     * 
     * Se non c'è DocumentReference, non aggiunge nulla.
     * 
     * @param bundle Bundle da analizzare per la presenza di DocumentReference con
     *               P99
     * @return Builder per method chaining
     */
    public AuditEventBuilder withDocumentObscurationHandling(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return this;
        }

        // Verifica se c'è un DocumentReference nel bundle
        boolean hasDocumentReference = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .anyMatch(r -> r instanceof DocumentReference);

        if (!hasDocumentReference) {
            log.debug("No DocumentReference found in bundle - obscuration handling not applicable");
            return this;
        }

        // Verifica se ha il securityLabel P99 (oscuramento)
        boolean hasP99 = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof DocumentReference)
                .map(r -> (DocumentReference) r)
                .filter(DocumentReference::hasSecurityLabel)
                .flatMap(dr -> dr.getSecurityLabel().stream())
                .flatMap(cc -> cc.getCoding().stream())
                .anyMatch(coding -> "P99".equals(coding.getCode()));

        if (hasP99) {
            // Oscuramento: aggiungi purposeOfEvent OSC e subtype 110129
            this.withPurposeOfEvent(AuditPurposeOfEventEnum.OBSCURE);
            this.withSubtype(AuditEventSubtypeEnum.OBFUSCATION);
            log.info("Obscuration detected (P99) - added purposeOfEvent=OSC and subtype=110129");
        } else {
            // De-oscuramento: aggiungi solo purposeOfEvent DE_OSC
            this.withPurposeOfEvent(AuditPurposeOfEventEnum.DE_OBSCURE);
            log.info("De-obscuration detected (no P99) - added purposeOfEvent=DE_OSC");
        }

        return this;
    }
}
