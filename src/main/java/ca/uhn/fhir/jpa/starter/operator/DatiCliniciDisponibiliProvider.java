package ca.uhn.fhir.jpa.starter.operator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

@Component
public class DatiCliniciDisponibiliProvider {

    // ── Lista statica dei tipi di risorsa supportati ─────────────────────────
    private static final List<String> SUPPORTED_RESOURCE_TYPES = List.of(
            "Observation",
            "Condition",
            "AllergyIntolerance",
            "MedicationStatement",
            "MedicationDispense",
            "Immunization",
            "Procedure",
            "DiagnosticReport");

    @Autowired
    private IFhirResourceDao<Observation> observationDao;

    @Autowired
    private IFhirResourceDao<Condition> conditionDao;

    @Autowired
    private IFhirResourceDao<AllergyIntolerance> allergyIntoleranceDao;

    @Autowired
    private IFhirResourceDao<MedicationStatement> medicationStatementDao;

    @Autowired
    private IFhirResourceDao<MedicationDispense> medicationDispenseDao;

    @Autowired
    private IFhirResourceDao<Immunization> immunizationDao;

    @Autowired
    private IFhirResourceDao<Procedure> procedureDao;

    @Autowired
    private IFhirResourceDao<DiagnosticReport> diagnosticReportDao;

    @Autowired
    private IFhirResourceDao<Patient> patientDao;

