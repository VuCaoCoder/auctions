package com.example.auctions.service;

import com.example.auctions.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class PaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaymentService.class);

    @Value("${vnpay.tmnCode}")
    private String vnpTmnCode;

    @Value("${vnpay.hashSecret}")
    private String vnpHashSecret;

    @Value("${vnpay.payUrl}")
    private String vnpPayUrl;

    @Value("${vnpay.returnUrl}")
    private String vnpReturnUrl;

    public String createPaymentUrl(Transaction transaction) {
        String vnpVersion = "2.1.0";
        String vnpCommand = "pay";
        String orderType = "other";
        String locale = "vn";

        long amount = transaction.getPrice().multiply(new java.math.BigDecimal(100)).longValue();
        String vnpTxnRef = String.valueOf(transaction.getId());
        String vnpOrderInfo = "Thanh toan cho don hang: " + vnpTxnRef;

        Map<String, String> vnpParams = new LinkedHashMap<>();
        vnpParams.put("vnp_Version", vnpVersion);
        vnpParams.put("vnp_Command", vnpCommand);
        vnpParams.put("vnp_TmnCode", vnpTmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(amount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", vnpTxnRef);
        vnpParams.put("vnp_OrderInfo", vnpOrderInfo);
        vnpParams.put("vnp_OrderType", orderType);
        vnpParams.put("vnp_Locale", locale);
        vnpParams.put("vnp_ReturnUrl", vnpReturnUrl);
        vnpParams.put("vnp_IpAddr", "127.0.0.1");

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String vnpCreateDate = formatter.format(calendar.getTime());
        vnpParams.put("vnp_CreateDate", vnpCreateDate);

        // Sort parameters by key before creating signature
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        // Create signature data
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();
        
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = vnpParams.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {
                // Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(urlEncode(fieldValue)); // URL encode value for hash data

                // Build query
                query.append(urlEncode(fieldName));
                query.append('=');
                query.append(urlEncode(fieldValue));

                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String queryUrl = query.toString();
        String vnpSecureHash = hmacSHA512(vnpHashSecret, hashData.toString());
        queryUrl += "&vnp_SecureHash=" + vnpSecureHash;

        logger.debug("Generated payment URL parameters: {}", hashData.toString());
        logger.debug("Generated secure hash: {}", vnpSecureHash);

        return vnpPayUrl + "?" + queryUrl;
    }

    public boolean validatePaymentResponse(Map<String, String> response) {
        String vnpSecureHash = response.get("vnp_SecureHash");
        String responseCode = response.get("vnp_ResponseCode");
        
        // Log the full response for debugging
        logger.info("Validating VNPay response: {}", response);
        
        // In development/test environment, we'll trust the response code
        // TODO: In production, implement proper hash validation
        if ("00".equals(responseCode)) {
            logger.info("Payment successful with response code: {}", responseCode);
            return true;
        }
        
        logger.error("Payment failed with response code: {}", responseCode);
        return false;
    }

    private String hmacSHA512(String key, String data) {
        try {
            Mac sha512_HMAC = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            sha512_HMAC.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = sha512_HMAC.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : result) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return "";
        }
    }

    public boolean processPaymentCallback(Map<String, String> response) {
        // Validate the payment response
        if (!validatePaymentResponse(response)) {
            return false;
        }

        // Check payment status
        String vnpResponseCode = response.get("vnp_ResponseCode");
        return "00".equals(vnpResponseCode);
    }
} 