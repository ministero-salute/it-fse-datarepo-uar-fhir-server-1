/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * 
 * Copyright (C) 2023 Ministero della Salute
 */
package it.finanze.sanita.uar.audit.enums;

/**
 * Enum per i servizi consultati secondo le specifiche FSE 2.0
 * Distingue tra servizi Assistito (SERV_ASS) e Professionista (SERV_PRO)
 */
public enum AuditEntityTypeEnum {

    // ========== SERVIZI ASSISTITO (SERV_ASS) ==========

    /**
     * SERV_ASS_01 - Consultazione dati di sintesi
     */
    SERV_ASS_01(
            "SERV_ASS_01",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione dati di sintesi"),

    /**
     * SERV_ASS_02 - Consultazione dati clinici
     */
    SERV_ASS_02(
            "SERV_ASS_02",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione dati clinici"),

    /**
     * SERV_ASS_03 - Consultazione dossier farmaceutico
     */
    SERV_ASS_03(
            "SERV_ASS_03",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione di dati relativi al dossier farmaceutico"),

    /**
     * SERV_ASS_04 - Consultazione andamento dati clinici
     */
    SERV_ASS_04(
            "SERV_ASS_04",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione dell'andamento dei dati clinici"),

    /**
     * SERV_ASS_05 - Consultazione percorso di cura
     */
    SERV_ASS_05(
            "SERV_ASS_05",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione delle informazioni relative al percorso di cura"),

    /**
     * SERV_ASS_06 - Consultazione documento FSE
     */
    SERV_ASS_06(
            "SERV_ASS_06",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione documento FSE"),

    // ========== SERVIZI PROFESSIONISTA (SERV_PRO) ==========

    /**
     * SERV_PRO_01 - Consultazione dati di sintesi
     */
    SERV_PRO_01(
            "SERV_PRO_01",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione dati di sintesi"),

    /**
     * SERV_PRO_02 - Consultazione ricoveri e pronto soccorso
     */
    SERV_PRO_02(
            "SERV_PRO_02",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Ricerca e consultazione dei dati provenienti da eventi di ricovero e dagli accessi di pronto soccorso"),

    /**
     * SERV_PRO_03 - Consultazione dossier farmaceutico
     */
    SERV_PRO_03(
            "SERV_PRO_03",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione di dati relativi al dossier farmaceutico"),

    /**
     * SERV_PRO_04 - Consultazione andamento dati clinici
     */
    SERV_PRO_04(
            "SERV_PRO_04",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione dell'andamento dei dati clinici"),

    /**
     * SERV_PRO_05 - Consultazione vaccinazioni
     */
    SERV_PRO_05(
            "SERV_PRO_05",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione di dati relativi alle vaccinazioni"),

    /**
     * SERV_PRO_06 - Consultazione prestazioni
     */
    SERV_PRO_06(
            "SERV_PRO_06",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Consultazione di dati relativi alle prestazioni"),

    // ========== SERVIZI DI PUBBLICAZIONE/MODIFICA (WRITE OPERATIONS) ==========

    /**
     * SERV_WRITE_01 - Creazione/Pubblicazione documento FSE
     * Utilizzato per operazioni CREATE
     */
    SERV_WRITE_01(
            "SERV_WRITE_01",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Creazione e pubblicazione di un nuovo documento"),

    /**
     * SERV_WRITE_02 - Sostituzione documento FSE
     * Utilizzato per operazioni REPLACE
     */
    SERV_WRITE_02(
            "SERV_WRITE_02",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Sostituzione di un documento esistente"),

    /**
     * SERV_WRITE_03 - Aggiornamento metadati documento FSE
     * Utilizzato per operazioni UPDATE METADATA
     */
    SERV_WRITE_03(
            "SERV_WRITE_03",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Aggiornamento dei metadati di un documento"),

    /**
     * SERV_WRITE_04 - Cancellazione documento FSE
     * Utilizzato per operazioni DELETE
     */
    SERV_WRITE_04(
            "SERV_WRITE_04",
            "https://fascicolosanitario.gov.it/codesystem/auditevent-entity-type",
            "Cancellazione di un documento ");

    private final String code;
    private final String system;
    private final String display;

    AuditEntityTypeEnum(String code, String system, String display) {
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

    /**
     * Recupera l'enum corrispondente al codice fornito
     * 
     * @param code il codice del servizio (es. "SERV_ASS_01", "SERV_PRO_01", ecc.)
     * @return l'enum corrispondente o null se non trovato
     */
    public static AuditEntityTypeEnum get(String code) {
        AuditEntityTypeEnum out = null;
        for (AuditEntityTypeEnum v : AuditEntityTypeEnum.values()) {
            if (v.getCode().equalsIgnoreCase(code)) {
                out = v;
                break;
            }
        }
        return out;
    }

}
