package ca.uhn.fhir.jpa.starter.common;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.starter.AppProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

/**
 * Configuration for FHIR Parser options.
 * 
 * This configuration ensures that reference versions are preserved at specified paths
 * when parsing FHIR resources, working in conjunction with the auto-versioning feature
 * configured in JpaStorageSettings.
 * 
 * The paths are read from application.yaml property 'auto_version_reference_at_paths'.
 * For example: AuditEvent.agent.who, AuditEvent.entity.what, etc.
 * 
 * Without this configuration, the parser would strip version information from references
 * (e.g., "Patient/123/_history/2" would become "Patient/123"), even though the storage
 * layer adds versions automatically.
 */
@Configuration
public class FhirParserOptionsConfig {

    private static final org.slf4j.Logger ourLog = 
        org.slf4j.LoggerFactory.getLogger(FhirParserOptionsConfig.class);

    /**
     * Configures the FhirContext parser to preserve versions at specified reference paths.
     * 
     * This method is automatically called by Spring after the FhirContext bean is created
     * and injected, ensuring the parser options are configured before any parsing occurs.
     * 
     * @param fhirContext The FHIR context bean created by HAPI FHIR
     * @param appProperties Application properties containing the path configuration
     */
    @Autowired
    public void configureFhirParser(FhirContext fhirContext, AppProperties appProperties) {
        Set<String> paths = appProperties.getAuto_version_reference_at_paths();
        if (paths != null && !paths.isEmpty()) {
            fhirContext.getParserOptions()
               .setDontStripVersionsFromReferencesAtPaths(paths);
            ourLog.info("Configured FhirContext parser to preserve versions at paths: {}", paths);
        } else {
            ourLog.warn("No auto_version_reference_at_paths configured");
        }
    }
}
