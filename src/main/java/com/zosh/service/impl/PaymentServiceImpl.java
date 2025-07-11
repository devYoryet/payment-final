package com.zosh.service.impl;

import com.razorpay.Payment;
import com.razorpay.PaymentLink;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import com.zosh.domain.PaymentMethod;
import com.zosh.domain.PaymentOrderStatus;
import com.zosh.exception.UserException;
import com.zosh.messaging.BookingEventProducer;
import com.zosh.messaging.NotificationEventProducer;
import com.zosh.modal.PaymentOrder;
import com.zosh.payload.dto.BookingDTO;
import com.zosh.payload.dto.UserDTO;
import com.zosh.payload.response.PaymentLinkResponse;
import com.zosh.repository.PaymentOrderRepository;
import com.zosh.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    @Value("${stripe.api.key}")
    private String stripeSecretKey;

    @Value("${razorpay.api.key}")
    private String apiKey;

    @Value("${razorpay.api.secret}")
    private String apiSecret;

    private final PaymentOrderRepository paymentOrderRepository;
    private final NotificationEventProducer notificationEventProducer;
    private final BookingEventProducer bookingEventProducer;

    /* ───────────────────────── CREATE ORDER ───────────────────────── */
    @Override
    public PaymentLinkResponse createOrder(UserDTO user, BookingDTO booking, PaymentMethod paymentMethod)
            throws RazorpayException, UserException, StripeException {

        System.out.println("🇨🇱 PAYMENT CHILE - createOrder");
        System.out.println("   Usuario: " + user.getEmail());
        System.out.println("   Booking ID: " + booking.getId());
        System.out.println("   Total: $" + booking.getTotalPrice() + " CLP");
        System.out.println("   Método: " + paymentMethod);

        try {
            // Total price como BigDecimal (2 decimales)
            BigDecimal total = booking.getTotalPrice().setScale(2, RoundingMode.HALF_UP);

            PaymentOrder order = new PaymentOrder();
            order.setUserId(user.getId());
            order.setAmount(total);
            order.setBookingId(booking.getId());
            order.setSalonId(booking.getSalonId());
            order.setPaymentMethod(paymentMethod);

            PaymentOrder saved = paymentOrderRepository.save(order);
            System.out.println("✅ PaymentOrder guardado con ID: " + saved.getId());

            PaymentLinkResponse res = new PaymentLinkResponse();

            // 🇨🇱 CREAR MOCK PAYMENT LINK CHILENO
            String mockPaymentUrl = createChileanMockPaymentLink(user, total, saved.getId(), paymentMethod);
            String mockPaymentId = "CHILE_PAY_" + saved.getId() + "_" + System.currentTimeMillis();

            res.setPayment_link_url(mockPaymentUrl);
            res.setPayment_link_id(mockPaymentId);

            saved.setPaymentLinkId(mockPaymentId);
            paymentOrderRepository.save(saved);

            System.out.println("✅ Mock Payment Link Chileno creado:");
            System.out.println("   URL: " + mockPaymentUrl);
            System.out.println("   ID: " + mockPaymentId);

            return res;

        } catch (Exception e) {
            System.err.println("❌ Error en createOrder: " + e.getMessage());
            e.printStackTrace();
            throw new UserException("Error procesando pago: " + e.getMessage());
        }
    }

    // 🇨🇱 NUEVO MÉTODO: CREAR MOCK PAYMENT LINK CHILENO
    private String createChileanMockPaymentLink(UserDTO user, BigDecimal amount, Long orderId, PaymentMethod method) {

        System.out.println("🇨🇱 Creando mock payment chileno...");

        // Determinar el proveedor según el método
        String provider = determineChileanProvider(method);

        // Crear URL con parámetros
        String baseUrl = "http://localhost:3000/payment/chile-mock";
        String params = String.format("?orderId=%d&amount=%s&provider=%s&email=%s&name=%s",
                orderId,
                amount.toString(),
                provider,
                user.getEmail(),
                user.getFullName().replace(" ", "+"));

        String finalUrl = baseUrl + params;

        System.out.println("🇨🇱 Mock URL generada: " + finalUrl);

        return finalUrl;
    }

    // 🇨🇱 DETERMINAR PROVEEDOR CHILENO
    private String determineChileanProvider(PaymentMethod method) {
        switch (method) {
            case RAZORPAY:
                return "webpay"; // Usar WebPay como default
            case STRIPE:
                return "onepay";
            default:
                // Rotar entre proveedores chilenos
                String[] providers = { "webpay", "onepay", "mercadopago", "khipu", "flow" };
                int index = (int) (System.currentTimeMillis() % providers.length);
                return providers[index];
        }
    }

    /* ───────────────────────── FETCHERS ───────────────────────── */
    @Override
    public PaymentOrder getPaymentOrderById(Long id) throws Exception {
        return paymentOrderRepository.findById(id)
                .orElseThrow(() -> new Exception("Payment order not found with id " + id));
    }

    @Override
    public PaymentOrder getPaymentOrderByPaymentId(String paymentLinkId) throws Exception {
        PaymentOrder po = paymentOrderRepository.findByPaymentLinkId(paymentLinkId);
        if (po == null)
            throw new Exception("Payment order not found with id " + paymentLinkId);
        return po;
    }

    /* ───────────────────────── CONFIRM / CAPTURE ───────────────────────── */
    // ✅ ACTUALIZAR MÉTODO DE PROCESAMIENTO
    @Override
    public Boolean ProceedPaymentOrder(PaymentOrder paymentOrder, String paymentId, String paymentLinkId)
            throws RazorpayException {

        System.out.println("🇨🇱 CHILE PAYMENT - ProceedPaymentOrder");
        System.out.println("   Order ID: " + paymentOrder.getId());
        System.out.println("   Payment ID: " + paymentId);
        System.out.println("   Status actual: " + paymentOrder.getStatus());

        if (paymentOrder.getStatus() != PaymentOrderStatus.PENDING) {
            System.out.println("⚠️ Orden ya procesada");
            return false;
        }

        try {
            // 🇨🇱 SIMULAR PAGO EXITOSO CHILENO
            System.out.println("✅ Simulando pago exitoso chileno...");

            // Enviar notificaciones
            notificationEventProducer.sentNotificationEvent(
                    paymentOrder.getBookingId(),
                    paymentOrder.getUserId(),
                    paymentOrder.getSalonId());

            // Actualizar booking
            bookingEventProducer.sentBookingUpdateEvent(paymentOrder);

            // Marcar como exitoso
            paymentOrder.setStatus(PaymentOrderStatus.SUCCESS);
            paymentOrderRepository.save(paymentOrder);

            System.out.println("✅ Pago chileno procesado exitosamente");
            return true;

        } catch (Exception e) {
            System.err.println("❌ Error procesando pago chileno: " + e.getMessage());
            paymentOrder.setStatus(PaymentOrderStatus.FAILED);
            paymentOrderRepository.save(paymentOrder);
            return false;
        }
    }

    /* ───────────────────────── RAZORPAY ───────────────────────── */
    @Override
    public PaymentLink createRazorpayPaymentLink(UserDTO user, Long amountMajorUnits, Long orderId)
            throws RazorpayException {

        // 🎭 MOCK SIMPLE PARA COMPATIBILIDAD
        JSONObject mockResponse = new JSONObject();
        mockResponse.put("id", "mock_razorpay_" + orderId);
        mockResponse.put("short_url", "http://localhost:3000/payment-success/" + orderId);

        return new PaymentLink(mockResponse);
    }

    /* ───────────────────────── STRIPE ───────────────────────── */
    @Override
    public String createStripePaymentLink(UserDTO user, Long amountMajorUnits, Long orderId)
            throws StripeException {

        // 🎭 MOCK SIMPLE PARA COMPATIBILIDAD
        return "http://localhost:3000/payment-success/" + orderId;
    }

    @Override
    public Boolean proceedChileanPayment(String paymentId, String paymentLinkId) throws Exception {
        PaymentOrder paymentOrder = getPaymentOrderByPaymentId(paymentLinkId);
        return ProceedPaymentOrder(paymentOrder, paymentId, paymentLinkId);
    }
}