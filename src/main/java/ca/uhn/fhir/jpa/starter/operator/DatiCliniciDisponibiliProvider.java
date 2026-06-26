package ca.uhn.fhir.jpa.starter.operator;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.annotation.Operation;
import ca.uhn.fhir.rest.annotation.OperationParam;
import ca.uhn.fhir.rest.api.SortOrderEnum;
import ca.uhn.fhir.rest.api.SortSpec;
import ca.uhn.fhir.rest.api.server.IBundleProvider;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.param.DateParam;
import ca.uhn.fhir.rest.param.DateRangeParam;
import ca.uhn.fhir.rest.param.ReferenceParam;
import ca.uhn.fhir.rest.param.TokenParam;
import ca.uhn.fhir.rest.server.exceptions.InvalidRequestException;

@Component
public class DatiCliniciDisponibiliProvider {

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
    public Bundle datiCliniciDisponibili(
            @OperationParam(name = "patientValue", min = 1) StringType patientValue,
            @OperationParam(name = "dateFrom", min = 0) DateType dateFrom,
            @OperationParam(name = "dateTo", min = 0) DateType dateTo,
            @OperationParam(name = "resourceType", min = 0) StringType resourceType,
            RequestDetails requestDetails) {

        // *************************************
        // Check on resourceType
        if (resourceType != null && !resourceType.isEmpty()) {
            String rt = resourceType.getValue();
            if (!SUPPORTED_RESOURCE_TYPES.contains(rt)) {
                throw new InvalidRequestException(
                        "resourceType non supportato: '" + rt + "'. Valori ammessi: "
                                + String.join(", ", SUPPORTED_RESOURCE_TYPES));
            }
        }

        // **************************************
        // Search PATIENT
        String patientValueStr = patientValue.getValue();
        SearchParameterMap patientSearch = new SearchParameterMap();
        patientSearch.setLoadSynchronous(true);
        patientSearch.add(Patient.SP_IDENTIFIER, new TokenParam(null, patientValueStr));
        IBundleProvider patientResults = patientDao.search(patientSearch, requestDetails);

        List<Patient> patients = patientResults.getResources(0, Integer.MAX_VALUE)
                .stream()
                .filter(r -> r instanceof Patient)
                .map(r -> (Patient) r)
                .toList();

        if (patients.isEmpty()) {
            throw new InvalidRequestException(
                    "Nessun Patient trovato con identifier: " + patientValueStr);
        }

        IIdType patientId = patients.get(0).getIdElement().toUnqualifiedVersionless();

        // ********************************
        // DATE RANGE
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

        List<String> resourcesToSearch = (resourceType != null && !resourceType.isEmpty())
                ? List.of(resourceType.getValue())
                : SUPPORTED_RESOURCE_TYPES;

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);

        Set<String> addedResourceKeys = new LinkedHashSet<>();

        for (String rt : resourcesToSearch) {
            List<? extends Resource> primaryResources = searchResourceWithRevinclude(
                    rt, patientId, dateRange, requestDetails);

            for (Resource resource : primaryResources) {
                String resourceTypeName = resource.getResourceType().name();

                if ("Composition".equals(resourceTypeName) || "DocumentReference".equals(resourceTypeName)) {
                    addToBundle(bundle, resource, Bundle.SearchEntryMode.INCLUDE, addedResourceKeys);
                } else {
                    Resource lightResource = toLightResource(resource);
                    addToBundle(bundle, lightResource, Bundle.SearchEntryMode.MATCH, addedResourceKeys);
                }
            }
        }

        int matchCount = (int) bundle.getEntry().stream()
                .filter(e -> e.getSearch().getMode() == Bundle.SearchEntryMode.MATCH)
                .count();
        bundle.setTotal(matchCount);

