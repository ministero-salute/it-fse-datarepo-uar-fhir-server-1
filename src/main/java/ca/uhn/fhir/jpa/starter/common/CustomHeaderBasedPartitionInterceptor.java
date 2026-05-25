package ca.uhn.fhir.jpa.starter.common;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AllergyIntolerance;
import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.CarePlan;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Composition;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.DiagnosticReport;
import org.hl7.fhir.r4.model.DocumentReference;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.EpisodeOfCare;
import org.hl7.fhir.r4.model.ExplanationOfBenefit;
import org.hl7.fhir.r4.model.ImagingStudy;
import org.hl7.fhir.r4.model.Immunization;
import org.hl7.fhir.r4.model.MedicationAdministration;
import org.hl7.fhir.r4.model.MedicationDispense;
import org.hl7.fhir.r4.model.MedicationRequest;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.RequestGroup;
import org.hl7.fhir.r4.model.ServiceRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.jpa.entity.PartitionEntity;
import ca.uhn.fhir.jpa.partition.IPartitionLookupSvc;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import jakarta.annotation.PostConstruct;

@Component
@Interceptor
public class CustomHeaderBasedPartitionInterceptor {

	@Autowired
	private IPartitionLookupSvc partitionLookupSvc;

	// -------------------------------------------------------------------------
	// Mappa resource type → partition ID logica HAPI
	// -------------------------------------------------------------------------
	private static final Map<String, Integer> RESOURCE_TYPE_PARTITION_MAP = Map.ofEntries(
			Map.entry("Account", 1),
			Map.entry("ActivityDefinition", 2),
			Map.entry("AdverseEvent", 3),
			Map.entry("AllergyIntolerance", 4),
			Map.entry("Appointment", 5),
			Map.entry("AppointmentResponse", 6),
			Map.entry("AuditEvent", 7),
			Map.entry("Basic", 8),
			Map.entry("Binary", 9),
			Map.entry("BiologicallyDerivedProduct", 10),
			Map.entry("BodyStructure", 11),
			Map.entry("Bundle", 12),
			Map.entry("CarePlan", 14),
			Map.entry("CareTeam", 15),
			Map.entry("CatalogEntry", 16),
			Map.entry("ChargeItem", 17),
			Map.entry("ChargeItemDefinition", 18),
			Map.entry("Claim", 19),
			Map.entry("ClaimResponse", 20),
			Map.entry("ClinicalImpression", 21),
			Map.entry("Communication", 23),
			Map.entry("CommunicationRequest", 24),
			Map.entry("Composition", 26),
			Map.entry("Condition", 28),
			Map.entry("Consent", 29),
			Map.entry("Contract", 30),
			Map.entry("Coverage", 31),
			Map.entry("CoverageEligibilityRequest", 32),
			Map.entry("CoverageEligibilityResponse", 33),
			Map.entry("DetectedIssue", 34),
			Map.entry("Device", 35),
			Map.entry("DeviceDefinition", 36),
			Map.entry("DeviceMetric", 37),
			Map.entry("DeviceRequest", 38),
			Map.entry("DeviceUseStatement", 39),
			Map.entry("DiagnosticReport", 40),
			Map.entry("DocumentManifest", 41),
			Map.entry("DocumentReference", 42),
			Map.entry("EffectEvidenceSynthesis", 43),
			Map.entry("Encounter", 44),
			Map.entry("Endpoint", 45),
			Map.entry("EnrollmentRequest", 46),
			Map.entry("EnrollmentResponse", 47),
			Map.entry("EpisodeOfCare", 48),
			Map.entry("EventDefinition", 49),
			Map.entry("Evidence", 50),
			Map.entry("EvidenceVariable", 51),
			Map.entry("ExampleScenario", 52),
			Map.entry("ExplanationOfBenefit", 53),
			Map.entry("FamilyMemberHistory", 54),
			Map.entry("Flag", 55),
			Map.entry("Goal", 56),
			Map.entry("GraphDefinition", 57),
			Map.entry("Group", 58),
			Map.entry("GuidanceResponse", 59),
			Map.entry("HealthcareService", 60),
			Map.entry("ImagingStudy", 61),
			Map.entry("Immunization", 62),
			Map.entry("ImmunizationEvaluation", 63),
			Map.entry("ImmunizationRecommendation", 64),
			Map.entry("ImplementationGuide", 65),
			Map.entry("InsurancePlan", 66),
			Map.entry("Invoice", 67),
			Map.entry("Linkage", 69),
			Map.entry("List", 70),
			Map.entry("Location", 71),
			Map.entry("Measure", 72),
			Map.entry("MeasureReport", 73),
			Map.entry("Media", 74),
			Map.entry("Medication", 75),
			Map.entry("MedicationAdministration", 76),
			Map.entry("MedicationDispense", 77),
			Map.entry("MedicationKnowledge", 78),
			Map.entry("MedicationRequest", 79),
			Map.entry("MedicationStatement", 80),
			Map.entry("MedicinalProduct", 81),
			Map.entry("MedicinalProductAuthorization", 82),
			Map.entry("MedicinalProductContraindication", 83),
			Map.entry("MedicinalProductIndication", 84),
			Map.entry("MedicinalProductIngredient", 85),
			Map.entry("MedicinalProductInteraction", 86),
			Map.entry("MedicinalProductManufactured", 87),
			Map.entry("MedicinalProductPackaged", 88),
			Map.entry("MedicinalProductPharmaceutical", 89),
			Map.entry("MedicinalProductUndesirableEffect", 90),
			Map.entry("MessageDefinition", 91),
			Map.entry("MessageHeader", 92),
			Map.entry("MolecularSequence", 93),
			Map.entry("NutritionOrder", 95),
			Map.entry("Observation", 96),
			Map.entry("ObservationDefinition", 97),
			Map.entry("OperationDefinition", 98),
			Map.entry("OperationOutcome", 99),
			Map.entry("Organization", 100),
			Map.entry("OrganizationAffiliation", 101),
			Map.entry("Patient", 102),
			Map.entry("PaymentNotice", 103),
			Map.entry("PaymentReconciliation", 104),
			Map.entry("Person", 105),
			Map.entry("PlanDefinition", 106),
			Map.entry("Practitioner", 107),
			Map.entry("PractitionerRole", 108),
			Map.entry("Procedure", 109),
			Map.entry("Provenance", 110),
			Map.entry("QuestionnaireResponse", 112),
			Map.entry("RelatedPerson", 113),
			Map.entry("RequestGroup", 114),
			Map.entry("ResearchDefinition", 115),
			Map.entry("ResearchElementDefinition", 116),
			Map.entry("ResearchStudy", 117),
			Map.entry("ResearchSubject", 118),
			Map.entry("RiskAssessment", 119),
			Map.entry("RiskEvidenceSynthesis", 120),
			Map.entry("Schedule", 121),
			Map.entry("ServiceRequest", 123),
			Map.entry("Slot", 124),
			Map.entry("Specimen", 125),
			Map.entry("SpecimenDefinition", 126),
			Map.entry("Subscription", 129),
			Map.entry("Substance", 130),
			Map.entry("SubstanceNucleicAcid", 131),
			Map.entry("SubstancePolymer", 132),
			Map.entry("SubstanceProtein", 133),
			Map.entry("SubstanceReferenceInformation", 134),
			Map.entry("SubstanceSourceMaterial", 135),
			Map.entry("SubstanceSpecification", 136),
			Map.entry("SupplyDelivery", 137),
			Map.entry("SupplyRequest", 138),
			Map.entry("Task", 139),
			Map.entry("TerminologyCapabilities", 140),
			Map.entry("TestReport", 141),
			Map.entry("TestScript", 142),
			Map.entry("VerificationResult", 144),
			Map.entry("VisionPrescription", 145)
			);

