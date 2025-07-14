package com.zosh.domain;

public enum PaymentMethod {
    RAZORPAY,
    STRIPE,
    CHILE_PAY; // ✅ AGREGAR ESTE NUEVO MÉTODO

    // Método auxiliar para obtener el método por defecto para Chile
    public static PaymentMethod getDefaultChileMethod() {
        return CHILE_PAY;
    }

    // Método auxiliar para verificar si es un método chileno
    public boolean isChileanMethod() {
        return this == CHILE_PAY;
    }
}