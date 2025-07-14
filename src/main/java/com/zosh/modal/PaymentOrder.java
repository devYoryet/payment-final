// src/main/java/com/zosh/modal/PaymentOrder.java
package com.zosh.modal;

import com.zosh.domain.PaymentMethod;
import com.zosh.domain.PaymentOrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "payment_orders")
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_order_seq")
    @SequenceGenerator(name = "payment_order_seq", sequenceName = "payment_orders_seq", allocationSize = 1)
    private Long id;

    // ✅ CORREGIR: Usar BigDecimal en lugar de Long
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "payment_link_id")
    private String paymentLinkId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "booking_id", nullable = false)
    private Long bookingId;

    @Column(name = "salon_id", nullable = false)
    private Long salonId;

    // ✅ AGREGAR: Campo status que faltaba
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentOrderStatus status = PaymentOrderStatus.PENDING;
}