    @Operation(name = "$dati-clinici-disponibili", idempotent = true)
    public Parameters datiCliniciDisponibili(
            @OperationParam(name = "patientValue", min = 1) StringType patientValue,
            @OperationParam(name = "patientSystem", min = 1) StringType patientSystem,
            @OperationParam(name = "dateFrom", min = 0) DateType dateFrom,
            @OperationParam(name = "dateTo", min = 0) DateType dateTo,
            @OperationParam(name = "resourceType", min = 0) StringType resourceType,
            RequestDetails requestDetails) {

        // ── Validazione resourceType se fornito ──────────────────────────────
        if (resourceType != null && !resourceType.isEmpty()) {
            String rt = resourceType.getValue();
            if (!SUPPORTED_RESOURCE_TYPES.contains(rt)) {
                throw new InvalidRequestException(
                        "resourceType non supportato: '" + rt + "'. Valori ammessi: "
                                + String.join(", ", SUPPORTED_RESOURCE_TYPES));
            }
        }

        String patientSystemStr = patientSystem != null ? patientSystem.getValue()
                : "urn:oid:2.16.840.1.113883.2.9.4.3.17";
        String patientValueStr = patientValue.getValue();

        // ── 1. Risolvi identifier → Patient FHIR ID ──────────────────────────
        SearchParameterMap patientSearch = new SearchParameterMap();
        patientSearch.setLoadSynchronous(true);
        patientSearch.add(
                Patient.SP_IDENTIFIER,
                new TokenParam(
                        patientSystemStr, patientValueStr));

        IBundleProvider patientResults = patientDao.search(patientSearch, requestDetails);

        List<Patient> patients = patientResults.getResources(0, Integer.MAX_VALUE)
                .stream()
                .filter(r -> r instanceof Patient)
                .map(r -> (Patient) r)
                .toList();

        if (patients.isEmpty()) {
            throw new InvalidRequestException(
                    "Nessun Patient trovato con identifier: "
                            + patientSystemStr + "|" + patientValueStr);
        }

        IIdType patientId = patients.get(0).getIdElement().toUnqualifiedVersionless();

        // ── 2. Costruisci DateRangeParam (opzionale) ─────────────────────────
        DateRangeParam dateRange = null;
        if (dateFrom != null || dateTo != null) {
            dateRange = new DateRangeParam();
            if (dateFrom != null) {
                dateRange.setLowerBound(new DateParam("ge" + dateFrom.getValueAsString()));
            }
            if (dateTo != null) {
                dateRange.setUpperBound(new DateParam("le" + dateTo.getValueAsString()));
            }
        }

        // ── 3. Determina quali risorse cercare ───────────────────────────────
        List<String> resourcesToSearch = (resourceType != null && !resourceType.isEmpty())
                ? List.of(resourceType.getValue())
                : SUPPORTED_RESOURCE_TYPES;

        // ── 4. Raccogli i codici per risorsa:
        // Map< resourceType, Map< displayName, List<"system|code"> > >
        Map<String, Map<String, List<String>>> resultMap = new LinkedHashMap<>();

        for (String rt : resourcesToSearch) {
            Map<String, List<String>> codesForResource = searchResource(
                    rt, patientId, dateRange, requestDetails);
            if (!codesForResource.isEmpty()) {
                resultMap.put(rt, codesForResource);
            }
        }

        // ── 5. Costruisci la risposta Parameters strutturata ─────────────────
        //
        // Struttura:
        // Parameters
        // └─ parameter (name = "Observation")
        // └─ part (name = "Glicemia") ← displayName
        // ├─ part (name = "system") value = "http://loinc.org"
        // └─ part (name = "code") value = "2339-0"
        //
        Parameters parameters = new Parameters();

        for (Map.Entry<String, Map<String, List<String>>> resourceEntry : resultMap.entrySet()) {
            String resourceTypeName = resourceEntry.getKey();
            Map<String, List<String>> displayMap = resourceEntry.getValue();

            // Parametro di primo livello = nome della risorsa
            Parameters.ParametersParameterComponent resourceParam = parameters.addParameter().setName(resourceTypeName);

            for (Map.Entry<String, List<String>> displayEntry : displayMap.entrySet()) {
                String displayName = displayEntry.getKey();
                List<String> codes = displayEntry.getValue();

                // Parte di secondo livello = displayName
                Parameters.ParametersParameterComponent displayPart = resourceParam.addPart().setName(displayName);

                for (String systemAndCode : codes) {
                    // "system|code" → split
                    String[] parts = systemAndCode.split("\\|", 2);
                    String system = parts.length > 0 ? parts[0] : "";
                    String code = parts.length > 1 ? parts[1] : "";

                    // Parti di terzo livello = system e code
                    displayPart.addPart()
                            .setName("system")
                            .setValue(new StringType(system));
                    displayPart.addPart()
                            .setName("code")
                            .setValue(new StringType(code));
                }
            }
        }

        return parameters;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helper: esegue la ricerca per il tipo di risorsa indicato e restituisce
    // Map< displayName, List<"system|code"> >
    // ────────────────────────────────────────────────────────────────────────
    private Map<String, List<String>> searchResource(
            String resourceTypeName,
            IIdType patientId,
            DateRangeParam dateRange,
            RequestDetails requestDetails) {

        Map<String, List<String>> result = new LinkedHashMap<>();

        switch (resourceTypeName) {

            case "Observation" -> {
                SearchParameterMap map = buildBaseMap(
                        Observation.SP_PATIENT, patientId, Observation.SP_DATE, dateRange);
                observationDao.search(map, requestDetails)
                        .getResources(0, Integer.MAX_VALUE).stream()
                        .filter(r -> r instanceof Observation).map(r -> (Observation) r)
                        .forEach(obs -> {
                            if (obs.hasCode() && obs.getCode().hasCoding())
                                extractCoding(obs.getCode().getCodingFirstRep(), result);
                        });
            }

            case "Condition" -> {
                SearchParameterMap map = buildBaseMap(
                        Condition.SP_PATIENT, patientId, Condition.SP_ONSET_DATE, dateRange);
                conditionDao.search(map, requestDetails)
                        .getResources(0, Integer.MAX_VALUE).stream()
                        .filter(r -> r instanceof Condition).map(r -> (Condition) r)
                        .forEach(cond -> {
                            if (cond.hasCode() && cond.getCode().hasCoding())
                                extractCoding(cond.getCode().getCodingFirstRep(), result);
                        });
            }

            case "AllergyIntolerance" -> {
                SearchParameterMap map = buildBaseMap(
                        AllergyIntolerance.SP_PATIENT, patientId,
                        AllergyIntolerance.SP_DATE, dateRange);
                allergyIntoleranceDao.search(map, requestDetails)
                        .getResources(0, Integer.MAX_VALUE).stream()
                        .filter(r -> r instanceof AllergyIntolerance)
                        .map(r -> (AllergyIntolerance) r)
                        .forEach(allergy -> {
                            if (allergy.hasCode() && allergy.getCode().hasCoding())
                                extractCoding(allergy.getCode().getCodingFirstRep(), result);
                        });
            }

            case "MedicationStatement" -> {
                SearchParameterMap map = buildBaseMap(
                        MedicationStatement.SP_PATIENT, patientId,
                        MedicationStatement.SP_EFFECTIVE, dateRange);
                medicationStatementDao.search(map, requestDetails)
                        .getResources(0, Integer.MAX_VALUE).stream()
                        .filter(r -> r instanceof MedicationStatement)
                        .map(r -> (MedicationStatement) r)
                        .forEach(ms -> {
                            // Il farmaco può essere un CodeableConcept o una Reference
                            if (ms.hasMedicationCodeableConcept()
                                    && ms.getMedicationCodeableConcept().hasCoding())
                                extractCoding(
                                        ms.getMedicationCodeableConcept().getCodingFirstRep(),
                                        result);
                        });
            }

            case "MedicationDispense" -> {
                SearchParameterMap map = buildBaseMap(
                        MedicationDispense.SP_PATIENT, patientId,
                        MedicationDispense.SP_WHENHANDEDOVER, dateRange);
                medicationDispenseDao.search(map, requestDetails)
                        .getResources(0, Integer.MAX_VALUE).stream()
                        .filter(r -> r instanceof MedicationDispense)
                        .map(r -> (MedicationDispense) r)
                        .forEach(md -> {
                            if (md.hasMedicationCodeableConcept()
                                    && md.getMedicationCodeableConcept().hasCoding())
                                extractCoding(
                                        md.getMedicationCodeableConcept().getCodingFirstRep(),
                                        result);
                        });
            }

            case "Immunization" -> {
                SearchParameterMap map = buildBaseMap(
                        Immunization.SP_PATIENT, patientId,
                        Immunization.SP_DATE, dateRange);
                immunizationDao.search(map, requestDetails)
                        .getResources(0, Integer.MAX_VALUE).stream()
                        .filter(r -> r instanceof Immunization).map(r -> (Immunization) r)
                        .forEach(imm -> {
                            if (imm.hasVaccineCode() && imm.getVaccineCode().hasCoding())
                                extractCoding(imm.getVaccineCode().getCodingFirstRep(), result);
                        });
            }

            case "Procedure" -> {
                SearchParameterMap map = buildBaseMap(
                        Procedure.SP_PATIENT, patientId, Procedure.SP_DATE, dateRange);
                procedureDao.search(map, requestDetails)
                        .getResources(0, Integer.MAX_VALUE).stream()
                        .filter(r -> r instanceof Procedure).map(r -> (Procedure) r)
                        .forEach(proc -> {
                            if (proc.hasCode() && proc.getCode().hasCoding())
                                extractCoding(proc.getCode().getCodingFirstRep(), result);
                        });
            }

            case "DiagnosticReport" -> {
                SearchParameterMap map = buildBaseMap(
                        DiagnosticReport.SP_PATIENT, patientId,
                        DiagnosticReport.SP_DATE, dateRange);
                diagnosticReportDao.search(map, requestDetails)
                        .getResources(0, Integer.MAX_VALUE).stream()
                        .filter(r -> r instanceof DiagnosticReport)
                        .map(r -> (DiagnosticReport) r)
                        .forEach(dr -> {
                            if (dr.hasCode() && dr.getCode().hasCoding())
                                extractCoding(dr.getCode().getCodingFirstRep(), result);
                        });
            }

            default -> throw new InvalidRequestException(
                    "resourceType non gestito internamente: " + resourceTypeName);
        }

        return result;
    }

    // ── Costruisce la SearchParameterMap base con patient + dateRange opzionale
    private SearchParameterMap buildBaseMap(
            String patientSp, IIdType patientId,
            String dateSp, DateRangeParam dateRange) {

        SearchParameterMap map = new SearchParameterMap();
        map.setLoadSynchronous(true);
        map.add(patientSp, new ReferenceParam(patientId.getValue()));
        if (dateRange != null) {
            map.add(dateSp, dateRange);
        }
        return map;
    }

    // ── Estrae system|code da un Coding e li aggiunge alla mappa per displayName
    private void extractCoding(Coding coding, Map<String, List<String>> result) {
        if (coding == null)
            return;

        String displayName = coding.hasDisplay() ? coding.getDisplay() : coding.getCode();
        String systemAndCode = coding.getSystem() + "|" + coding.getCode();

        result.computeIfAbsent(displayName, k -> new ArrayList<>()).add(systemAndCode);
    }
}