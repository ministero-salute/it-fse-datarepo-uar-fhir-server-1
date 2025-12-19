/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.enums;

import org.hl7.fhir.r4.model.AuditEvent;

/**
 * Enum per le azioni AuditEvent secondo le specifiche FSE 2.0
 */
public enum AuditEventActionEnum {

    /**
     * Read/View/Query action
     */
    READ("R", "Read", AuditEvent.AuditEventAction.R),

    /**
     * Create action
     */
    CREATE("C", "Create", AuditEvent.AuditEventAction.C),

    /**
     * Update action
     */
    UPDATE("U", "Update", AuditEvent.AuditEventAction.U),

    /**
     * Delete action
     */
    DELETE("D", "Delete", AuditEvent.AuditEventAction.D);

    private final String code;
    private final String display;
    private final AuditEvent.AuditEventAction fhirAction;

    AuditEventActionEnum(String code, String display, AuditEvent.AuditEventAction fhirAction) {
        this.code = code;
        this.display = display;
        this.fhirAction = fhirAction;
    }

    public String getCode() {
        return code;
    }

    public String getDisplay() {
        return display;
    }

    public AuditEvent.AuditEventAction getFhirAction() {
        return fhirAction;
    }
}
