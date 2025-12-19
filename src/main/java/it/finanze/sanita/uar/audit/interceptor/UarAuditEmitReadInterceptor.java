/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.interceptor;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.jpa.api.dao.DaoRegistry;
import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.rest.api.RequestTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.api.server.ResponseDetails;
import ca.uhn.fhir.rest.api.server.SystemRequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InternalErrorException;
import it.finanze.sanita.uar.audit.builder.AuditEventBuilder;
import it.finanze.sanita.uar.audit.config.AuditProperties;
import it.finanze.sanita.uar.audit.context.UarAuditContext;
import it.finanze.sanita.uar.audit.enums.AuditEntityTypeEnum;
import it.finanze.sanita.uar.audit.util.AuditEventHelper;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.Bundle;
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
public class UarAuditEmitReadInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UarAuditEmitTransactionInterceptor.class);

    private final AuditProperties auditProperties;
    private final AuditEventHelper auditEventHelper;
    private final DaoRegistry daoRegistry;

    public UarAuditEmitReadInterceptor(
            @Autowired AuditProperties auditProperties,
            @Autowired AuditEventHelper auditEventHelper,
            @Autowired DaoRegistry daoRegistry) {
        this.auditProperties = auditProperties;
        this.auditEventHelper = auditEventHelper;
        this.daoRegistry = daoRegistry;
    }

    /**
     * Hook eseguito quando il server sta per restituire una risposta HTTP.
     * Questo è l'hook più affidabile per l'audit di consultazione perché si attiva SEMPRE,
     * indipendentemente dal tipo di query (anche con _revinclude, _include, etc.).
     * Genera un AuditEvent separato per ogni query GET eseguita.
     */
    @Hook(Pointcut.SERVER_OUTGOING_RESPONSE)
    public void addAuditEventForQuery(
            RequestDetails requestDetails,
            ResponseDetails responseDetails) {

        String requestorRole = "ASS"; // TODO: PRENDI DA HEADER/context
        String operationIDHeader = "SERV_ASS_01"; // TODO: PRENDI DA HEADER/context

        // Skip if auditing is disabled
        if (!auditProperties.isEnabled()) {
            return;
        }

        // Skip if this is an internal audit write (prevent recursion)
        if (UarAuditCaptureInterceptor.isInternalAuditWrite(requestDetails)) {
            return;
        }

        // Only process GET requests (queries)
        if (requestDetails.getRequestType() != RequestTypeEnum.GET) {
            log.debug("Not a GET request, skipping audit for query");
            return;
        }

        // Get the audit context captured during pre-processing
        UarAuditContext context = UarAuditCaptureInterceptor.getAuditContext(requestDetails);
        if (context == null) {
            log.debug("No audit context found, skipping audit generation");
            return;
        }

        // Get response resource - should be a Bundle for search queries
        IBaseResource responseResource = responseDetails.getResponseResource();
        if (responseResource == null) {
            log.debug("No response resource, skipping audit generation");
            return;
        }

        // Only process Bundle responses (search results)
        if (!(responseResource instanceof Bundle)) {
            log.debug("Response is not a Bundle (type: {}), skipping audit for query", 
                    responseResource.getClass().getSimpleName());
            return;
        }

        Bundle responseBundle = (Bundle) responseResource;

        try {
            // Extract IDs from response resources
            String patientId = auditEventHelper.extractPatientIdFromResponseBundle(responseBundle);
            String requesterId = auditEventHelper.extractRequesterFromResponse(responseBundle);
            String organizationId = auditEventHelper.extractOrganizationIdFromResponseBudle(responseBundle);

            // Fallback: if no separate requester, use patient as requester
            if (requesterId == null && patientId != null) {
                requesterId = patientId;
            }

            // Extract query string from request URL
            String queryString = context.getRequestUrl();
            if (queryString != null && queryString.contains("?")) {
                queryString = queryString.substring(queryString.indexOf("?") + 1);
            }

            // Build AuditEvent for query/consultation
            AuditEvent audit = AuditEventBuilder.forDataConsultation()
                    .withEntityType(AuditEntityTypeEnum.get(operationIDHeader))
                    .withAgentRolePatientAndRequestor(patientId, requesterId, requestorRole)
                    .withObserverOrganization(organizationId)
                    .withQueryString(queryString)
                    .withResourcesFromResponseBundle(responseBundle)
                    .build();

            // Persist the AuditEvent (separate transaction)
            // CRITICAL: If audit fails, block data access
            persistAuditEvent(audit, requestDetails);

        } catch (InternalErrorException e) {
            // Re-throw audit persistence failures to block data access
            log.error("CRITICAL: Audit persistence failed - blocking data access", e);
            throw e;
        } catch (Exception e) {
            // Wrap other exceptions and block data access
            log.error("CRITICAL: Error while creating AuditEvent for query - blocking data access", e);
            throw new InternalErrorException(
                    "Impossibile generare audit per l'accesso ai dati. L'operazione è stata bloccata per motivi di sicurezza.",
                    e);
        }
    }

    /**
     * Persiste l'AuditEvent come risorsa separata usando il DAO.
     * Questo metodo viene usato per query/consultazioni dove l'AuditEvent
     * non può essere aggiunto al Bundle di risposta (già generato).
     * 
     * CRITICAL SECURITY REQUIREMENT:
     * Se la persistenza dell'audit fallisce, viene lanciata un'eccezione
     * che blocca l'accesso ai dati. Questo garantisce che nessun dato
     * venga restituito senza essere auditato.
     * 
     * Crea una SystemRequestDetails per evitare ricorsione infinita marcando
     * la richiesta come interna.
     * 
     * @param audit                  AuditEvent da persistere
     * @param originalRequestDetails RequestDetails originale della query
     * @throws InternalErrorException se la persistenza fallisce
     */
    private void persistAuditEvent(AuditEvent audit, RequestDetails originalRequestDetails) {
        try {
            // Get the AuditEvent DAO from registry
            IFhirResourceDao<AuditEvent> auditEventDao = daoRegistry.getResourceDao(AuditEvent.class);
            SystemRequestDetails systemRequest = new SystemRequestDetails();
            UarAuditContext internalContext = new UarAuditContext();
            internalContext.setInternalAuditWrite(true);
            systemRequest.getUserData().put(UarAuditCaptureInterceptor.AUDIT_CONTEXT_KEY, internalContext);

            // Persist the AuditEvent
            auditEventDao.create(audit, systemRequest);

            log.info("Successfully persisted AuditEvent for query operation");

        } catch (Exception e) {
            // CRITICAL: Log and re-throw to block data access
            log.error("CRITICAL: Failed to persist AuditEvent - blocking data access to prevent unaudited operations",
                    e);
            throw new InternalErrorException(
                    "Impossibile registrare l'evento di audit. Per motivi di sicurezza, l'accesso ai dati è stato negato.",
                    e);
        }
    }
}
