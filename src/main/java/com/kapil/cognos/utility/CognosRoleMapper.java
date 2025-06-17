package com.kapil.cognos.utility;

import java.util.*;
import java.util.stream.Collectors;

public class CognosRoleMapper {

    private static final Map<String, String> GROUP_TO_ROLE = new HashMap<>();

    static {
        GROUP_TO_ROLE.put("Administrators", "ROLE_ADMIN");
        GROUP_TO_ROLE.put("Consumers", "ROLE_USER");
    }

    public static List<String> mapGroupsToRoles(List<String> cognosGroups) {
        return cognosGroups.stream()
                .map(GROUP_TO_ROLE::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}