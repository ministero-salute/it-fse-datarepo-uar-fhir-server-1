package ca.uhn.fhir.jpa.starter.operator;

import java.util.List;

import org.hl7.fhir.instance.model.api.IIdType;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Patient;
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

    @Autowired
    private IFhirResourceDao<Observation> observationDao;

    @Autowired
    private IFhirResourceDao<Patient> patientDao;

    @Operation(name = "$dati-clinici-disponibili", idempotent = true)
    public Parameters datiCliniciDisponibili(
            @OperationParam(name = "patientValue", min = 1) StringType patientValue,
            @OperationParam(name = "patientSystem", min = 1) StringType patientSystem,
            @OperationParam(name = "dateFrom", min = 0) DateType dateFrom,
            @OperationParam(name = "dateTo", min = 0) DateType dateTo,
            RequestDetails requestDetails) {

        // 1. Risolvi CF → ID FHIR del Patient tramite identifier
        SearchParameterMap patientSearch = new SearchParameterMap();
        patientSearch.setLoadSynchronous(true);
        patientSearch.add(
                Patient.SP_IDENTIFIER,
                new TokenParam(patientSystem.getValue(), patientValue.getValue()));

        IBundleProvider patientResults = patientDao.search(patientSearch, requestDetails);

        // ← usa getResources() invece di getAllResources(): compatibile con entrambe le modalità
        List<Patient> patients = patientResults.getResources(0, Integer.MAX_VALUE)
                .stream()
                .filter(r -> r instanceof Patient)
                .map(r -> (Patient) r)
                .toList();

        if (patients.isEmpty()) {
            throw new InvalidRequestException(
                    "Nessun Patient trovato con identifier: "
                    + patientSystem.getValue() + "|" + patientValue.getValue());
        }

        // Il CF è univoco: prendi il primo risultato
        IIdType patientId = patients.get(0).getIdElement().toUnqualifiedVersionless();

        // 2. Cerca le Observation legate al Patient tramite il suo ID FHIR
        SearchParameterMap obsSearch = new SearchParameterMap();
        obsSearch.setLoadSynchronous(true);
        obsSearch.add(
                Observation.SP_PATIENT,
                new ReferenceParam(patientId.getValue()));

        // 3. Filtro temporale opzionale
        if (dateFrom != null || dateTo != null) {
            DateRangeParam dateRange = new DateRangeParam();
            if (dateFrom != null) {
                dateRange.setLowerBound(new DateParam("ge" + dateFrom.getValueAsString()));
            }
            if (dateTo != null) {
                dateRange.setUpperBound(new DateParam("le" + dateTo.getValueAsString()));
            }
            obsSearch.add(Observation.SP_DATE, dateRange);
        }

        // 4. Esegui la ricerca sul DAO interno
        IBundleProvider obsResults = observationDao.search(obsSearch, requestDetails);

        // ← usa getResources() invece di getAllResources()
        List<Observation> observations = obsResults.getResources(0, Integer.MAX_VALUE)
                .stream()
                .filter(r -> r instanceof Observation)
                .map(r -> (Observation) r)
                .toList();

        // 5. Costruisci il Parameters di risposta:
        // - name  = display dell'Observation (primo coding del codice)
        // - value = "system|code"
        Parameters parameters = new Parameters();

        for (Observation obs : observations) {
            if (obs.hasCode() && obs.getCode().hasCoding()) {

                var coding = obs.getCode().getCodingFirstRep();

                String displayName = coding.hasDisplay()
                        ? coding.getDisplay()
                        : coding.getCode();

                String codeValue = coding.getSystem() + "|" + coding.getCode();

                parameters.addParameter()
                        .setName(displayName)
                        .setValue(new StringType(codeValue));
            }
        }

        return parameters;
    }
}