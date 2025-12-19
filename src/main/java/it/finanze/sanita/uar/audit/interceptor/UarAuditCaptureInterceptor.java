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
import it.finanze.sanita.uar.audit.config.AuditProperties;
import it.finanze.sanita.uar.audit.context.UarAuditContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Interceptor per catturare informazioni di audit durante la fase di pre-processing della richiesta.
 * 
 * Questo interceptor viene eseguito PRIMA che la richiesta venga processata dal server FHIR.
 * Cattura:
 * - Metodo HTTP (POST, PUT, DELETE, GET)
 * - URL completa della richiesta
 * - Ruolo dell'utente da header HTTP
 * - Parametri di query
 * 
 * Le informazioni catturate vengono memorizzate in un UarAuditContext associato alla richiesta
 * e saranno utilizzate dall'interceptor di emissione per costruire l'AuditEvent.
 */
@Component
@Interceptor
public class UarAuditCaptureInterceptor {

    private static final Logger log = LoggerFactory.getLogger(UarAuditCaptureInterceptor.class);
    
    public static final String AUDIT_CONTEXT_KEY = "UAR_AUDIT_CONTEXT";
    public static final String AUDIT_INTERNAL_FLAG = "UAR_AUDIT_INTERNAL";

    private final AuditProperties auditProperties;

    @Autowired
    public UarAuditCaptureInterceptor(AuditProperties auditProperties) {
        this.auditProperties = auditProperties;
    }

    /**
     * Hook eseguito prima della gestione della richiesta.
     * Cattura i metadati necessari per l'audit.
     */
    @Hook(Pointcut.SERVER_INCOMING_REQUEST_PRE_HANDLED)
    public void captureRequestMetadata(RequestDetails requestDetails) {
        
        // Skip if auditing is disabled
        if (!auditProperties.isEnabled()) {
            return;
        }

        // Skip if this is an AuditEvent resource request (avoid recursion)
        if (isAuditEventRequest(requestDetails)) {
            log.debug("Skipping audit capture for AuditEvent request to prevent recursion");
            markAsInternalAuditWrite(requestDetails);
            return;
        }

        try {
            // Create audit context
            UarAuditContext context = new UarAuditContext();
            context.setRequestMethod(requestDetails.getRequestType());
            context.setRequestUrl(requestDetails.getCompleteUrl());
            context.setQueryParams(requestDetails.getParameters());
            
            // Extract role from HTTP header
            String roleHeaderName = auditProperties.getHeaders().getRoleHeaderName();
            String role = requestDetails.getHeader(roleHeaderName);
            context.setRole(role);
            
            // Store context in request userData for later use
            requestDetails.getUserData().put(AUDIT_CONTEXT_KEY, context);
            
            log.debug("Captured audit context: method={}, url={}, role={}", 
                    context.getRequestMethod(), context.getRequestUrl(), context.getRole());
            
        } catch (Exception e) {
            // Don't fail the request if audit capture fails
            log.error("Error capturing audit context", e);
        }
    }

    /**
     * Verifica se la richiesta è per una risorsa AuditEvent.
     * Questo previene la ricorsione infinita quando persisteremo l'AuditEvent.
     * 
     * @param requestDetails Dettagli della richiesta
     * @return true se è una richiesta AuditEvent
     */
    private boolean isAuditEventRequest(RequestDetails requestDetails) {
        String resourceName = requestDetails.getResourceName();
        return "AuditEvent".equals(resourceName);
    }

    /**
     * Determina se la richiesta deve essere catturata per l'audit.
     * 
     * @param requestDetails Dettagli della richiesta
     * @return true se la richiesta deve essere auditata
     */
    private boolean shouldCaptureRequest(RequestDetails requestDetails) {
        RequestTypeEnum requestType = requestDetails.getRequestType();
        
        if (requestType == null) {
            return false;
        }

        switch (requestType) {
            case POST:
                return auditProperties.isGenerateForCreate();
            case PUT:
                return auditProperties.isGenerateForUpdate();
            case DELETE:
                return auditProperties.isGenerateForDelete();
            case GET:
                return auditProperties.isGenerateForRead();
            default:
                return false;
        }
    }

    /**
     * Marca una richiesta come scrittura interna di audit per prevenire ricorsione.
     * 
     * @param requestDetails Dettagli della richiesta
     */
    private void markAsInternalAuditWrite(RequestDetails requestDetails) {
        UarAuditContext context = new UarAuditContext();
        context.setInternalAuditWrite(true);
        requestDetails.getUserData().put(AUDIT_CONTEXT_KEY, context);
    }

    /**
     * Recupera il contesto di audit dalla richiesta.
     * 
     * @param requestDetails Dettagli della richiesta
     * @return Contesto di audit o null se non presente
     */
    public static UarAuditContext getAuditContext(RequestDetails requestDetails) {
        if (requestDetails == null || requestDetails.getUserData() == null) {
            return null;
        }
        return (UarAuditContext) requestDetails.getUserData().get(AUDIT_CONTEXT_KEY);
    }

    /**
     * Verifica se la richiesta è una scrittura interna di audit.
     * 
     * @param requestDetails Dettagli della richiesta
     * @return true se è una scrittura interna di audit
     */
    public static boolean isInternalAuditWrite(RequestDetails requestDetails) {
        UarAuditContext context = getAuditContext(requestDetails);
        return context != null && context.isInternalAuditWrite();
    }
}
