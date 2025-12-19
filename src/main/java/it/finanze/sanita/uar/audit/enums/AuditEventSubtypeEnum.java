/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.enums;

/**
 * Enum per i subtype AuditEvent secondo le specifiche FSE 2.0
 */
public enum AuditEventSubtypeEnum {
    
    /**
     * Subtype per oscuramento dati
     * Code: 110129 - Security Alert
     */
    OBFUSCATION(
        "110129",
        "http://dicom.nema.org/resources/ontology/DCM",
        "Security Alert"
    );
    
    private final String code;
    private final String system;
    private final String display;
    
    AuditEventSubtypeEnum(String code, String system, String display) {
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
