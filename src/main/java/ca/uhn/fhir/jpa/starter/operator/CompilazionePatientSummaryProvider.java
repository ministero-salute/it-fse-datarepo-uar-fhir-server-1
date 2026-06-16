package ca.uhn.fhir.jpa.starter.operator;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.jpa.api.dao.IFhirResourceDao;
import ca.uhn.fhir.jpa.searchparam.SearchParameterMap;
import ca.uhn.fhir.model.api.Include;
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
public class CompilazionePatientSummaryProvider {

    private static final String OBSERVATION_LOINC_SYSTEM = "http://loinc.org";
    private static final String GRAVIDANZE_PARTI_LOINC_CODE = "10162-6";

    @Autowired
    private EngineComponent engineComponent;

    @Autowired
    private IFhirResourceDao<MedicationStatement> medicationStatementDao;

    @Autowired
    private IFhirResourceDao<MedicationAdministration> medicationAdministrationDao;

    @Autowired
    private IFhirResourceDao<Medication> medicationDao;

    @Autowired
    private IFhirResourceDao<Immunization> immunizationDao;

    @Autowired
    private IFhirResourceDao<Observation> observationDao;

    @Autowired
    private IFhirResourceDao<Procedure> procedureDao;

    @Autowired
    private IFhirResourceDao<Encounter> encounterDao;

    @Autowired
    private IFhirResourceDao<Patient> patientDao;

    @Operation(name = "$compilazione-patient-summary", idempotent = true, returnParameters = {
            @OperationParam(name = "return", type = Bundle.class) })
    public Bundle compilazionePatientSummary(
            @OperationParam(name = "patientValue") StringType patientValue,
            @OperationParam(name = "patientSystem") StringType patientSystem,
            RequestDetails requestDetails) {

        if (patientValue == null || patientValue.isEmpty()) {
            throw new InvalidRequestException("Il parametro patientValue non può essere nullo o vuoto.");
        }

        // patientSystem is optional, extract value safely
        String systemValue = (patientSystem != null && !patientSystem.isEmpty())
                ? patientSystem.getValue()
                : null;

        Patient patient = findPatientByIdentifier(
                systemValue,
                patientValue.getValue(),
                requestDetails);

        IIdType patientId = patient.getIdElement().toUnqualifiedVersionless();
        DateRangeParam lastMonthRange = buildLastMonthDateRange();

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.SEARCHSET);

        Set<String> addedResourceKeys = new LinkedHashSet<>();

        addResourcesToBundle(
                bundle,
                searchResourceWithRevinclude("MedicationStatement", patientId, lastMonthRange, requestDetails),
                addedResourceKeys);

        addResourcesToBundle(
                bundle,
                searchResourceWithRevinclude("MedicationAdministration", patientId, lastMonthRange, requestDetails),
                addedResourceKeys);

        addReferencedMedications(bundle, addedResourceKeys, requestDetails);

        addResourcesToBundle(
                bundle,
                searchResourceWithRevinclude("Immunization", patientId, null, requestDetails),
                addedResourceKeys);

        addResourcesToBundle(
                bundle,
                searchObservationWithRevinclude(patientId, requestDetails),
                addedResourceKeys);

        addResourcesToBundle(
                bundle,
                searchResourceWithRevinclude("Procedure", patientId, null, requestDetails),
                addedResourceKeys);

        addResourcesToBundle(
                bundle,
                searchResourceWithRevinclude("Encounter", patientId, null, requestDetails),
                addedResourceKeys);

        int matchCount = (int) bundle.getEntry().stream()
                .filter(e -> e.getSearch().getMode() == Bundle.SearchEntryMode.MATCH)
                .count();
        bundle.setTotal(matchCount);

