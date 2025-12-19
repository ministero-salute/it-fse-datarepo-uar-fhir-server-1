/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import it.finanze.sanita.uar.audit.builder.AuditEventBuilder;
import it.finanze.sanita.uar.audit.config.AuditProperties;
import it.finanze.sanita.uar.audit.context.UarAuditContext;
import it.finanze.sanita.uar.audit.enums.AuditEntityTypeEnum;
import it.finanze.sanita.uar.audit.util.AuditEventHelper;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Interceptor per generare e aggiungere AuditEvent al Bundle di transazione
 * PRIMA del processing.
 * 
 * Questo interceptor viene eseguito PRIMA che la transazione venga processata
 * dal server.
 * Modifica il Bundle in ingresso aggiungendo un'entry con l'AuditEvent, in modo
 * che
 * tutto venga persistito in una singola transazione atomica.
 * 
 * Caratteristiche chiave:
 * - Aggiunge AuditEvent al Bundle PRIMA del processing
 * - Usa client-assigned IDs (UUIDs o fullUrl) per i riferimenti
 * - Singola transazione atomica (Bundle + AuditEvent)
 * - Previene ricorsione infinita
 * - Estrae patient/practitioner dal Bundle di richiesta (client IDs)
 */
@Component
@Interceptor
public class UarAuditEmitInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UarAuditEmitInterceptor.class);

    private final AuditProperties auditProperties;
    private final AuditEventHelper auditEventHelper;

    public UarAuditEmitInterceptor(
            @Autowired AuditProperties auditProperties,
            @Autowired AuditEventHelper auditEventHelper) {
        this.auditProperties = auditProperties;
        this.auditEventHelper = auditEventHelper;
    }

    /**
     * Hook eseguito quando viene processato un Bundle di transazione.
     * Qui aggiungiamo l'AuditEvent come entry del Bundle.
     */
    @Hook(Pointcut.STORAGE_TRANSACTION_PROCESSING)
    public void addAuditEventToTransactionBundle(
            RequestDetails requestDetails,
            Bundle theBundle) {

        String requestorRole = "APR"; // PRENDI DA HEADER O UserData
        String operationIDHeader = "SERV_WRITE_02";

        // Skip if auditing is disabled
        if (!auditProperties.isEnabled()) {
            return;
        }

        // Skip if this is an internal audit write (prevent recursion)
        if (UarAuditCaptureInterceptor.isInternalAuditWrite(requestDetails)) {
            return;
        }

        // Get the audit context captured during pre-processing
        UarAuditContext context = UarAuditCaptureInterceptor.getAuditContext(requestDetails);
        if (context == null) {
            log.debug("No audit context found, skipping audit generation");
            return;
        }
        IBaseResource res = requestDetails.getResource();
        theBundle = (Bundle) res;
        // Only process transaction bundles
        if (theBundle == null || theBundle.getType() != Bundle.BundleType.TRANSACTION) {
            log.debug("Not a transaction bundle, skipping audit generation");
            return;
        }

        try {

            // Estrae gli ID dal bundle
            String patientId = auditEventHelper.extractPatientId(theBundle);
            String requesterId = auditEventHelper.extractRequester(theBundle);
            String organizationId = auditEventHelper.extractOrganizationId(theBundle);

            // Build AuditEvent from the request bundle (using client IDs)
            AuditEvent audit = determineBuilderType(context.getRequestMethod())
                    .withEntityType(AuditEntityTypeEnum.get(operationIDHeader))
                    .withAgentRolePatientAndRequestor(patientId, requesterId, requestorRole)
                    .withObserverOrganization(organizationId)
                    .withDocumentObscurationHandling(theBundle)
                    .withResourcesFromRequestBundle(theBundle)
                    .build();

            addAuditEventToBundle(theBundle, audit);

        } catch (Exception e) {
            log.error("Error while adding AuditEvent to transactional bundle", e);
        }
    }

    private AuditEventBuilder determineBuilderType(RequestTypeEnum requestMethod) {
        if (requestMethod == null) {
            return null;
        }

        switch (requestMethod) {
            case POST:
                return AuditEventBuilder.forDataCreation();
            case PUT:
                return AuditEventBuilder.forDataUpdate();
            case DELETE:
                return AuditEventBuilder.forDataDeletion();
            case GET:
                return AuditEventBuilder.forDataConsultation();
            default:
                return null;
        }
    }

    /**
     * Aggiunge l'AuditEvent al Bundle come ultima entry con request POST.
     * L'AuditEvent verrà creato dal server FHIR insieme alle altre risorse nella
     * stessa transazione.
     * 
     * @param bundle     Bundle di richiesta al quale aggiungere l'AuditEvent
     * @param auditEvent AuditEvent da aggiungere
     */
    private void addAuditEventToBundle(Bundle bundle, AuditEvent auditEvent) {
        BundleEntryComponent auditEntry = new BundleEntryComponent();
        auditEntry.setResource(auditEvent);

        // Imposta la request per POST
        Bundle.BundleEntryRequestComponent request = new Bundle.BundleEntryRequestComponent();
        request.setMethod(Bundle.HTTPVerb.POST);
        request.setUrl("AuditEvent");
        auditEntry.setRequest(request);

        // Aggiungi come ultima entry
        bundle.addEntry(auditEntry);
        log.debug("Added AuditEvent to Bundle as entry #{} with method POST", bundle.getEntry().size());
    }
}
