package com.zosh.controller;

import com.razorpay.RazorpayException;
import com.stripe.exception.StripeException;
import com.zosh.domain.PaymentMethod;
import com.zosh.exception.UserException;
import com.zosh.modal.PaymentOrder;
import com.zosh.payload.dto.BookingDTO;
import com.zosh.payload.dto.UserDTO;
import com.zosh.payload.response.ApiResponse;
import com.zosh.payload.response.PaymentLinkResponse;
import com.zosh.service.PaymentService;
import com.zosh.service.clients.UserFeignClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments") // También faltaba esta anotación
@RequiredArgsConstructor
public class PaymentController {

        private final PaymentService paymentService;
        private final UserFeignClient userService;

        @PostMapping("/create")
        public ResponseEntity<PaymentLinkResponse> createPaymentLink(
                        @RequestHeader("Authorization") String jwt,
                        @RequestBody BookingDTO booking,
                        @RequestParam PaymentMethod paymentMethod) throws UserException,
                        RazorpayException, StripeException {

                System.out.println("------" + booking);

                UserDTO user = userService.getUserFromJwtToken(jwt).getBody();

                PaymentLinkResponse paymentLinkResponse = paymentService
                                .createOrder(user, booking, paymentMethod);

                return ResponseEntity.ok(paymentLinkResponse);
        }

        @GetMapping("/{paymentOrderId}")
        public ResponseEntity<PaymentOrder> getPaymentOrderById(
                        @PathVariable Long paymentOrderId) {
                try {
                        PaymentOrder paymentOrder = paymentService.getPaymentOrderById(paymentOrderId);
                        return ResponseEntity.ok(paymentOrder);
                } catch (Exception e) {
                        return ResponseEntity.notFound().build();
                }
        }

        // Payment Controller endpoint para procesar pagos
        @PatchMapping("/proceed")
        public ResponseEntity<ApiResponse> proceedPayment(
                        @RequestParam String paymentId,
                        @RequestParam String paymentLinkId,
                        @RequestHeader("Authorization") String jwt) throws Exception {

                System.out.println("🔄 PROCEEDING PAYMENT:");
                System.out.println("   PaymentId: " + paymentId);
                System.out.println("   PaymentLinkId: " + paymentLinkId);

                try {
                        PaymentOrder paymentOrder = paymentService.getPaymentOrderByPaymentId(paymentLinkId);
                        Boolean success = paymentService.ProceedPaymentOrder(paymentOrder, paymentId, paymentLinkId);

                        if (success) {
                                System.out.println("✅ Pago procesado exitosamente");
                                return ResponseEntity.ok(new ApiResponse("Pago procesado exitosamente"));
                        } else {
                                System.out.println("❌ Error procesando pago");
                                return ResponseEntity.badRequest().body(new ApiResponse("Error procesando pago"));
                        }

                } catch (Exception e) {
                        System.err.println("❌ Exception en proceedPayment: " + e.getMessage());
                        return ResponseEntity.status(500).body(new ApiResponse("Error interno: " + e.getMessage()));
                }
        }

}
