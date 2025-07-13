package com.zosh.modal;

import com.zosh.domain.PaymentMethod;
import com.zosh.domain.PaymentOrderStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "PAYMENT_ORDER")
@SequenceGenerator( // declara el generador
        name = "payment_order_seq", // alias JPA
        sequenceName = "PAYMENT_ORDER_SEQ", // nombre que hibernate creará
        allocationSize = 1)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class PaymentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "payment_order_seq")
    private Long id;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentOrderStatus status = PaymentOrderStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private PaymentMethod paymentMethod;

    @Column(name = "PAYMENT_LINK_ID", length = 255)
    private String paymentLinkId;

    @Column(name = "USER_ID")
    private Long userId;

    @Column(name = "BOOKING_ID")
    private Long bookingId;

    @Column(name = "SALON_ID", nullable = false)
    private Long salonId;

    public void setUpdatedAt(LocalDateTime now) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'setUpdatedAt'");
    }
}
