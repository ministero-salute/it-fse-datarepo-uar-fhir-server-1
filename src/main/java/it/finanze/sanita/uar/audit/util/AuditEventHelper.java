/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.util;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.RelatedPerson;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.finanze.sanita.uar.audit.enums.AuditPurposeOfEventEnum;

/**
 * Helper class per la gestione degli AuditEvent
 * Fornisce metodi di utilità per estrazione dati da Bundle e conversioni
 */
@Component
public class AuditEventHelper {
    
    private static final Logger log = LoggerFactory.getLogger(AuditEventHelper.class);
    /**
     * Estrae il fullUrl del Patient dal Bundle.
     * Usa il fullUrl identico per garantire la risoluzione in-memory da parte di
     * HAPI FHIR.
     * 
     * @param bundle Bundle contenente le risorse
     * @return fullUrl del Patient (es. "https://example/Patient/123") o null se non
     *         trovato
     */
    public String extractPatientId(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }
        
        return bundle.getEntry().stream()
            .map(Bundle.BundleEntryComponent::getResource)
            .filter(r -> r instanceof Patient)
            .map(r -> "Patient/" + r.getIdElement().getIdPart())
            .findFirst()
            .orElse(null);
    }

    /**
     * Estrae il fullUrl del Patient da Bundle Response.
     * Usa il fullUrl identico per garantire la risoluzione in-memory da parte di
     * HAPI FHIR.
     * 
     * @param bundle Bundle contenente le risorse
     * @return fullUrl del Patient (es. "https://example/Patient/123") o null se non
     *         trovato
     */
    public String extractPatientIdFromResponseBundle(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }

        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResponse)
                .filter(Objects::nonNull)
                .map(Bundle.BundleEntryResponseComponent::getLocation)
                .filter(Objects::nonNull)
                .filter(location -> location.startsWith("Patient/"))
                .findFirst()
                .orElse(null);

    }

    /**
     * Estrae il fullUrl del Patient da Bundle Response.
     * Usa il fullUrl identico per garantire la risoluzione in-memory da parte di
     * HAPI FHIR.
     * 
     * @param bundle Bundle contenente le risorse
     * @return fullUrl del Patient (es. "https://example/Patient/123") o null se non
     *         trovato
     */
    public String extractPractitionerRoleIdFromResponseBundle(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }

        String practitionerRole = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResponse)
                .filter(Objects::nonNull)
                .map(Bundle.BundleEntryResponseComponent::getLocation)
                .filter(Objects::nonNull)
                .filter(location -> location.startsWith("PractitionerRole/"))
                .findFirst()
                .orElse(null);

        if (practitionerRole != null) {
            return practitionerRole;
        }

        // Se non trovato, cerca Practitioner
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResponse)
                .filter(Objects::nonNull)
                .map(Bundle.BundleEntryResponseComponent::getLocation)
                .filter(Objects::nonNull)
                .filter(location -> location.startsWith("Practitioner/"))
                .findFirst()
                .orElse(null);

    }

    /**
     * Estrae il fullUrl del PractitionerRole o Practitioner dal Bundle.
     * Usa il fullUrl identico per garantire la risoluzione in-memory da parte di
     * HAPI FHIR.
     * 
     * @param bundle Bundle contenente le risorse
     * @return fullUrl del PractitionerRole/Practitioner o null se non trovato
     */
    public String extractPractitionerRole(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }
        
        // Prima cerca PractitionerRole
        String practitionerRole = bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof PractitionerRole)
                .map(Bundle.BundleEntryComponent::getFullUrl)
            .findFirst()
            .orElse(null);
        
        if (practitionerRole != null) {
            return practitionerRole;
        }
        
        // Se non trovato, cerca Practitioner
        return bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof Practitioner)
                .map(Bundle.BundleEntryComponent::getFullUrl)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Estrae il reference del richiedente (agent.who) dal Bundle.
     * Cerca in ordine di priorità: PractitionerRole, Practitioner, RelatedPerson,
     * Patient.
     * 
     * Questo metodo supporta tutti i 4 tipi di risorse ammessi per
     * AuditEvent.agent.who
     * secondo le specifiche FSE 2.0:
     * - Practitioner: professionista sanitario
     * - PractitionerRole: ruolo del professionista
     * - Patient: paziente stesso (accesso ai propri dati)
     * - RelatedPerson: tutore, delegato o familiare
     * 
     * @param bundle Bundle contenente le risorse
     * @return fullUrl del richiedente o null se non trovato
     */
    public String extractRequester(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }

        // TODO: Confronta con Ruolo in JWT e fai check con quello

        // 1. Prima cerca PractitionerRole (professionista con ruolo specifico)
        String practitionerRole = bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof PractitionerRole)
                .map(Bundle.BundleEntryComponent::getFullUrl)
                .findFirst()
                .orElse(null);

        if (practitionerRole != null) {
            log.debug("Found PractitionerRole as requester: {}", practitionerRole);
            return practitionerRole;
        }

        // 2. Cerca Practitioner (professionista generico)
        String practitioner = bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof Practitioner)
                .map(Bundle.BundleEntryComponent::getFullUrl)
                .findFirst()
                .orElse(null);

        if (practitioner != null) {
            log.debug("Found Practitioner as requester: {}", practitioner);
            return practitioner;
        }

        // 3. Cerca RelatedPerson (tutore, delegato, familiare)
        String relatedPerson = bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof RelatedPerson)
                .map(Bundle.BundleEntryComponent::getFullUrl)
                .findFirst()
                .orElse(null);

        if (relatedPerson != null) {
            log.debug("Found RelatedPerson as requester: {}", relatedPerson);
            return relatedPerson;
        }

        log.warn("No requester found in bundle (searched: PractitionerRole, Practitioner, RelatedPerson, Patient)");
        return null;
    }

    /**
     * Estrae il reference del richiedente dal Bundle response.
     * Cerca in ordine di priorità: PractitionerRole, Practitioner, RelatedPerson,
     * Patient.
     * 
     * @param responseBundle Bundle di risposta dalla transazione FHIR
     * @return Reference completo del richiedente o null se non trovato
     */
    public String extractRequesterFromResponse(Bundle responseBundle) {
        if (responseBundle == null || !responseBundle.hasEntry()) {
            return null;
        }

        // 1. Prima cerca PractitionerRole
        String practitionerRole = extractResourceFromResponse(responseBundle, "PractitionerRole");
        if (practitionerRole != null) {
            return practitionerRole;
        }

        // 2. Cerca Practitioner
        String practitioner = extractResourceFromResponse(responseBundle, "Practitioner");
        if (practitioner != null) {
            return practitioner;
        }

        // 3. Cerca RelatedPerson
        String relatedPerson = extractResourceFromResponse(responseBundle, "RelatedPerson");
        if (relatedPerson != null) {
            return relatedPerson;
        }

        // 4. Fallback: Patient
        return extractResourceFromResponse(responseBundle, "Patient");
    }

    /**
     * Estrae il fullUrl dell'Organization dal Bundle.
     * Usa il fullUrl identico per garantire la risoluzione in-memory da parte di
     * HAPI FHIR.
     * 
     * @param bundle Bundle contenente le risorse
     * @return fullUrl dell'Organization (es. "https://example/Organization/123") o
     *         null se non trovato
     */
    public String extractOrganizationId(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }
        
        return bundle.getEntry().stream()
                .filter(entry -> entry.getResource() instanceof org.hl7.fhir.r4.model.Organization)
                .map(Bundle.BundleEntryComponent::getFullUrl)
                .findFirst()
                .orElse(null);
    }

    /**
     * Estrae il fullUrl dell'Organization dal Bundle.
     * Usa il fullUrl identico per garantire la risoluzione in-memory da parte di
     * HAPI FHIR.
     * 
     * @param bundle Bundle contenente le risorse
     * @return fullUrl dell'Organization (es. "https://example/Organization/123") o
     *         null se non trovato
     */
    public String extractOrganizationIdFromResponseBudle(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }
        return bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResponse)
                .filter(Objects::nonNull)
                .map(Bundle.BundleEntryResponseComponent::getLocation)
                .filter(Objects::nonNull)
                .filter(location -> location.startsWith("Organization/"))
                .findFirst().orElse(null);

    }
    
    /**
     * Estrae il reference al Patient dal Bundle response.
     * Utilizza direttamente il location header fornito dal server FHIR.
     * 
     * Il reference è già completo e corretto dal server, includendo history e versioning.
     * 
     * @param responseBundle Bundle di risposta dalla transazione FHIR
     * @return Reference completo al Patient (es. "Patient/123/_history/1") o null se non trovato
     */
    public String extractPatientIdFromResponse(Bundle responseBundle) {
        return extractResourceFromResponse(responseBundle, "Patient");
    }
    
    /**
     * Estrae il reference al PractitionerRole/Practitioner dal Bundle response.
     * Utilizza direttamente il location header fornito dal server FHIR.
     * 
     * Cerca prima PractitionerRole, poi Practitioner se non trovato.
     * Il reference è già completo e corretto dal server, includendo history e versioning.
     * 
     * @param responseBundle Bundle di risposta dalla transazione FHIR
     * @return Reference completo (es. "PractitionerRole/123/_history/1") o null se non trovato
     */
    public String extractPractitionerRoleFromResponse(Bundle responseBundle) {
        // Prima cerca PractitionerRole
        String practitionerRole = extractResourceFromResponse(responseBundle, "PractitionerRole");
        if (practitionerRole != null) {
            return practitionerRole;
        }
        
        // Se non trovato, cerca Practitioner
        return extractResourceFromResponse(responseBundle, "Practitioner");
    }
    
    /**
     * Estrae il reference all'Organization dal Bundle response.
     * Utilizza direttamente il location header fornito dal server FHIR.
     * 
     * Il reference è già completo e corretto dal server, includendo history e versioning.
     * 
     * @param responseBundle Bundle di risposta dalla transazione FHIR
     * @return Reference completo all'Organization (es. "Organization/123/_history/1") o null se non trovato
     */
    public String extractOrganizationIdFromResponse(Bundle responseBundle) {
        return extractResourceFromResponse(responseBundle, "Organization");
    }
    
    /**
     * Metodo helper generico per estrarre un reference dal response bundle.
     * Utilizza direttamente il location header senza parsing o ricostruzione.
     * 
     * @param responseBundle Bundle di risposta dalla transazione FHIR
     * @param resourceType Tipo di risorsa da cercare (es. "Patient", "Organization")
     * @return Location header completo o null se non trovato
     */
    private String extractResourceFromResponse(Bundle responseBundle, String resourceType) {
        if (responseBundle == null || !responseBundle.hasEntry() || resourceType == null) {
            return null;
        }
        
        for (Bundle.BundleEntryComponent entry : responseBundle.getEntry()) {
            if (!entry.hasResponse() || !entry.getResponse().hasLocation()) {
                continue;
            }
            
            String location = entry.getResponse().getLocation();
            if (location.startsWith(resourceType + "/")) {
                log.debug("Extracted {} reference from response: {}", resourceType, location);
                return location;
            }
        }
        
        log.debug("No {} found in response bundle", resourceType);
        return null;
    }
    
    /**
     * Estrae la versione di una risorsa FHIR
     * @param resource Risorsa FHIR
     * @return Versione della risorsa o "1" come default
     */
    public String extractResourceVersion(Resource resource) {
        if (resource == null) {
            return "1";
        }
        
        if (resource.hasMeta() && resource.getMeta().hasVersionId()) {
            return resource.getMeta().getVersionId();
        }
        
        // Default se non c'è versione
        return "1";
    }
    
    /**
     * Converte una stringa in Base64
     * @param input Stringa da convertire
     * @return Stringa codificata in Base64
     */
    public String encodeToBase64(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        
        try {
            byte[] encodedBytes = Base64.getEncoder().encode(input.getBytes(StandardCharsets.UTF_8));
            return new String(encodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error encoding string to Base64: {}", input, e);
            return "";
        }
    }
    
    /**
     * Decodifica una stringa Base64
     * @param base64Input Stringa Base64
     * @return Stringa decodificata
     */
    public String decodeFromBase64(String base64Input) {
        if (base64Input == null || base64Input.isEmpty()) {
            return "";
        }
        
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Input.getBytes(StandardCharsets.UTF_8));
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Error decoding Base64 string: {}", base64Input, e);
            return "";
        }
    }

    /**
     * Analizza il bundle per rilevare se contiene un DocumentReference con
     * securityLabel P99 (oscuramento).
     * 
     * Il codice P99 nel securityLabel indica "Oscuramento del documento" secondo
     * il sistema urn:oid:2.16.840.1.113883.2.9.3.3.6.1.3
     * 
     * @param bundle Bundle contenente le risorse
     * @return true se è presente securityLabel P99 (oscuramento), false altrimenti
     *         (de-oscuramento)
     */
    public boolean hasObscurationSecurityLabel(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return false;
        }

        boolean hasP99 = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof DocumentReference)
                .map(r -> (DocumentReference) r)
                .filter(DocumentReference::hasSecurityLabel)
                .flatMap(dr -> dr.getSecurityLabel().stream())
                .flatMap(cc -> cc.getCoding().stream())
                .anyMatch(coding -> "P99".equals(coding.getCode()) &&
                        "urn:oid:2.16.840.1.113883.2.9.3.3.6.1.3".equals(coding.getSystem()));

        if (hasP99) {
            log.debug("Found DocumentReference with P99 securityLabel (obscuration)");
        } else {
            log.debug("No P99 securityLabel found in DocumentReference (de-obscuration or not applicable)");
        }

        return hasP99;
    }

    /**
     * Determina il purposeOfEvent basato sul securityLabel P99 del
     * DocumentReference.
     * 
     * Se il bundle contiene un DocumentReference:
     * - Con securityLabel P99 → OBSCURE (oscuramento)
     * - Senza securityLabel P99 → DE_OBSCURE (de-oscuramento)
     * 
     * Se non c'è DocumentReference nel bundle, restituisce null (non applicabile).
     * 
     * @param bundle Bundle contenente le risorse
     * @return OBSCURE se P99 presente, DE_OBSCURE altrimenti, null se non c'è
     *         DocumentReference
     */
    public AuditPurposeOfEventEnum determineObscurationPurpose(Bundle bundle) {
        if (bundle == null || !bundle.hasEntry()) {
            return null;
        }

        // Verifica se c'è un DocumentReference nel bundle
        boolean hasDocumentReference = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .anyMatch(r -> r instanceof DocumentReference);

        if (!hasDocumentReference) {
            log.debug("No DocumentReference found in bundle - obscuration purpose not applicable");
            return null; // Non applicabile se non c'è DocumentReference
        }

        // Se ha P99 → Oscuramento, altrimenti → De-oscuramento
        boolean isObscuration = hasObscurationSecurityLabel(bundle);
        AuditPurposeOfEventEnum purpose = isObscuration
                ? AuditPurposeOfEventEnum.OBSCURE
                : AuditPurposeOfEventEnum.DE_OBSCURE;

        log.info("Determined obscuration purpose: {} for DocumentReference", purpose.getCode());
        return purpose;
    }
}
