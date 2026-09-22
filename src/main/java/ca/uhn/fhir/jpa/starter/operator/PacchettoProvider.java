package ca.uhn.fhir.jpa.starter.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.annotation.ResourceParam;
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
            @OperationParam(name = "operationDefinition", min = 0) StringType op,
            @OperationParam(name = "resource-type", min = 0, max = OperationParam.MAX_UNLIMITED) List<StringType> resourceTypes,
            @ResourceParam Parameters rawParameters,
            RequestDetails requestDetails) {

        // Validazione parametri
        if (code == null || publisher == null) {
            throw new RuntimeException(
                    "Il code e il publisher non possono essere nulli per l'esecuzione del pacchetto.");
        }

        Map<String, List<String>> mappaRisorseCodici = new HashMap<>();

        if (rawParameters != null && rawParameters.hasParameter()) {
            for (ParametersParameterComponent param : rawParameters.getParameter()) {

                if ("resourceMap".equals(param.getName()) && param.hasPart()) {

                    String resourceTypeAttuale = null;
                    List<String> codiciAttuali = new ArrayList<>();

                    for (ParametersParameterComponent part : param.getPart()) {
                        if ("resourceType".equals(part.getName()) && part.hasValue()) {
                            resourceTypeAttuale = part.getValue().primitiveValue();
                        } else if ("code".equals(part.getName()) && part.hasValue()) {
                            codiciAttuali.add(part.getValue().primitiveValue());
                        }
                    }

                    if (resourceTypeAttuale != null && !codiciAttuali.isEmpty()) {
                        // computeIfAbsent gestisce il caso in cui il client invii più blocchi
                        // "resourceMap" per la stessa risorsa: i codici vengono sommati.
                        mappaRisorseCodici
                                .computeIfAbsent(resourceTypeAttuale, k -> new ArrayList<>())
                                .addAll(codiciAttuali);
                    }
                }
            }
        }

        String publisherStr = publisher != null ? publisher.getValue() : null;
        String cfStr = codiceFiscale != null ? codiceFiscale.getValue() : null;
        String codeStr = code != null ? code.getValue() : null;

        // Converte List<StringType> → List<String> (null-safe)
        List<String> resourceTypesStr = (resourceTypes != null)
                ? resourceTypes.stream()
                        .filter(st -> st != null && st.getValue() != null)
                        .map(StringType::getValue)
                        .toList()
                : null;

        return engineComponent.getBundle(
                codeStr,
                publisherStr,
                cfStr,
                null,
                dateFrom,
                dateTo,
                op,
                resourceTypesStr,
                mappaRisorseCodici,
                requestDetails);
    }

}