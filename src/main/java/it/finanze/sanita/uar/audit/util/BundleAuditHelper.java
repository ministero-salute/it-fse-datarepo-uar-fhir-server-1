/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.util;

import it.finanze.sanita.uar.audit.config.AuditProperties;
import it.finanze.sanita.uar.audit.enums.AuditEntityTypeEnum;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Helper utility per estrarre informazioni dai Bundle per la generazione di AuditEvent.
 * 
 * Fornisce metodi per:
 * - Estrarre Patient ID dal Bundle
 * - Estrarre Practitioner/PractitionerRole ID dal Bundle
 * - Determinare il tipo di servizio consultato (entity.type) in base alle risorse presenti
 */
@Component
public class BundleAuditHelper {

    private static final Logger log = LoggerFactory.getLogger(BundleAuditHelper.class);

    private final AuditProperties auditProperties;

    @Autowired
    public BundleAuditHelper(AuditProperties auditProperties) {
        this.auditProperties = auditProperties;
    }

    /**
     * Estrae il Patient ID dal Bundle di richiesta (client IDs/fullUrls).
     * Cerca una risorsa Patient nel Bundle e restituisce il fullUrl o ID client.
     * 
     * @param bundle Bundle di richiesta con client IDs
     * @return Patient ID (fullUrl o client ID) o null se non trovato
     */
    public String extractPatientIdFromRequestBundle(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }

