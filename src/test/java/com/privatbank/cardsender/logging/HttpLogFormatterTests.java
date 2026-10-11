package com.privatbank.cardsender.logging;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HttpLogFormatterTests {
    private final HttpLogFormatter formatter = new HttpLogFormatter();

    @Test
    void redactsNestedFieldsRegardlessOfCaseOrSeparator() {
        String body = """
                {"data":[{"CARD_NUMBER":"4444440000000008","first-name":"Dmytro",
                "apiKey":"secret","LAST_NAME":"Test","status":"SENT"}]}
                """;
        assertThat(formatter.body(body.getBytes(StandardCharsets.UTF_8), "application/json"))
                .contains("SENT", "[MASKED]", "4444********0008", "Dmytro", "Test")
                .doesNotContain("4444440000000008", "secret");
    }

    @Test
    void masksCardNumbersInBodiesAndPathsWhileKeepingOtherMasking() {
        String body = """
                {"cardNumber":"4444443396185017","CARD_NUMBER":"0000003396185017",
                 "card-number":"invalid","phone":"380671234567","token":"secret"}
                """;
        assertThat(formatter.body(body.getBytes(StandardCharsets.UTF_8), "application/json"))
                .isEqualTo("{\"cardNumber\":\"4444********5017\",\"CARD_NUMBER\":\"0000********5017\","
                           + "\"card-number\":\"[MASKED]\",\"phone\":\"380671234567\",\"token\":\"[MASKED]\"}");
        assertThat(formatter.path("/api/orders/status/4444443396185017"))
                .isEqualTo("/api/orders/status/4444********5017");
    }

    @Test
    void preservesWaybillsIncludingLeadingZerosWhileMaskingOtherLongNumbers() {
        String body = """
                {"CARD_NUMBER":"6666660000007594","WAYBILL":"00012345678901",
                 "data":[{"waybill":"0012345678901234","phone":"380671234567"}],
                 "other":"6666660000007594","token":"secret"}
                """;
        assertThat(formatter.body(body.getBytes(StandardCharsets.UTF_8), "application/json"))
                .contains("\"WAYBILL\":\"00012345678901\"", "\"waybill\":\"0012345678901234\"",
                        "\"CARD_NUMBER\":\"6666********7594\"", "\"phone\":\"380671234567\"", "\"token\":\"[MASKED]\"")
                .doesNotContain("6666660000007594", "secret");
    }

    @Test
    void omitsMalformedNonJsonAndOversizedBodiesInsteadOfLoggingRawData() {
        byte[] malformed = "{\"password\":\"secret".getBytes(StandardCharsets.UTF_8);
        assertThat(formatter.body(malformed, "application/json")).isEqualTo("[unreadable JSON omitted]");
        assertThat(formatter.body(malformed, "text/plain")).isEqualTo("[non-JSON body omitted]");
        assertThat(formatter.body(new byte[HttpLogFormatter.MAX_BODY_BYTES], "application/json"))
                .isEqualTo("[body omitted: size limit]");
    }
}
