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
    public PaymentLinkResponse createOrder(UserDTO user,
            BookingDTO booking,
            PaymentMethod paymentMethod)
            throws RazorpayException, UserException, StripeException {

        // Total price como BigDecimal (2 decimales)
        BigDecimal total = booking.getTotalPrice().setScale(2, RoundingMode.HALF_UP);

        PaymentOrder order = new PaymentOrder();
        order.setUserId(user.getId());
        order.setAmount(total); // ahora BigDecimal en la entidad
        order.setBookingId(booking.getId());
        order.setSalonId(booking.getSalonId());
        order.setPaymentMethod(paymentMethod);

        PaymentOrder saved = paymentOrderRepository.save(order);
        PaymentLinkResponse res = new PaymentLinkResponse();

        Long majorUnits = total.longValueExact(); // 25 000.50 → 25000 (CLP) / 120.75 → 120 (USD) si decimales

        if (paymentMethod == PaymentMethod.RAZORPAY) {
            PaymentLink link = createRazorpayPaymentLink(user, majorUnits, saved.getId());
            res.setPayment_link_url(link.get("short_url"));
            saved.setPaymentLinkId(link.get("id"));
            paymentOrderRepository.save(saved);
        } else { // STRIPE u otro
            String url = createStripePaymentLink(user, majorUnits, saved.getId());
            res.setPayment_link_url(url);
        }
        return res;
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
    @Override
    public Boolean ProceedPaymentOrder(PaymentOrder paymentOrder,
            String paymentId,
            String paymentLinkId) throws RazorpayException {

        if (paymentOrder.getStatus() != PaymentOrderStatus.PENDING)
            return false;

        if (paymentOrder.getPaymentMethod() == PaymentMethod.RAZORPAY) {
            RazorpayClient rz = new RazorpayClient(apiKey, apiSecret);
            Payment pay = rz.payments.fetch(paymentId);

            if ("captured".equals(pay.get("status"))) {
                notificationEventProducer.sentNotificationEvent(
                        paymentOrder.getBookingId(),
                        paymentOrder.getUserId(),
                        paymentOrder.getSalonId());

                bookingEventProducer.sentBookingUpdateEvent(paymentOrder);
                paymentOrder.setStatus(PaymentOrderStatus.SUCCESS);
                paymentOrderRepository.save(paymentOrder);
                return true;
            }
            paymentOrder.setStatus(PaymentOrderStatus.FAILED);
            paymentOrderRepository.save(paymentOrder);
            return false;
        }

        // Stripe confirmado vía webhook
        paymentOrder.setStatus(PaymentOrderStatus.SUCCESS);
        paymentOrderRepository.save(paymentOrder);
        return true;
    }

    /* ───────────────────────── RAZORPAY ───────────────────────── */
    @Override
    public PaymentLink createRazorpayPaymentLink(UserDTO user,
            Long amountMajorUnits,
            Long orderId) throws RazorpayException {

        long paise = amountMajorUnits * 100; // INR → paise

        RazorpayClient razorpay = new RazorpayClient(apiKey, apiSecret);

        JSONObject req = new JSONObject();
        req.put("amount", paise);
        req.put("currency", "INR");

        JSONObject customer = new JSONObject();
        customer.put("name", user.getFullName());
        customer.put("email", user.getEmail());
        req.put("customer", customer);

        JSONObject notify = new JSONObject();
        notify.put("email", true);
        req.put("notify", notify);

        req.put("reminder_enable", true);
        req.put("callback_url", "http://localhost:3000/payment-success/" + orderId);
        req.put("callback_method", "get");

        return razorpay.paymentLink.create(req);
    }

    /* ───────────────────────── STRIPE ───────────────────────── */
    @Override
    public String createStripePaymentLink(UserDTO user,
            Long amountMajorUnits,
            Long orderId) throws StripeException {
        Stripe.apiKey = stripeSecretKey;

        long cents = amountMajorUnits * 100; // USD → cents

        SessionCreateParams params = SessionCreateParams.builder()
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl("http://localhost:3000/payment-success/" + orderId)
                .setCancelUrl("http://localhost:3000/payment/cancel")
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("usd")
                                .setUnitAmount(cents)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName("Top up wallet")
                                        .build())
                                .build())
                        .build())
                .build();

        return Session.create(params).getUrl();
    }
}