        // Cerca Patient nelle entry del bundle
        Optional<Bundle.BundleEntryComponent> patientEntry = bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof Patient)
                .findFirst();

        if (patientEntry.isPresent()) {
            Bundle.BundleEntryComponent entry = patientEntry.get();
            
            // Preferisci fullUrl se presente
            if (entry.hasFullUrl()) {
                log.debug("Found Patient in request bundle with fullUrl: {}", entry.getFullUrl());
                return entry.getFullUrl();
            }
            
            // Altrimenti usa l'ID della risorsa se presente
            Patient patient = (Patient) entry.getResource();
            if (patient.hasId()) {
                String patientId = patient.getId();
                log.debug("Found Patient in request bundle with ID: {}", patientId);
                return patientId;
            }
        }

        log.debug("No Patient found in request bundle");
        return null;
    }

    /**
     * Estrae il Practitioner o PractitionerRole ID dal Bundle di richiesta (client IDs/fullUrls).
     * Cerca una risorsa Practitioner o PractitionerRole nel Bundle.
     * 
     * @param bundle Bundle di richiesta con client IDs
     * @return Practitioner/PractitionerRole ID (fullUrl o client ID) o null se non trovato
     */
    public String extractPractitionerIdFromRequestBundle(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }

        // Cerca Practitioner o PractitionerRole nelle entry
        Optional<Bundle.BundleEntryComponent> practitionerEntry = bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof Practitioner || 
                               entry.getResource() instanceof PractitionerRole)
                .findFirst();

        if (practitionerEntry.isPresent()) {
            Bundle.BundleEntryComponent entry = practitionerEntry.get();
            
            // Preferisci fullUrl se presente
            if (entry.hasFullUrl()) {
                log.debug("Found Practitioner/PractitionerRole in request bundle with fullUrl: {}", 
                         entry.getFullUrl());
                return entry.getFullUrl();
            }
            
            // Altrimenti usa l'ID della risorsa
            if (entry.getResource().hasId()) {
                String id = entry.getResource().getId();
                log.debug("Found Practitioner/PractitionerRole in request bundle with ID: {}", id);
                return id;
            }
        }

        log.debug("No Practitioner/PractitionerRole found in request bundle");
        return null;
    }

    /**
     * Estrae il Patient ID dal Bundle di risposta.
     * Cerca una risorsa Patient nel Bundle e restituisce un reference con versione.
     * 
     * @param bundle Bundle di risposta dal server
     * @return Patient ID nel formato "Patient/id/_history/version" o null se non trovato
     */
    public String extractPatientIdFromBundle(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }

        Optional<String> patientLocation = bundle.getEntry().stream()
                .filter(entry -> entry.hasResponse() && entry.getResponse().hasLocation())
                .map(entry -> entry.getResponse().getLocation())
                .filter(location -> location.startsWith("Patient/"))
                .findFirst();

        if (patientLocation.isPresent()) {
            log.debug("Found Patient in bundle: {}", patientLocation.get());
            return patientLocation.get();
        }

        // Fallback: cerca Patient nelle risorse del bundle
        Optional<Resource> patientResource = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(resource -> resource instanceof Patient)
                .findFirst();

        if (patientResource.isPresent()) {
            Resource patient = patientResource.get();
            String patientId = patient.getIdElement().getIdPart();
            if (patientId != null && !patientId.isEmpty()) {
                String versionId = patient.getMeta() != null ? patient.getMeta().getVersionId() : "1";
                String reference = "Patient/" + patientId + "/_history/" + versionId;
                log.debug("Extracted Patient from resource: {}", reference);
                return reference;
            }
        }

        log.debug("No Patient found in bundle");
        return null;
    }

    /**
     * Estrae il Practitioner o PractitionerRole ID dal Bundle di risposta.
     * Cerca una risorsa Practitioner o PractitionerRole nel Bundle.
     * 
     * @param bundle Bundle di risposta dal server
     * @return Practitioner/PractitionerRole ID nel formato "Practitioner/id/_history/version" o null se non trovato
     */
    public String extractPractitionerIdFromBundle(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }

        // Prima cerca nei location headers della response
        Optional<String> practitionerLocation = bundle.getEntry().stream()
                .filter(entry -> entry.hasResponse() && entry.getResponse().hasLocation())
                .map(entry -> entry.getResponse().getLocation())
                .filter(location -> location.startsWith("Practitioner/") || location.startsWith("PractitionerRole/"))
                .findFirst();

        if (practitionerLocation.isPresent()) {
            log.debug("Found Practitioner/PractitionerRole in bundle: {}", practitionerLocation.get());
            return practitionerLocation.get();
        }

        // Fallback: cerca nelle risorse del bundle
        Optional<Resource> practitionerResource = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(resource -> resource instanceof Practitioner || resource instanceof PractitionerRole)
                .findFirst();

        if (practitionerResource.isPresent()) {
            Resource practitioner = practitionerResource.get();
            String practitionerId = practitioner.getIdElement().getIdPart();
            String resourceType = practitioner.fhirType();
            if (practitionerId != null && !practitionerId.isEmpty()) {
                String versionId = practitioner.getMeta() != null ? practitioner.getMeta().getVersionId() : "1";
                String reference = resourceType + "/" + practitionerId + "/_history/" + versionId;
                log.debug("Extracted Practitioner from resource: {}", reference);
                return reference;
            }
        }

        log.debug("No Practitioner/PractitionerRole found in bundle");
        return null;
    }

    /**
     * Determina il tipo di servizio consultato (entity.type) in base ai tipi di risorse
     * presenti nel Bundle.
     * 
     * Implementa una logica di priorità per determinare il servizio più appropriato:
     * 1. Dossier farmaceutico (MedicationDispense, MedicationStatement)
     * 2. Percorso di cura (CarePlan)
     * 3. Documento FSE (DocumentReference)
     * 4. Dati clinici (Observation, DiagnosticReport, Condition, etc.)
     * 5. Dati di sintesi (Composition)
     * 6. Default: SERV_ASS_01
     * 
     * @param bundle Bundle di risposta dal server
     * @return Tipo di servizio appropriato
     */
    public AuditEntityTypeEnum determineEntityTypeFromBundle(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return getDefaultEntityType();
        }

        // Raccogli tutti i tipi di risorse nel bundle (escludendo amministrative)
        Set<String> resourceTypes = new HashSet<>();
        bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(resource -> resource != null)
                .map(Resource::fhirType)
                .filter(this::isNotAdministrativeResource)
                .forEach(resourceTypes::add);

        if (resourceTypes.isEmpty()) {
            return getDefaultEntityType();
        }

        log.debug("Resource types in bundle: {}", resourceTypes);

        // Logica di priorità
        
        // 1. Dossier farmaceutico
        if (resourceTypes.contains("MedicationDispense") || resourceTypes.contains("MedicationStatement")) {
            return AuditEntityTypeEnum.SERV_ASS_03;
        }

        // 2. Percorso di cura
        if (resourceTypes.contains("CarePlan")) {
            return AuditEntityTypeEnum.SERV_ASS_05;
        }

        // 3. Documento FSE
        if (resourceTypes.contains("DocumentReference")) {
            return AuditEntityTypeEnum.SERV_ASS_06;
        }

        // 4. Dati clinici generici
        if (resourceTypes.contains("Observation") ||
            resourceTypes.contains("DiagnosticReport") ||
            resourceTypes.contains("Condition") ||
            resourceTypes.contains("AllergyIntolerance") ||
            resourceTypes.contains("Immunization") ||
            resourceTypes.contains("Procedure") ||
            resourceTypes.contains("Encounter")) {
            return AuditEntityTypeEnum.SERV_ASS_02;
        }

        // 5. Dati di sintesi
        if (resourceTypes.contains("Composition")) {
            return AuditEntityTypeEnum.SERV_ASS_01;
        }

        // 6. Default
        return getDefaultEntityType();
    }

    /**
     * Verifica se un tipo di risorsa è amministrativo (non clinico).
     * 
     * @param resourceType Tipo di risorsa FHIR
     * @return true se NON è amministrativo
     */
    private boolean isNotAdministrativeResource(String resourceType) {
        return !"Patient".equals(resourceType) &&
               !"Practitioner".equals(resourceType) &&
               !"PractitionerRole".equals(resourceType) &&
               !"Organization".equals(resourceType) &&
               !"Location".equals(resourceType);
    }

    /**
     * Restituisce il tipo di servizio di default dalla configurazione.
     * 
     * @return Tipo di servizio di default
     */
    private AuditEntityTypeEnum getDefaultEntityType() {
        String defaultCode = auditProperties.getService().getDefaultCode();
        
        // Trova l'enum corrispondente al codice di default
        for (AuditEntityTypeEnum type : AuditEntityTypeEnum.values()) {
            if (type.getCode().equals(defaultCode)) {
                return type;
            }
        }
        
        // Fallback estremo
        return AuditEntityTypeEnum.SERV_ASS_01;
    }
}
