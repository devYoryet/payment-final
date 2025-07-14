// src/main/java/com/zosh/service/impl/PaymentServiceImpl.java
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
import java.util.List;

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
            order.setAmount(total); // ✅ Ya es BigDecimal
            order.setBookingId(booking.getId());
            order.setSalonId(booking.getSalonId());
            order.setPaymentMethod(paymentMethod);
            order.setStatus(PaymentOrderStatus.PENDING); // ✅ Estado inicial

            PaymentOrder saved = paymentOrderRepository.save(order);
            System.out.println("✅ PaymentOrder guardado con ID: " + saved.getId());

            PaymentLinkResponse res = new PaymentLinkResponse();

            // 🇨🇱 CREAR MOCK PAYMENT LINK CHILENO
            String mockPaymentUrl = createChileanMockPaymentLink(user, total, saved.getId(), paymentMethod);

            // ✅ FORMATO CONSISTENTE para paymentLinkId
            String mockPaymentId = "CHILE_PAY_" + saved.getId() + "_" + System.currentTimeMillis();

            res.setPayment_link_url(mockPaymentUrl);
            res.setPayment_link_id(mockPaymentId);

            // ✅ GUARDAR EL paymentLinkId en la BD
            saved.setPaymentLinkId(mockPaymentId);
            paymentOrderRepository.save(saved);

            System.out.println("✅ Mock Payment Link Chileno creado:");
            System.out.println("   URL: " + mockPaymentUrl);
            System.out.println("   PaymentLinkId: " + mockPaymentId);

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
        System.out.println("   Usuario: " + user.getEmail());
        System.out.println("   Monto: " + amount);
        System.out.println("   Orden: " + orderId);
        System.out.println("   Método: " + method);

        // Determinar el proveedor según el método
        String provider = determineChileanProvider(method);

        // ✅ USAR URL DE FRONTEND CORRECTA (VERCEL)
        String baseUrl = "https://front-final-nine.vercel.app/payment/chile-mock";

        String params = String.format(
                "?orderId=%d&amount=%.2f&provider=%s&email=%s&name=%s",
                orderId,
                amount,
                provider,
                user.getEmail(),
                user.getFullName() != null ? user.getFullName() : user.getEmail());

        String fullUrl = baseUrl + params;
        System.out.println("🔗 URL generada: " + fullUrl);

        return fullUrl;
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

    // ✅ MÉTODO MEJORADO CON BÚSQUEDA INTELIGENTE
    @Override
    public PaymentOrder getPaymentOrderByPaymentId(String paymentLinkId) throws Exception {
        System.out.println("🔍 Buscando PaymentOrder con paymentLinkId: " + paymentLinkId);

        // 🚀 PRIMERA BÚSQUEDA: Buscar exactamente como viene
        PaymentOrder po = paymentOrderRepository.findByPaymentLinkId(paymentLinkId);

        if (po != null) {
            System.out.println("✅ PaymentOrder encontrado (búsqueda exacta): " + po.getId());
            return po;
        }

        // 🇨🇱 SEGUNDA BÚSQUEDA: Si es pago chileno con formato "chile_XX"
        if (paymentLinkId.startsWith("chile_")) {
            System.out.println("🇨🇱 Detectado formato chile_, buscando por orderId...");

            try {
                // Extraer orderId de "chile_65" -> 65
                String orderIdStr = paymentLinkId.replace("chile_", "");
                Long orderId = Long.parseLong(orderIdStr);

                System.out.println("📝 Buscando PaymentOrder por orderId: " + orderId);
                po = paymentOrderRepository.findById(orderId).orElse(null);

                if (po != null) {
                    System.out.println("✅ PaymentOrder encontrado por orderId: " + po.getId());
                    System.out.println("   PaymentLinkId en BD: " + po.getPaymentLinkId());
                    return po;
                }

            } catch (NumberFormatException e) {
                System.out.println("⚠️ No se pudo extraer orderId de: " + paymentLinkId);
            }
        }

        // 🚀 TERCERA BÚSQUEDA: Buscar todos los PaymentOrders y hacer debug
        System.out.println("🔄 Búsqueda de emergencia - listando todos los PaymentOrders...");
        List<PaymentOrder> allOrders = paymentOrderRepository.findAll();

        System.out.println("📋 PaymentOrders en BD (" + allOrders.size() + " total):");
        for (PaymentOrder order : allOrders) {
            System.out.println("   - ID: " + order.getId() +
                    ", PaymentLinkId: '" + order.getPaymentLinkId() +
                    "', Status: " + order.getStatus());

            // Buscar por similitud
            if (order.getPaymentLinkId() != null &&
                    order.getPaymentLinkId().contains(paymentLinkId.replace("chile_", ""))) {
                System.out.println("✅ Encontrado por similitud!");
                return order;
            }
        }

        System.err.println("❌ PaymentOrder NO encontrado con paymentLinkId: " + paymentLinkId);
        throw new Exception("Payment order not found with id " + paymentLinkId);
    }

    /* ───────────────────────── CONFIRM / CAPTURE ───────────────────────── */
    @Override
    public Boolean ProceedPaymentOrder(PaymentOrder paymentOrder, String paymentId, String paymentLinkId)
            throws RazorpayException {

        System.out.println("🇨🇱 CHILE PAYMENT - ProceedPaymentOrder");
        System.out.println("   Order ID: " + paymentOrder.getId());
        System.out.println("   Payment ID: " + paymentId);
        System.out.println("   PaymentLinkId: " + paymentLinkId);
        System.out.println("   Status actual: " + paymentOrder.getStatus());

        if (paymentOrder.getStatus() != PaymentOrderStatus.PENDING) {
            System.out.println("⚠️ Orden ya procesada con status: " + paymentOrder.getStatus());
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
        mockResponse.put("short_url", "https://front-final-nine.vercel.app/payment-success/" + orderId);

        return new PaymentLink(mockResponse);
    }

    /* ───────────────────────── STRIPE ───────────────────────── */
    @Override
    public String createStripePaymentLink(UserDTO user, Long amountMajorUnits, Long orderId)
            throws StripeException {

        // 🎭 MOCK SIMPLE PARA COMPATIBILIDAD
        return "https://front-final-nine.vercel.app/payment-success/" + orderId;
    }

    @Override
    public Boolean proceedChileanPayment(String paymentId, String paymentLinkId) throws Exception {
        PaymentOrder paymentOrder = getPaymentOrderByPaymentId(paymentLinkId);
        return ProceedPaymentOrder(paymentOrder, paymentId, paymentLinkId);
    }
}