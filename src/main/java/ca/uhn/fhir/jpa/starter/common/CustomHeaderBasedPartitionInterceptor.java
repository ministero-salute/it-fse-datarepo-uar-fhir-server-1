package ca.uhn.fhir.jpa.starter.common;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Practitioner;

import ca.uhn.fhir.interceptor.api.Hook;
import ca.uhn.fhir.interceptor.api.Interceptor;
import ca.uhn.fhir.interceptor.api.Pointcut;
import ca.uhn.fhir.interceptor.model.RequestPartitionId;
import ca.uhn.fhir.rest.api.server.RequestDetails;

@Interceptor
public class CustomHeaderBasedPartitionInterceptor {
 
	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_CREATE)
	public RequestPartitionId PartitionIdentifyCreate(IBaseResource theResource, RequestDetails theRequestDetails) {
		return calculatePartition(theResource, theRequestDetails);
	}
	
	@Hook(Pointcut.STORAGE_PARTITION_IDENTIFY_READ)
	public RequestPartitionId PartitionIdentifyRead(IBaseResource theResource, RequestDetails theRequestDetails) {
	    return calculatePartition(theResource, theRequestDetails);
	}
	
	private RequestPartitionId calculatePartition(IBaseResource theResource, RequestDetails theRequestDetails) {
	    String partitionName = theRequestDetails.getHeader("X-Tenant-ID");

	    if (theResource instanceof Patient) {
	        return RequestPartitionId.fromPartitionName("PATIENT");
	    } else if (theResource instanceof Location) {
	        return RequestPartitionId.fromPartitionName("LOCATION");
	    } else if (theResource instanceof Organization) {
	        return RequestPartitionId.fromPartitionName("ORGANIZATION");
	    } else if (theResource instanceof Practitioner) {
	        return RequestPartitionId.fromPartitionName("PRACTITIONER");
	    } else {
	    	 return RequestPartitionId.fromPartitionName(partitionName.toUpperCase()); 
	    }
	}
   
}