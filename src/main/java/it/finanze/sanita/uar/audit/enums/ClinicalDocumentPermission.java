package it.finanze.sanita.uar.audit.enums;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ClinicalDocumentPermission {

    private static final Map<String, String> ROLE_TO_CATEGORY = new HashMap<>();

    static {
        ROLE_TO_CATEGORY.put("APR", "MEDICO");
        ROLE_TO_CATEGORY.put("AAS", "MEDICO");
        ROLE_TO_CATEGORY.put("DRS", "MEDICO");
        ROLE_TO_CATEGORY.put("RSA", "MEDICO");
        ROLE_TO_CATEGORY.put("MRP", "MEDICO");
        ROLE_TO_CATEGORY.put("INF", "INFERMIERE_OSTETRICA");
        ROLE_TO_CATEGORY.put("FAR", "FARMACISTA");
        ROLE_TO_CATEGORY.put("ASS", "ASSISTITO");
        ROLE_TO_CATEGORY.put("TUT", "ASSISTITO");
        ROLE_TO_CATEGORY.put("ING", "ASSISTITO");
        ROLE_TO_CATEGORY.put("GEN", "ASSISTITO");

    }

    public static String getCategory(String role) {
        return ROLE_TO_CATEGORY.get(role);
    }

    private static final Map<String, Set<String>> PERMISSIONS = new HashMap<>();

    static {
    	PERMISSIONS.put("82593-5", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("11502-2", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("68604-8", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("11488-4", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("11526-1", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("59258-4", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("34105-7", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("60591-5", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("57833-6", Set.of("MEDICO"));
        PERMISSIONS.put("57832-8", Set.of("MEDICO"));
        PERMISSIONS.put("100971-1", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("60593-1", Set.of("MEDICO"));
        PERMISSIONS.put("87273-9", Set.of("MEDICO", "INFERMIERE_OSTETRICA", "FARMACISTA"));
        PERMISSIONS.put("82539-5", Set.of("MEDICO", "INFERMIERE_OSTETRICA", "FARMACISTA"));
        PERMISSIONS.put("81223-0", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
        PERMISSIONS.put("101881-1", Set.of("MEDICO"));
        PERMISSIONS.put("108276-7", Set.of("MEDICO", "INFERMIERE_OSTETRICA"));
    }

    public static boolean canAccess(String clinicalDocumentCode, List<String> userRoles) {

        Set<String> allowedCategories = PERMISSIONS.get(clinicalDocumentCode);
        if (allowedCategories == null) {
            return false; // codice non riconosciuto
        }

        // Converte i ruoli dell'utente nelle relative categorie
        Set<String> userCategories = userRoles.stream()
                .map(String::toUpperCase)
                .map(ROLE_TO_CATEGORY::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return userCategories.stream().anyMatch(allowedCategories::contains);
    }
    
}