package ca.uhn.fhir.jpa.starter.config;

import ca.uhn.fhir.jpa.api.config.JpaStorageSettings;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

@Configuration
@DependsOn("jpaStorageSettings")
public class JpaDeleteRiBypassConfig {
}
