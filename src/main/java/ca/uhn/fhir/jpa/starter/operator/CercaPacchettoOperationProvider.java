package ca.uhn.fhir.jpa.starter.operator;


import java.util.ArrayList;
import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationDefinition;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

/**
 * Provider per l'operazione $assistito-consultazione-cerca-pacchetto
 * Permette di cercare pacchetti di assistenza tramite Codice Fiscale, Nome e Regione
 */
@Component
public class CercaPacchettoOperationProvider {

    /**
     * Operation: $assistito-consultazione-cerca-pacchetto
     * 
     * Restituisce tutte le OperationDefinition create dal cittadino, 
     * identificato dal Codice Fiscale o da Nome_Regione
     * 
     * @param publisher Nome del publisher dell'OperationDefinition (opzionale)
     * @param codiceFiscale Codice Fiscale del cittadino (opzionale)
     * @param nome Nome del cittadino (opzionale)
     * @param regione Regione (opzionale, usata insieme a Nome)
     * @param requestDetails Dettagli della richiesta
     * @return Bundle contenente le OperationDefinition trovate
     */
    @Operation(name = "$assistito-consultazione-cerca-pacchetto",
        idempotent = true,
        returnParameters = {
            @OperationParam(name = "return", type = Bundle.class)
        }
    )
    public Bundle cercaPacchetto(
            @OperationParam(name = "publisher") StringType publisher,
            @OperationParam(name = "codiceFiscale") StringType codiceFiscale,
            @OperationParam(name = "nome") StringType nome,
            @OperationParam(name = "regione") StringType regione,
            RequestDetails requestDetails) {
        
        // Validazione parametri
        validateParameters(publisher, codiceFiscale, nome, regione);
        
        // Creazione del bundle di risposta
        Bundle responseBundle = new Bundle();
        responseBundle.setType(Bundle.BundleType.SEARCHSET);
        responseBundle.setTimestamp(new java.util.Date());
        
        // Logica di ricerca delle OperationDefinition
        List<OperationDefinition> operationDefinitions = searchOperationDefinitions(
            publisher != null ? publisher.getValue() : null,
            codiceFiscale != null ? codiceFiscale.getValue() : null,
            nome != null ? nome.getValue() : null,
            regione != null ? regione.getValue() : null
        );
        
        // Aggiunta delle OperationDefinition al bundle
        for (OperationDefinition opDef : operationDefinitions) {
            Bundle.BundleEntryComponent entry = responseBundle.addEntry();
            entry.setResource(opDef);
            entry.setFullUrl("OperationDefinition/" + opDef.getIdElement().getIdPart());
        }
        
        responseBundle.setTotal(operationDefinitions.size());
        
        return responseBundle;
    }
    
    /**
     * Valida i parametri di input
     */
    private void validateParameters(StringType publisher, StringType codiceFiscale, 
                                   StringType nome, StringType regione) {
        // Deve essere specificato almeno un criterio di ricerca
        if (isEmpty(publisher) && isEmpty(codiceFiscale) && isEmpty(nome)) {
            throw new InvalidRequestException(
                "Deve essere specificato almeno uno dei seguenti parametri: publisher, codiceFiscale, nome"
            );
        }
        
        // Se è specificato il nome, deve essere specificata anche la regione
        if (!isEmpty(nome) && isEmpty(regione)) {
            throw new InvalidRequestException(
                "Se viene specificato il parametro 'nome', è obbligatorio specificare anche 'regione'"
            );
        }
        
        // Se è specificata la regione, deve essere specificato anche il nome
        if (!isEmpty(regione) && isEmpty(nome)) {
            throw new InvalidRequestException(
                "Se viene specificato il parametro 'regione', è obbligatorio specificare anche 'nome'"
            );
        }
    }
    
    /**
     * Verifica se un parametro StringType è vuoto o null
     */
    private boolean isEmpty(StringType stringType) {
        return stringType == null || stringType.isEmpty() || stringType.getValue() == null;
    }
    
    /**
     * Esegue la ricerca delle OperationDefinition nel repository
     * NOTA: Implementare la logica effettiva di ricerca nel database/repository
     */
    private List<OperationDefinition> searchOperationDefinitions(
            String publisher, String codiceFiscale, String nome, String regione) {
        
        List<OperationDefinition> results = new ArrayList<>();
        
        // TODO: Implementare la logica di ricerca effettiva
        // Esempio di ricerca:
        // - Se è presente codiceFiscale, cercare per Codice Fiscale
        // - Se sono presenti nome e regione, cercare per Nome_Regione
        // - Se è presente publisher, filtrare per publisher
        
        // Esempio di costruzione URL base per la ricerca:
        // GET [BASE_URL]/assistito-consultazione-cerca-pacchetto/V1/OperationDefinition?
        //     publisher=(Codice_Fiscale|Nome_Regione)
        
        /* IMPLEMENTAZIONE ESEMPIO:
        StringBuilder queryBuilder = new StringBuilder();
        
        if (codiceFiscale != null) {
            queryBuilder.append("codiceFiscale=").append(codiceFiscale);
        } else if (nome != null && regione != null) {
            queryBuilder.append("nome=").append(nome)
                       .append("&regione=").append(regione);
        }
        
        if (publisher != null) {
            if (queryBuilder.length() > 0) {
                queryBuilder.append("&");
            }
            queryBuilder.append("publisher=").append(publisher);
        }
        
        // Eseguire la query sul repository
        results = operationDefinitionRepository.search(queryBuilder.toString());
        */
        
        return results;
    }
     
}
