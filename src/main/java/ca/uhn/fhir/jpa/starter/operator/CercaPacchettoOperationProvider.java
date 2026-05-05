//package ca.uhn.fhir.jpa.starter.operator;
//
//import java.util.ArrayList;
//import java.util.List;
//
//import org.hl7.fhir.instance.model.api.IBaseResource;
//import org.hl7.fhir.r4.model.Bundle;
//import org.hl7.fhir.r4.model.OperationDefinition;
//import org.hl7.fhir.r4.model.StringType;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
//import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
//import ca.uhn.fhir.rest.annotation.Operation;
//import ca.uhn.fhir.rest.annotation.OperationParam;
//import ca.uhn.fhir.rest.api.server.IBundleProvider;
//import ca.uhn.fhir.rest.api.server.RequestDetails;
//import ca.uhn.fhir.rest.param.StringAndListParam;
//import ca.uhn.fhir.rest.param.StringOrListParam;
//import ca.uhn.fhir.rest.param.StringParam;
//import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;
//
///**
// * Provider per l'operazione $assistito-consultazione-cerca-pacchetto
// * Permette di cercare pacchetti di assistenza tramite Codice Fiscale, Nome e Regione
// */
//@Component
//public class CercaPacchettoOperationProvider {
//
//    @Autowired
//    private IFhirResourceDao<OperationDefinition> myOperationDefinitionDao;
//
//
//    /**
//     * Operation: $assistito-consultazione-cerca-pacchetto
//     *
//     * Restituisce tutte le OperationDefinition create dal cittadino,
//     * identificato dal Codice Fiscale o da Nome_Regione.
//     *
//     * Logica publisher:
//     *   - Se presente codiceFiscale  → publisher = codiceFiscale
//     *   - Se presenti nome+regione   → publisher = nome + "_" + regione
//     *   - Se publisher è passato direttamente, viene usato così com'è
//     *   - I criteri si combinano in OR quando più di uno è presente
//     */
//    @Operation(
//        name = "$assistito-consultazione-cerca-pacchetto",
//        idempotent = true,
//        returnParameters = {
//            @OperationParam(name = "return", type = Bundle.class)
//        }
//    )
//    public Bundle cercaPacchetto(
//            @OperationParam(name = "publisher")     StringType publisher,
//            @OperationParam(name = "codiceFiscale") StringType codiceFiscale,
//            @OperationParam(name = "nome")          StringType nome,
//            @OperationParam(name = "regione")       StringType regione,
//            RequestDetails requestDetails) {
//
//        // ── 1. Validazione ──────────────────────────────────────────────────
//        validateParameters(publisher, codiceFiscale, nome, regione);
//
//        // ── 2. Costruzione valori publisher da usare come criteri di ricerca ─
//        List<String> publisherValues = buildPublisherValues(publisher, codiceFiscale, nome, regione);
//
//        // ── 3. Ricerca nel DAO ───────────────────────────────────────────────
//        List<OperationDefinition> operationDefinitions = searchOperationDefinitions(
//                publisherValues, requestDetails);
//
//        // ── 4. Costruzione Bundle di risposta ────────────────────────────────
//        Bundle responseBundle = new Bundle();
//        responseBundle.setType(Bundle.BundleType.SEARCHSET);
//        responseBundle.setTimestamp(new java.util.Date());
//
//        for (OperationDefinition opDef : operationDefinitions) {
//            Bundle.BundleEntryComponent entry = responseBundle.addEntry();
//            entry.setResource(opDef);
//            entry.setFullUrl("OperationDefinition/" + opDef.getIdElement().getIdPart());
//            entry.getSearch()
//                 .setMode(Bundle.SearchEntryMode.MATCH);
//        }
//
//        responseBundle.setTotal(operationDefinitions.size());
//        return responseBundle;
//    }
// 
//
//    private void validateParameters(StringType publisher, StringType codiceFiscale,
//                                    StringType nome, StringType regione) {
//
//        if (isEmpty(publisher) && isEmpty(codiceFiscale) && isEmpty(nome)) {
//            throw new InvalidRequestException(
//                "Deve essere specificato almeno uno dei seguenti parametri: publisher, codiceFiscale, nome"
//            );
//        }
//
//        if (!isEmpty(nome) && isEmpty(regione)) {
//            throw new InvalidRequestException(
//                "Se viene specificato il parametro 'nome', è obbligatorio specificare anche 'regione'"
//            );
//        }
//
//        if (!isEmpty(regione) && isEmpty(nome)) {
//            throw new InvalidRequestException(
//                "Se viene specificato il parametro 'regione', è obbligatorio specificare anche 'nome'"
//            );
//        }
//    }
//
//    
//    /**
//     * Converte i parametri in ingresso in una lista di valori da cercare
//     * sul campo "publisher" delle OperationDefinition.
//     *
//     * Convenzione dati:
//     *   codiceFiscale  → publisher esatto
//     *   nome + regione → publisher = "<nome>_<regione>"
//     *   publisher      → publisher esatto (passthrough)
//     *
//     * Più valori = OR tra di loro nella query FHIR.
//     */
//    private List<String> buildPublisherValues(StringType publisher,
//                                               StringType codiceFiscale,
//                                               StringType nome,
//                                               StringType regione) {
//        List<String> values = new ArrayList<>();
//
//        if (!isEmpty(codiceFiscale)) {
//            values.add(codiceFiscale.getValue().trim().toUpperCase());
//        }
//
//        if (!isEmpty(nome) && !isEmpty(regione)) {
//            String nomeRegione = nome.getValue().trim() + "_" + regione.getValue().trim();
//            values.add(nomeRegione);
//        }
//
//        if (!isEmpty(publisher)) {
//            String pub = publisher.getValue().trim();
//            if (!values.contains(pub)) {          // evita duplicati
//                values.add(pub);
//            }
//        }
//
//        return values;
//    }
// 
//
//    /**
//     * Interroga il repository HAPI FHIR cercando OperationDefinition
//     * il cui campo "publisher" corrisponda a uno dei valori forniti (OR).
//     *
//     * HAPI FHIR supporta OR su un singolo parametro tramite StringOrListParam:
//     *   ?publisher=VAL1,VAL2  →  publisher = VAL1 OR VAL2
//     */
//    private List<OperationDefinition> searchOperationDefinitions(
//            List<String> publisherValues,
//            RequestDetails requestDetails) {
//
//        SearchParameterMap params = new SearchParameterMap();
//
//        if (!publisherValues.isEmpty()) {
//            // Costruiamo un OR tra tutti i valori publisher
//            StringOrListParam orList = new StringOrListParam();
//            for (String val : publisherValues) {
//                // StringParam con exact=false → ricerca "contains" (prefix-match FHIR)
//                // Passare exact=true se si vuole corrispondenza esatta
//                orList.addOr(new StringParam(val, /* exact */ true));
//            }
//            StringAndListParam andParam = new StringAndListParam();
//            andParam.addAnd(orList);
//            params.add(OperationDefinition.SP_PUBLISHER, andParam);
//        }
//
//        // Esegue la ricerca nel database tramite il DAO iniettato
//        IBundleProvider result = myOperationDefinitionDao.search(params, requestDetails);
//
//        // Raccoglie tutte le risorse dalla pagina corrente
//        // (per dataset grandi considera paginazione)
//        List<IBaseResource> resources = result.getAllResources();
//
//        List<OperationDefinition> operationDefinitions = new ArrayList<>();
//        for (IBaseResource resource : resources) {
//            if (resource instanceof OperationDefinition) {
//                operationDefinitions.add((OperationDefinition) resource);
//            }
//        }
//
//        return operationDefinitions;
//    }
//
//     
//    private boolean isEmpty(StringType stringType) {
//        return stringType == null || stringType.isEmpty() || stringType.getValue() == null || stringType.getValue().isBlank();
//    }
//}