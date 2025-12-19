/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for UAR AuditEvent generation.
 * 
 * Configures when and how AuditEvents are generated for FHIR operations.
 */
@Configuration
@ConfigurationProperties(prefix = "uar.audit")
public class AuditProperties {

    /**
     * Master switch to enable/disable audit generation
     */
    private boolean enabled = true;

    /**
     * Generate AuditEvent for CREATE operations
     */
    private boolean generateForCreate = true;

    /**
     * Generate AuditEvent for UPDATE operations
     */
    private boolean generateForUpdate = true;

    /**
     * Generate AuditEvent for DELETE operations
     */
    private boolean generateForDelete = true;

    /**
     * Generate AuditEvent for READ/SEARCH operations (currently not implemented)
     */
    private boolean generateForRead = false;

    /**
     * Use reference strategy (entity.what.reference) instead of identifier strategy.
     * WARNING: Setting to true may block hard deletes due to referential integrity.
     * Recommended: false (use identifier strategy)
     */
    private boolean useWhatReference = false;

    /**
     * Include query string in entity.query for search operations (Base64 encoded)
     */
    private boolean includeQueryForSearch = true;

    /**
     * Maximum number of entities per AuditEvent (guard against huge transactions)
     */
    private int maxEntitiesPerAudit = 2000;

    /**
     * HTTP header configuration
     */
    private Headers headers = new Headers();

    /**
     * Service consulted (entity.type) configuration
     */
    private Service service = new Service();

    /**
     * Observer organization configuration
     */
    private Organization organization = new Organization();

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isGenerateForCreate() {
        return generateForCreate;
    }

    public void setGenerateForCreate(boolean generateForCreate) {
        this.generateForCreate = generateForCreate;
    }

    public boolean isGenerateForUpdate() {
        return generateForUpdate;
    }

    public void setGenerateForUpdate(boolean generateForUpdate) {
        this.generateForUpdate = generateForUpdate;
    }

    public boolean isGenerateForDelete() {
        return generateForDelete;
    }

    public void setGenerateForDelete(boolean generateForDelete) {
        this.generateForDelete = generateForDelete;
    }

    public boolean isGenerateForRead() {
        return generateForRead;
    }

    public void setGenerateForRead(boolean generateForRead) {
        this.generateForRead = generateForRead;
    }

    public boolean isUseWhatReference() {
        return useWhatReference;
    }

    public void setUseWhatReference(boolean useWhatReference) {
        this.useWhatReference = useWhatReference;
    }

    public boolean isIncludeQueryForSearch() {
        return includeQueryForSearch;
    }

    public void setIncludeQueryForSearch(boolean includeQueryForSearch) {
        this.includeQueryForSearch = includeQueryForSearch;
    }

    public int getMaxEntitiesPerAudit() {
        return maxEntitiesPerAudit;
    }

    public void setMaxEntitiesPerAudit(int maxEntitiesPerAudit) {
        this.maxEntitiesPerAudit = maxEntitiesPerAudit;
    }

    public Headers getHeaders() {
        return headers;
    }

    public void setHeaders(Headers headers) {
        this.headers = headers;
    }

    public Service getService() {
        return service;
    }

    public void setService(Service service) {
        this.service = service;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public static class Headers {
        /**
         * Name of the HTTP header containing the subject role (e.g., APR, ASS, TUT)
         */
        private String roleHeaderName = "X-Subject-Role";

        /**
         * Name of the HTTP header containing the subject identifier (optional, for fallback)
         */
        private String subjectIdHeaderName = "X-Subject-Id";

        public String getRoleHeaderName() {
            return roleHeaderName;
        }

        public void setRoleHeaderName(String roleHeaderName) {
            this.roleHeaderName = roleHeaderName;
        }

        public String getSubjectIdHeaderName() {
            return subjectIdHeaderName;
        }

        public void setSubjectIdHeaderName(String subjectIdHeaderName) {
            this.subjectIdHeaderName = subjectIdHeaderName;
        }
    }

    public static class Service {
        /**
         * CodeSystem URL for service consulted codes
         */
        private String codeSystem = "http://fse.salute.gov.it/CodeSystem/servizio-consultato";

        /**
         * Default service code when not determinable from context
         */
        private String defaultCode = "SERV_ASS_01";

        /**
         * Default display text for service
         */
        private String defaultDisplay = "Dati di sintesi";

        public String getCodeSystem() {
            return codeSystem;
        }

        public void setCodeSystem(String codeSystem) {
            this.codeSystem = codeSystem;
        }

        public String getDefaultCode() {
            return defaultCode;
        }

        public void setDefaultCode(String defaultCode) {
            this.defaultCode = defaultCode;
        }

        public String getDefaultDisplay() {
            return defaultDisplay;
        }

        public void setDefaultDisplay(String defaultDisplay) {
            this.defaultDisplay = defaultDisplay;
        }
    }

    public static class Organization {
        /**
         * Reference to the Organization resource acting as observer (source.observer)
         */
        private String reference = "Organization/min-salute";

        /**
         * Display name for the organization
         */
        private String display = "Ministero della Salute";

        public String getReference() {
            return reference;
        }

        public void setReference(String reference) {
            this.reference = reference;
        }

        public String getDisplay() {
            return display;
        }

        public void setDisplay(String display) {
            this.display = display;
        }
    }
}