        return bundle;
    }

    private List<Resource> searchResourceWithRevinclude(
            String resourceTypeName,
            IIdType patientId,
            DateRangeParam dateRange,
            RequestDetails requestDetails) {

        System.out.println("Cerco risorse per dati clinici disponibili per tipo " + resourceTypeName
                + " e per paziente con id " + patientId);

        IFhirResourceDao<?> dao = getDaoForResourceType(resourceTypeName);
        List<Resource> collected = new ArrayList<>();

        int count = 10;
        int offset = 0;

        while (true) {
            SearchParameterMap map = buildBaseMap(
                    getPatientSPForResourceType(resourceTypeName),
                    patientId,
                    getDateSPForResourceType(resourceTypeName),
                    dateRange);

            map.addRevInclude(new Include("Composition:entry"));
            map.addRevInclude(new Include("DocumentReference:related", true));
            map.setLoadSynchronous(true);
            map.setSort(new SortSpec("_lastUpdated", SortOrderEnum.DESC));
            map.setCount(count);
            map.setOffset(offset);

            IBundleProvider results = dao.search(map, requestDetails);
            List<IBaseResource> page = results.getResources(0, Integer.MAX_VALUE);

            if (page == null || page.isEmpty())
                break;

            page.stream()
                    .filter(Resource.class::isInstance)
                    .map(Resource.class::cast)
                    .forEach(collected::add);

            if (page.size() < count)
                break;
            offset += count;
        }

        System.out.println("Totale risorse recuperate per " + resourceTypeName + ": " + collected.size());

        return collected;
    }

    private IFhirResourceDao<?> getDaoForResourceType(String resourceTypeName) {
        return switch (resourceTypeName) {
            case "Observation" -> observationDao;
            case "Condition" -> conditionDao;
            case "AllergyIntolerance" -> allergyIntoleranceDao;
            case "MedicationStatement" -> medicationStatementDao;
            case "MedicationDispense" -> medicationDispenseDao;
            case "Immunization" -> immunizationDao;
            case "Procedure" -> procedureDao;
            case "DiagnosticReport" -> diagnosticReportDao;
            default -> throw new InvalidRequestException(
                    "resourceType non gestito internamente: " + resourceTypeName);
        };
    }

    private String getPatientSPForResourceType(String resourceTypeName) {
        return switch (resourceTypeName) {
            case "Observation" -> Observation.SP_PATIENT;
            case "Condition" -> Condition.SP_PATIENT;
            case "AllergyIntolerance" -> AllergyIntolerance.SP_PATIENT;
            case "MedicationStatement" -> MedicationStatement.SP_PATIENT;
            case "MedicationDispense" -> MedicationDispense.SP_PATIENT;
            case "Immunization" -> Immunization.SP_PATIENT;
            case "Procedure" -> Procedure.SP_PATIENT;
            case "DiagnosticReport" -> DiagnosticReport.SP_PATIENT;
            default -> throw new InvalidRequestException(
                    "resourceType non gestito internamente: " + resourceTypeName);
        };
    }

    private String getDateSPForResourceType(String resourceTypeName) {
        return switch (resourceTypeName) {
            case "Observation" -> Observation.SP_DATE;
            case "Condition" -> Condition.SP_ONSET_DATE;
            case "AllergyIntolerance" -> AllergyIntolerance.SP_DATE;
            case "MedicationStatement" -> MedicationStatement.SP_EFFECTIVE;
            case "MedicationDispense" -> MedicationDispense.SP_WHENHANDEDOVER;
            case "Immunization" -> Immunization.SP_DATE;
            case "Procedure" -> Procedure.SP_DATE;
            case "DiagnosticReport" -> DiagnosticReport.SP_DATE;
            default -> throw new InvalidRequestException(
                    "resourceType non gestito internamente: " + resourceTypeName);
        };
    }

    private SearchParameterMap buildBaseMap(
            String patientSp,
            IIdType patientId,
            String dateSp,
            DateRangeParam dateRange) {

        SearchParameterMap map = new SearchParameterMap();
        map.setLoadSynchronous(true);
        map.add(patientSp, new ReferenceParam(patientId.getValue()));
        if (dateRange != null) {
            map.add(dateSp, dateRange);
        }
        return map;
    }

    private Resource toLightResource(Resource resource) {
        Resource lightResource = createLightResource(resource);

        if (lightResource != null && resource.hasIdElement()) {
            lightResource.setId(resource.getIdElement().toUnqualifiedVersionless());
        }

        CodeableConcept codeConcept = extractCodeFromResource(resource);
        if (lightResource != null && codeConcept != null) {
            setCodeOnLightResource(lightResource, codeConcept);
        }

        return lightResource;
    }

    private Resource createLightResource(Resource resource) {
        String typeName = resource.getResourceType().name();

        return switch (typeName) {
            case "Observation" -> new Observation();
            case "Condition" -> new Condition();
            case "AllergyIntolerance" -> new AllergyIntolerance();
            case "MedicationStatement" -> new MedicationStatement();
            case "MedicationDispense" -> new MedicationDispense();
            case "Immunization" -> new Immunization();
            case "Procedure" -> new Procedure();
            case "DiagnosticReport" -> new DiagnosticReport();
            default -> null;
        };
    }

    private CodeableConcept extractCodeFromResource(Resource resource) {
        String typeName = resource.getResourceType().name();

        return switch (typeName) {
            case "Observation" -> {
                Observation obs = (Observation) resource;
                yield obs.hasCode() ? obs.getCode() : null;
            }
            case "Condition" -> {
                Condition cond = (Condition) resource;
                yield cond.hasCode() ? cond.getCode() : null;
            }
            case "AllergyIntolerance" -> {
                AllergyIntolerance allergy = (AllergyIntolerance) resource;
                yield allergy.hasCode() ? allergy.getCode() : null;
            }
            case "MedicationStatement" -> {
                MedicationStatement ms = (MedicationStatement) resource;
                yield ms.hasMedicationCodeableConcept() ? ms.getMedicationCodeableConcept() : null;
            }
            case "MedicationDispense" -> {
                MedicationDispense md = (MedicationDispense) resource;
                yield md.hasMedicationCodeableConcept() ? md.getMedicationCodeableConcept() : null;
            }
            case "Immunization" -> {
                Immunization imm = (Immunization) resource;
                yield imm.hasVaccineCode() ? imm.getVaccineCode() : null;
            }
            case "Procedure" -> {
                Procedure proc = (Procedure) resource;
                yield proc.hasCode() ? proc.getCode() : null;
            }
            case "DiagnosticReport" -> {
                DiagnosticReport dr = (DiagnosticReport) resource;
                yield dr.hasCode() ? dr.getCode() : null;
            }
            default -> null;
        };
    }

    private void setCodeOnLightResource(Resource lightResource, CodeableConcept code) {
        String typeName = lightResource.getResourceType().name();

        switch (typeName) {
            case "Observation" -> ((Observation) lightResource).setCode(code);
            case "Condition" -> ((Condition) lightResource).setCode(code);
            case "AllergyIntolerance" -> ((AllergyIntolerance) lightResource).setCode(code);
            case "MedicationStatement" -> ((MedicationStatement) lightResource).setMedication(code);
            case "MedicationDispense" -> ((MedicationDispense) lightResource).setMedication(code);
            case "Immunization" -> ((Immunization) lightResource).setVaccineCode(code);
            case "Procedure" -> ((Procedure) lightResource).setCode(code);
            case "DiagnosticReport" -> ((DiagnosticReport) lightResource).setCode(code);
            default -> {
            }
        }
    }

    private void addToBundle(
            Bundle bundle,
            Resource resource,
            Bundle.SearchEntryMode mode,
            Set<String> addedResourceKeys) {

        if (resource == null || !resource.hasIdElement() || resource.getIdElement().isEmpty()) {
            return;
        }

        String key = resource.getResourceType().name() + "/"
                + resource.getIdElement().toUnqualifiedVersionless().getIdPart();

        if (!addedResourceKeys.add(key)) {
            return;
        }

        Bundle.BundleEntryComponent entry = bundle.addEntry();
        entry.setResource(resource);
        entry.setFullUrl(toFullUrl(resource.getIdElement().toUnqualifiedVersionless()));
        entry.getSearch().setMode(mode);
    }

    private String toFullUrl(IIdType id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        if (id.hasBaseUrl()) {
            return id.toUnqualifiedVersionless().getValue();
        }
        return id.getResourceType() + "/" + id.getIdPart();
    }
}