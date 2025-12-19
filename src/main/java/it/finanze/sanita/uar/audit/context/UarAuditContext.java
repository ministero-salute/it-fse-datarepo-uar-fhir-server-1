/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.context;

import ca.uhn.fhir.rest.api.RequestTypeEnum;

import java.util.Map;

/**
 * Request-scoped context holder for audit information.
 * 
 * Stores information captured during request pre-processing that will be used
 * to build the AuditEvent after the operation completes successfully.
 */
public class UarAuditContext {
    
    /**
     * HTTP request method (POST, PUT, DELETE, GET)
     */
    private RequestTypeEnum requestMethod;
    
    /**
     * Complete request URL including query parameters
     */
    private String requestUrl;
    
    /**
     * Query parameters from the request
     */
    private Map<String, String[]> queryParams;
    
    /**
     * Role of the requestor (e.g., APR, ASS, TUT) extracted from header
     */
    private String role;
    
    /**
     * Practitioner/PractitionerRole ID (will be extracted from Bundle)
     */
    private String practitionerId;
    
    /**
     * Patient ID (will be extracted from Bundle)
     */
    private String patientId;
    
    /**
     * Flag to indicate this is an internal audit write (to prevent recursion)
     */
    private boolean internalAuditWrite = false;
    
    /**
     * Timestamp when the request was received
     */
    private long requestTimestamp = System.currentTimeMillis();

    // Getters and Setters
    
    public RequestTypeEnum getRequestMethod() {
        return requestMethod;
    }

    public void setRequestMethod(RequestTypeEnum requestMethod) {
        this.requestMethod = requestMethod;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public void setRequestUrl(String requestUrl) {
        this.requestUrl = requestUrl;
    }

    public Map<String, String[]> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(Map<String, String[]> queryParams) {
        this.queryParams = queryParams;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getPractitionerId() {
        return practitionerId;
    }

    public void setPractitionerId(String practitionerId) {
        this.practitionerId = practitionerId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public boolean isInternalAuditWrite() {
        return internalAuditWrite;
    }

    public void setInternalAuditWrite(boolean internalAuditWrite) {
        this.internalAuditWrite = internalAuditWrite;
    }

    public long getRequestTimestamp() {
        return requestTimestamp;
    }

    public void setRequestTimestamp(long requestTimestamp) {
        this.requestTimestamp = requestTimestamp;
    }

    @Override
    public String toString() {
        return "UarAuditContext{" +
                "requestMethod=" + requestMethod +
                ", requestUrl='" + requestUrl + '\'' +
                ", role='" + role + '\'' +
                ", practitionerId='" + practitionerId + '\'' +
                ", patientId='" + patientId + '\'' +
                ", internalAuditWrite=" + internalAuditWrite +
                '}';
    }
}
