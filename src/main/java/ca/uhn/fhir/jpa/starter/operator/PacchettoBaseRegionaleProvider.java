package ca.uhn.fhir.jpa.starter.operator;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;

/**
 * Provider per l'operazione $assistito-consultazione-cerca-pacchetto
 * Permette di cercare pacchetti di assistenza tramite Codice Fiscale, Nome e Regione
 */
@Component
public class PacchettoBaseRegionaleProvider {

    @Autowired
    private EngineComponent engineComponent;

     
    @Operation(name = "$cerca-pacchetto-base", idempotent = true, returnParameters = {@OperationParam(name = "return", type = Bundle.class)})
    public Bundle cercaPacchetto(@OperationParam(name = "publisher") StringType publisher, RequestDetails requestDetails) {
    	String system = "";
    	return engineComponent.getBundle("BASE", system,"");
    }
 
}