        return bundle;
    }

    private Patient findPatientByIdentifier(
            String patientSystem,
            String patientValue,
            RequestDetails requestDetails) {

        SearchParameterMap patientSearch = new SearchParameterMap();
        patientSearch.setLoadSynchronous(true);
        patientSearch.add(Patient.SP_IDENTIFIER, new TokenParam(patientSystem, patientValue));

        IBundleProvider patientResults = patientDao.search(patientSearch, requestDetails);
        List<Patient> patients = patientResults.getResources(0, Integer.MAX_VALUE).stream()
                .filter(r -> r instanceof Patient)
                .map(r -> (Patient) r)
                .toList();

        if (patients.isEmpty()) {
            SearchParameterMap fallbackSearch = new SearchParameterMap();
            fallbackSearch.setLoadSynchronous(true);
            fallbackSearch.add(Patient.SP_IDENTIFIER, new TokenParam(null, patientValue));

            patientResults = patientDao.search(fallbackSearch, requestDetails);
            patients = patientResults.getResources(0, Integer.MAX_VALUE).stream()
                    .filter(r -> r instanceof Patient)
                    .map(r -> (Patient) r)
                    .toList();
        }

        if (patients.isEmpty()) {
            throw new InvalidRequestException("Nessun Patient trovato con identifier: " + patientValue);
        }

        return patients.get(0);
    }

    private List<? extends Resource> searchResourceWithRevinclude(
            String resourceTypeName,
            IIdType patientId,
            DateRangeParam dateRange,
            RequestDetails requestDetails) {

        IFhirResourceDao<?> dao = getDaoForResourceType(resourceTypeName);

        SearchParameterMap map = buildBaseMap(
                getPatientSPForResourceType(resourceTypeName),
                patientId,
                getDateSPForResourceType(resourceTypeName),
                dateRange);

        map.addRevInclude(new Include("Composition:entry"));
        map.addRevInclude(new Include("DocumentReference:related", true));

        IBundleProvider results = dao.search(map, requestDetails);

        return results.getResources(0, Integer.MAX_VALUE).stream()
                .filter(r -> r instanceof Resource)
                .map(r -> (Resource) r)
                .toList();
    }

    private IFhirResourceDao<?> getDaoForResourceType(String resourceTypeName) {
        return switch (resourceTypeName) {
            case "MedicationStatement" -> medicationStatementDao;
            case "MedicationAdministration" -> medicationAdministrationDao;
            case "Medication" -> medicationDao;
            case "Immunization" -> immunizationDao;
            case "Observation" -> observationDao;
            case "Procedure" -> procedureDao;
            case "Encounter" -> encounterDao;
            default -> throw new InvalidRequestException(
                    "resourceType non gestito internamente: " + resourceTypeName);
        };
    }

    private String getPatientSPForResourceType(String resourceTypeName) {
        return switch (resourceTypeName) {
            case "MedicationStatement" -> "subject";
            case "MedicationAdministration" -> "subject";
            case "Immunization" -> Immunization.SP_PATIENT;
            case "Observation" -> Observation.SP_PATIENT;
            case "Procedure" -> Procedure.SP_PATIENT;
            case "Encounter" -> Encounter.SP_PATIENT;
            default -> throw new InvalidRequestException(
                    "patient search parameter non gestito per: " + resourceTypeName);
        };
    }

    private String getDateSPForResourceType(String resourceTypeName) {
        return switch (resourceTypeName) {
            case "MedicationStatement" -> MedicationStatement.SP_EFFECTIVE;
            case "MedicationAdministration" -> MedicationAdministration.SP_EFFECTIVE_TIME;
            case "Immunization" -> Immunization.SP_DATE;
            case "Observation" -> Observation.SP_DATE;
            case "Procedure" -> Procedure.SP_DATE;
            case "Encounter" -> Encounter.SP_DATE;
            default -> throw new InvalidRequestException(
                    "date search parameter non gestito per: " + resourceTypeName);
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

    private List<? extends Resource> searchObservationWithRevinclude(
            IIdType patientId,
            RequestDetails requestDetails) {

        SearchParameterMap map = buildBaseMap(
                Observation.SP_PATIENT,
                patientId,
                Observation.SP_DATE,
                null);
        map.add(Observation.SP_CODE, new TokenParam(OBSERVATION_LOINC_SYSTEM, GRAVIDANZE_PARTI_LOINC_CODE));
        map.addRevInclude(new Include("Composition:entry"));
        map.addRevInclude(new Include("DocumentReference:related", true));

        IBundleProvider results = observationDao.search(map, requestDetails);

        return results.getResources(0, Integer.MAX_VALUE).stream()
                .filter(r -> r instanceof Resource)
                .map(r -> (Resource) r)
                .toList();
    }

    private void addResourcesToBundle(
            Bundle bundle,
            List<? extends Resource> resources,
            Set<String> addedResourceKeys) {

        for (Resource resource : resources) {
            Bundle.SearchEntryMode mode = isIncludedResource(resource)
                    ? Bundle.SearchEntryMode.INCLUDE
                    : Bundle.SearchEntryMode.MATCH;
            addToBundle(bundle, resource, mode, addedResourceKeys);
        }
    }

    private boolean isIncludedResource(Resource resource) {
        String resourceTypeName = resource.getResourceType().name();
        return "Composition".equals(resourceTypeName) || "DocumentReference".equals(resourceTypeName);
    }

    private void addReferencedMedications(
            Bundle bundle,
            Set<String> addedResourceKeys,
            RequestDetails requestDetails) {

        List<Resource> snapshot = bundle.getEntry().stream()
                .map(Bundle.BundleEntryComponent::getResource)
                .filter(r -> r instanceof MedicationStatement || r instanceof MedicationAdministration)
                .toList();

        for (Resource resource : snapshot) {
            Reference medicationReference = extractMedicationReference(resource);
            if (medicationReference == null) {
                continue;
            }

            Resource medication = engineComponent.readReference(medicationReference, requestDetails);
            if (medication instanceof Medication) {
                addToBundle(bundle, medication, Bundle.SearchEntryMode.INCLUDE, addedResourceKeys);
            }
        }
    }

    private Reference extractMedicationReference(Resource resource) {
        if (resource instanceof MedicationStatement medicationStatement
                && medicationStatement.hasMedicationReference()) {
            return medicationStatement.getMedicationReference();
        }

        if (resource instanceof MedicationAdministration medicationAdministration
                && medicationAdministration.hasMedicationReference()) {
            return medicationAdministration.getMedicationReference();
        }

        return null;
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
        entry.setFullUrl(resource.getIdElement().toUnqualifiedVersionless().getValue());
        entry.getSearch().setMode(mode);
    }

    private DateRangeParam buildLastMonthDateRange() {
        LocalDate today = LocalDate.now();
        LocalDate oneMonthAgo = today.minusMonths(1);

        DateRangeParam dateRange = new DateRangeParam();
        dateRange.setLowerBound(new DateParam("ge" + oneMonthAgo));
        dateRange.setUpperBound(new DateParam("le" + today));
        return dateRange;
    }

}