	// -------------------------------------------------------------------------
	// Resource types that HAPI FHIR does not allow in named partitions.
	// These must always resolve to the default partition (ID 0).
	// -------------------------------------------------------------------------
	private static final Set<String> NON_PARTITIONABLE_RESOURCE_TYPES = Set.of(
			"CapabilityStatement",
			"CodeSystem",
			"CompartmentDefinition",
			"ConceptMap",
			"Library",
			"NamingSystem",
			"OperationDefinition",
			"Questionnaire",
			"SearchParameter",
			"StructureDefinition",
			"StructureMap",
			"ValueSet"
			);

	// -------------------------------------------------------------------------
	// Inizializzazione partizioni logiche HAPI all'avvio
	// -------------------------------------------------------------------------
	@PostConstruct
	public void initPartitions() {
		RESOURCE_TYPE_PARTITION_MAP.forEach((name, id) -> {
			try {
				PartitionEntity partition = new PartitionEntity();
				partition.setId(id);
				partition.setName(name);
				partition.setDescription("Partizione per " + name);
				partitionLookupSvc.createPartition(partition, null);
			} catch (Exception e) {
				// Partizione già esistente: ignorato
			}
		});
	}


	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_CREATE)
	public RequestPartitionId identifyPartitionForCreate(
			RequestDetails requestDetails,
			IBaseResource resource) {

		int partitionId      = resolvePartitionId(resource.fhirType());
		LocalDate clinicalDate = extractClinicalDate(resource);

		return RequestPartitionId.fromPartitionId(partitionId, clinicalDate);
	}

	// -------------------------------------------------------------------------
	// READ — identifica solo la partizione logica per tipo di risorsa.
	// NON passiamo la partitionDate: PostgreSQL farà il pruning fisico
	// automaticamente sulla WHERE generata da HAPI sui search parameters.
	// -------------------------------------------------------------------------
	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_READ)
	public RequestPartitionId identifyPartitionForRead(
			RequestDetails requestDetails) {

		String resourceName = requestDetails.getResourceName();

		// resourceName può essere null in alcune operazioni di sistema
		if (resourceName == null) {
			return RequestPartitionId.allPartitions();
		}

		int partitionId = resolvePartitionId(resourceName);

		// Se la risorsa non è mappata (es. operazioni custom) cerca ovunque
		if (partitionId == 0) {
			return RequestPartitionId.allPartitions();
		}

		return RequestPartitionId.fromPartitionId(partitionId);
	}


	// -------------------------------------------------------------------------
	// Estrae la data clinica più significativa per tipo di risorsa.
	// Per risorse "statiche" (Patient, Practitioner, Organization, ecc.)
	// che non hanno un asse temporale clinico, usa la data corrente
	// in modo che finiscano in una partizione recente e non si disperdano.
	// -------------------------------------------------------------------------
	private LocalDate extractClinicalDate(IBaseResource resource) {

		// --- Documenti ---
		if (resource instanceof Composition r && r.hasDate())
			return toLocalDate(r.getDate());

		if (resource instanceof DocumentReference r && r.hasDate())
			return toLocalDate(r.getDate());

		// --- Referti e osservazioni ---
		if (resource instanceof DiagnosticReport r && r.hasEffective()) {
			if (r.hasEffectiveDateTimeType())
				return toLocalDate(r.getEffectiveDateTimeType().getValue());
			if (r.hasEffectivePeriod() && r.getEffectivePeriod().hasStart())
				return toLocalDate(r.getEffectivePeriod().getStart());
		}

		if (resource instanceof Observation r && r.hasEffective()) {
			if (r.hasEffectiveDateTimeType())
				return toLocalDate(r.getEffectiveDateTimeType().getValue());
			if (r.hasEffectivePeriod() && r.getEffectivePeriod().hasStart())
				return toLocalDate(r.getEffectivePeriod().getStart());
		}

		if (resource instanceof ImagingStudy r && r.hasStarted())
			return toLocalDate(r.getStarted());

		// --- Episodi e incontri ---
		if (resource instanceof Encounter r && r.hasPeriod() && r.getPeriod().hasStart())
			return toLocalDate(r.getPeriod().getStart());

		if (resource instanceof EpisodeOfCare r && r.hasPeriod() && r.getPeriod().hasStart())
			return toLocalDate(r.getPeriod().getStart());

		// --- Procedure e interventi ---
		if (resource instanceof Procedure r && r.hasPerformed()) {
			if (r.hasPerformedDateTimeType())
				return toLocalDate(r.getPerformedDateTimeType().getValue());
			if (r.hasPerformedPeriod() && r.getPerformedPeriod().hasStart())
				return toLocalDate(r.getPerformedPeriod().getStart());
		}

		// --- Problemi e allergie ---
		if (resource instanceof Condition r) {
			if (r.hasOnsetDateTimeType())
				return toLocalDate(r.getOnsetDateTimeType().getValue());
			if (r.hasRecordedDate())
				return toLocalDate(r.getRecordedDate());
		}

		if (resource instanceof AllergyIntolerance r) {
			if (r.hasOnsetDateTimeType())
				return toLocalDate(r.getOnsetDateTimeType().getValue());
			if (r.hasRecordedDate())
				return toLocalDate(r.getRecordedDate());
		}

		// --- Farmaci ---
		if (resource instanceof MedicationRequest r && r.hasAuthoredOn())
			return toLocalDate(r.getAuthoredOn());

		if (resource instanceof MedicationAdministration r && r.hasEffective()) {
			if (r.hasEffectiveDateTimeType())
				return toLocalDate(r.getEffectiveDateTimeType().getValue());
			if (r.hasEffectivePeriod() && r.getEffectivePeriod().hasStart())
				return toLocalDate(r.getEffectivePeriod().getStart());
		}

		if (resource instanceof MedicationStatement r && r.hasEffective()) {
			if (r.hasEffectiveDateTimeType())
				return toLocalDate(r.getEffectiveDateTimeType().getValue());
			if (r.hasEffectivePeriod() && r.getEffectivePeriod().hasStart())
				return toLocalDate(r.getEffectivePeriod().getStart());
		}

		if (resource instanceof MedicationDispense r && r.hasWhenHandedOver())
			return toLocalDate(r.getWhenHandedOver());

		// --- Vaccinazioni ---
		if (resource instanceof Immunization r && r.hasOccurrenceDateTimeType())
			return toLocalDate(r.getOccurrenceDateTimeType().getValue());

		// --- Richieste e piani ---
		if (resource instanceof ServiceRequest r && r.hasAuthoredOn())
			return toLocalDate(r.getAuthoredOn());

		if (resource instanceof CarePlan r && r.hasPeriod() && r.getPeriod().hasStart())
			return toLocalDate(r.getPeriod().getStart());

		if (resource instanceof RequestGroup r && r.hasAuthoredOn())
			return toLocalDate(r.getAuthoredOn());

		// --- Amministrativo / finanziario ---
		if (resource instanceof Claim r && r.hasCreated())
			return toLocalDate(r.getCreated());

		if (resource instanceof ExplanationOfBenefit r && r.hasCreated())
			return toLocalDate(r.getCreated());

		// --- Audit ---
		if (resource instanceof AuditEvent r && r.hasRecorded())
			return toLocalDate(r.getRecorded());

		return LocalDate.now();
	}

	// -------------------------------------------------------------------------
	// Utility
	// -------------------------------------------------------------------------

	private int resolvePartitionId(String resourceType) {
	    if (resourceType == null) return 0;
	    if (NON_PARTITIONABLE_RESOURCE_TYPES.contains(resourceType)) return 0;
	    return RESOURCE_TYPE_PARTITION_MAP.getOrDefault(resourceType, 0);
	}

	private LocalDate toLocalDate(Date date) {
		if (date == null) return LocalDate.now();
		return date.toInstant()
				.atZone(ZoneId.systemDefault())
				.toLocalDate();
	}
}