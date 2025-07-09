// src/main/java/com/zosh/domain/UserRole.java - NORMALIZADO
package com.zosh.domain;

public enum UserRole {
    CUSTOMER,
    ADMIN,
    SALON_OWNER;

    /**
     * Método para normalizar roles desde diferentes fuentes
     * Maneja tanto formatos con ROLE_ como sin él
     */
    public static UserRole fromString(String role) {
        if (role == null || role.isEmpty()) {
            return CUSTOMER;
        }

        // Limpiar el string - remover prefijos y normalizar
        String cleanRole = role.toUpperCase()
                .replace("ROLE_", "")
                .replace("COGNITO_", "")
                .trim();

        switch (cleanRole) {
            case "SALON_OWNER":
            case "SALONOWNER":
                return SALON_OWNER;
            case "ADMIN":
            case "ADMINISTRATOR":
                return ADMIN;
            case "CUSTOMER":
            case "USER":
            default:
                return CUSTOMER;
        }
    }

    /**
     * Método para obtener el string del rol (sin prefijo)
     */
    public String getRoleString() {
        return this.name();
    }

    /**
     * Método para obtener el string del rol con prefijo ROLE_ (si es necesario)
     */
    public String getRoleStringWithPrefix() {
        return "ROLE_" + this.name();
    }
}