package ca.uhn.fhir.jpa.starter.operator;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;

@Component
public class PacchettoProvider {

    @Autowired
    private EngineComponent engineComponent;

    @Operation(name = "$esegui-pacchetto", idempotent = true, returnParameters = {
            @OperationParam(name = "return", type = Bundle.class) })
    public Bundle searchPacchetto(
            @OperationParam(name = "code") StringType code,
            @OperationParam(name = "publisher") StringType publisher,
            @OperationParam(name = "codiceFiscale") StringType codiceFiscale,
            @OperationParam(name = "patientSystem") StringType patientSystem,
            @OperationParam(name = "dateFrom", min = 0) DateType dateFrom,
            @OperationParam(name = "dateTo", min = 0) DateType dateTo,
            RequestDetails requestDetails) {

        if (code == null || publisher == null) {
            throw new RuntimeException(
                    "Il code e il publisher non possono essere nulli per l'esecuzione del pacchetto.");
        }

        String publisherStr = publisher != null ? publisher.getValue() : null;
        String cfStr = codiceFiscale != null ? codiceFiscale.getValue() : null;
        String codeStr = code != null ? code.getValue() : null;

        return engineComponent.getBundle(
                codeStr,
                publisherStr,
                cfStr,
                null,
                dateFrom,
                dateTo,
                requestDetails);
    }

}