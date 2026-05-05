package ca.uhn.fhir.jpa.starter.operator;

import org.hl7.fhir.r4.model.Bundle;
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

    @Operation(
        name = "$esegui-pacchetto",
        idempotent = true,
        returnParameters = {@OperationParam(name = "return", type = Bundle.class)}
    )
    public Bundle searchPacchetto(
            @OperationParam(name = "nomePacchetto")     StringType nomePacchetto,
            @OperationParam(name = "publisher")         StringType publisher,
            @OperationParam(name = "codiceFiscale")     StringType codiceFiscale,
            @OperationParam(name = "patientSystem")     StringType patientSystem,
            RequestDetails requestDetails) {

        String publisherStr    = publisher     != null ? publisher.getValue()     : null;
        String cfStr           = codiceFiscale != null ? codiceFiscale.getValue() : null;
        String patientSystemStr= patientSystem != null ? patientSystem.getValue() : "urn:oid:2.16.840.1.113883.2.9.4.3.17";
        String nomePacchettoStr= nomePacchetto != null ? nomePacchetto.getValue() : null;

        //TODO: aggiungere controllo se uno dei campi è nullo cosa fare

        return engineComponent.getBundle(nomePacchettoStr, publisherStr, cfStr, patientSystemStr);
    }

}
