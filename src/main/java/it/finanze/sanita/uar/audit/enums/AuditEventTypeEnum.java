/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.enums;

/**
 * Enum per i type AuditEvent secondo le specifiche FSE 2.0
 */
public enum AuditEventTypeEnum {
    
    /**
     * Type per consultazione dati (READ)
     * Code: 110106 - Export
     */
    DATA_CONSULTATION(
        "110106",
        "http://dicom.nema.org/resources/ontology/DCM",
        "Export"
    ),
    
    /**
     * Type per creazione dati (CREATE)
     * Code: 110121 - Create Object
     */
    DATA_CREATION(
        "110121",
        "http://dicom.nema.org/resources/ontology/DCM",
        "Create Object"
    ),
    
    /**
     * Type per oscuramento/modifica dati (UPDATE)
     * Code: 110107 - Import
     */
    DATA_OBFUSCATION(
        "110107",
        "http://dicom.nema.org/resources/ontology/DCM",
        "Import"
    );
    
    private final String code;
    private final String system;
    private final String display;

    AuditEventTypeEnum(String code, String system, String display) {
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
