package com.ffl.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calls airLowerPriceShop the same way the official mobile site does when the
 * device-fingerprint script is absent: fixed web client fields, HMAC-SHA1 sign,
 * and the sens field the site always attaches. No proxy, challenge solver, or
 * forged fingerprint.
 */
@Component
@Slf4j
public class HnairClient {

    private static final String SHOP_URL = "https://app.hnair.com/ticket/lfs/airLowerPriceShop";
    private static final String AKEY = "9E4BBDDEC6C8416EA380E418161A7CD3";
    private static final String HARD_CODE = "21047C596EAD45209346AE29F0350491";
    private static final String CERTIFICATE_HASH = "6093941774D84495A5D15D8F909CAA1E";
    private static final String DS_KEY = "m1FP4IS3FbZ7cx5phkMIxSdmF1hNNR6N";
    private static final String SNAME = "MacIntel";
    private static final String SVER = "5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public ShopResult search(String origin, String destination, LocalDate date) {
        long stime = System.currentTimeMillis();
        String sens = "%hna%" + encrypt(stime + ",0,0", iv(stime));
        Map<String, String> signed = signedFields(stime, sens);
        Map<String, String> headers = new TreeMap<>();
        headers.put("hna-app", "APP");
        headers.put("hna-channel", "HTML5");
        String sign = sign(headers, signed);
        String body = body(stime, sens, origin, destination, date.toString());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SHOP_URL + "?hnairSign=" + sign))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .header("Accept", "application/json")
                    .header("appver", "10.19.0")
                    .header("hna-app", "APP")
                    .header("hna-channel", "HTML5")
                    .header("Origin", "https://m.hnair.com")
                    .header("Referer", "https://m.hnair.com/")
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Shop request failed with http status {}", response.statusCode());
                return ShopResult.failed("HTTP " + response.statusCode(), false);
            }
            return parse(response.body());
        } catch (Exception e) {
            log.warn("Shop request error: {}", e.getMessage());
            return ShopResult.failed(e.getMessage() == null ? "request failed" : e.getMessage(), false);
        }
    }

    private ShopResult parse(String raw) throws Exception {
        JsonNode root = mapper.readTree(raw);
        JsonNode data = root.path("data");
        String errorCode = text(root, data, "errorCode");
        String errorMessage = text(root, data, "errorMessage");
        boolean flagged = !errorCode.isBlank() && !"0".equals(errorCode) && !"0000".equals(errorCode);
        boolean success = !flagged && (root.path("success").asBoolean(false) || data.path("success").asBoolean(false));
        if (!success) {
            boolean limited = "E000163".equals(errorCode) || errorMessage.contains("访问受限");
            log.info("Shop rejected code={} limited={}", errorCode, limited);
            String message = !errorMessage.isBlank() ? errorMessage : errorCode;
            return ShopResult.failed(message.isBlank() ? "query failed" : message, limited);
        }
        JsonNode groups = data.path("originDestinations");
        if (!groups.isArray() || groups.isEmpty()) {
            return ShopResult.ok(List.of());
        }
        JsonNode flights = groups.get(0).path("airItineraries");
        List<FlightQuote> quotes = new ArrayList<>();
        if (flights.isArray()) {
            for (JsonNode item : flights) {
                quotes.add(toQuote(item));
            }
        }
        return ShopResult.ok(quotes);
    }

    private FlightQuote toQuote(JsonNode item) {
        JsonNode segment = item.path("flightSegments").isArray() && !item.path("flightSegments").isEmpty()
                ? item.path("flightSegments").get(0)
                : mapper.createObjectNode();
        String airline = first(segment, "marketingAirlineCode", "operatingAirlineCode");
        String number = first(segment, "flightNumber", "flightNo");
        if (number.isBlank()) {
            number = first(item, "flightNumber", "flightNo");
        }
        String flightNo = number.startsWith(airline) || airline.isBlank() ? number : airline + number;
        String price = first(item, "lowPriceY", "minLowPrice");
        String inventory = first(item, "inventoryQuantityY", "inventoryQuantity");
        return new FlightQuote(
                flightNo,
                first(segment, "departureTime", "depTime"),
                first(segment, "arrivalTime", "arrTime"),
                price,
                inventory
        );
    }

    private static String text(JsonNode root, JsonNode data, String name) {
        String value = root.path(name).asText("");
        return value.isBlank() ? data.path(name).asText("") : value;
    }

    private static String first(JsonNode node, String... names) {
        for (String name : names) {
            String value = node.path(name).asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private Map<String, String> signedFields(long stime, String sens) {
        Map<String, String> signed = new TreeMap<>();
        signed.put("sname", SNAME);
        signed.put("sver", SVER);
        signed.put("schannel", "HTML5");
        signed.put("caller", "HTML5");
        signed.put("slang", "zh-CN");
        signed.put("did", "defualt_web_did");
        signed.put("stime", Long.toString(stime));
        signed.put("szone", "-480");
        signed.put("aname", "com.hnair.spa.web.standard");
        signed.put("aver", "10.19.0");
        signed.put("akey", AKEY);
        signed.put("abuild", "65702");
        signed.put("atarget", "standard");
        signed.put("slat", "slat");
        signed.put("slng", "slng");
        signed.put("gtcid", "defualt_web_gtcid");
        signed.put("riskToken", "");
        signed.put("captchaToken", "");
        signed.put("blackBox", "");
        signed.put("validateToken", "");
        signed.put("sens", sens);
        signed.put("passenger", "ADT:1");
        signed.put("_referer", "/book/query/start");
        return signed;
    }

    private String body(long stime, String sens, String origin, String destination, String date) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode common = root.putObject("common");
        common.put("sname", SNAME);
        common.put("sver", SVER);
        common.put("schannel", "HTML5");
        common.put("caller", "HTML5");
        common.put("slang", "zh-CN");
        common.put("did", "defualt_web_did");
        common.put("stime", stime);
        common.put("szone", -480);
        common.put("aname", "com.hnair.spa.web.standard");
        common.put("aver", "10.19.0");
        common.put("akey", AKEY);
        common.put("abuild", "65702");
        common.put("atarget", "standard");
        common.put("slat", "slat");
        common.put("slng", "slng");
        common.put("gtcid", "defualt_web_gtcid");
        common.put("riskToken", "");
        common.put("captchaToken", "");
        common.put("blackBox", "");
        common.put("validateToken", "");
        common.put("sens", sens);
        ObjectNode data = root.putObject("data");
        ArrayNode ods = data.putArray("originDestinations");
        ObjectNode od = ods.addObject();
        od.put("departureDate", date);
        od.put("destination", destination);
        od.put("origin", origin);
        od.put("destinationType", "1");
        od.put("originType", "1");
        data.put("passenger", "ADT:1");
        data.put("_referer", "/book/query/start");
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String sign(Map<String, String> headers, Map<String, String> fields) {
        StringBuilder raw = new StringBuilder();
        headers.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("hna"))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> raw.append(entry.getValue() == null ? "" : entry.getValue()));
        fields.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> raw.append(entry.getValue() == null ? "" : entry.getValue()));
        raw.append(CERTIFICATE_HASH);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(HARD_CODE.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] digest = mac.doFinal(raw.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02X", value));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String iv(long stime) {
        return (stime + AKEY).substring(0, 16);
    }

    private static String encrypt(String plain, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(DS_KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8))
            );
            return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public record FlightQuote(String flightNo, String depTime, String arrTime, String price, String inventory) {
    }

    public record ShopResult(boolean success, boolean riskLimited, String errorMessage, List<FlightQuote> quotes) {
        public static ShopResult ok(List<FlightQuote> quotes) {
            return new ShopResult(true, false, "", quotes);
        }

        public static ShopResult failed(String message, boolean riskLimited) {
            return new ShopResult(false, riskLimited, message == null ? "" : message, List.of());
        }
    }
}
