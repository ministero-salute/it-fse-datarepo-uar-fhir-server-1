package ca.uhn.fhir.jpa.starter.operator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import ca.uhn.fhir.rest.server.RestfulServer;
import jakarta.annotation.PostConstruct;

/**
 * ┌─────────────────────────────────────────────────────────────┐
 * │  PROGETTO : hapi-fhir-jpaserver-starter                    │
 * │  PACKAGE  : ca.uhn.fhir.jpa.starter.config                 │
 * │  FILE     : SintesiConfig.java                              │
 * ├─────────────────────────────────────────────────────────────┤
 * │  SCOPO: Registra EngineComponent nel RestfulServer  │
 * │  HAPI già esistente, senza toccare la config di default.    │
 * └─────────────────────────────────────────────────────────────┘
 *
 * NOTA: hapi-fhir-jpaserver-starter istanzia il RestfulServer
 * tramite JpaRestfulServer (o FhirServerConfigR4). Iniettandolo
 * qui possiamo aggiungere provider extra senza sovrascrivere nulla.
 */
@Configuration
public class SintesiConfig {

    @Autowired
    private RestfulServer restfulServer; // bean già creato da HAPI starter

    @Autowired
    private PacchettoProvider pacchettoProvider;

    @Autowired
    private DatiCliniciDisponibiliProvider datiCliniciDisponibiliProvider;

    @PostConstruct
    public void registerProviders() {
        restfulServer.registerProvider(pacchettoProvider);
        restfulServer.registerProvider(datiCliniciDisponibiliProvider);
    }
}