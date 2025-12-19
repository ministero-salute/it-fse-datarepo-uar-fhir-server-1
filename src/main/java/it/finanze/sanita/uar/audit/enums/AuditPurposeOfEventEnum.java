/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.enums;

/**
 * Enum per il purposeOfEvent dell'AuditEvent secondo le specifiche FSE 2.0
 */
public enum AuditPurposeOfEventEnum {
    
    /**
     * Break The Glass - Accesso in emergenza
     */
    EMERGENCY_ACCESS(
        "BTG", 
        "http://terminology.hl7.org/CodeSystem/v3-ActReason",
        "Break The Glass"
    ),
    
    /**
     * Oscuramento dei dati e dei documenti
     */
    OBSCURE(
        "OSC",
        "https://fascicolosanitario.gov.it/codes/v3-PurposeOfUse",
        "Oscuramento dei dati e dei documenti"
    ),
    
    /**
     * De-oscuramento dei dati e dei documenti
     */
    DE_OBSCURE(
        "DE_OSC",
        "https://fascicolosanitario.gov.it/codes/v3-PurposeOfUse",
        "De-oscuramento dei dati e dei documenti"
    );
    
    private final String code;
    private final String system;
    private final String display;

    AuditPurposeOfEventEnum(String code, String system, String display) {
        this.code = code;
        this.system = system;
        this.display = display;
    }

    public String getCode() {
        return code;
    }

    public String getSystem() {
        return system;
    }

    public String getDisplay() {
        return display;
    }
}
