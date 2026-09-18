package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class VerifyMojoTest {
    @Test
    void escapesNdjsonControlCharacters() {
        assertEquals(
            "quote\\\" slash\\\\ newline\\n tab\\t nul\\u0000",
            VerifyMojo.esc("quote\" slash\\ newline\n tab\t nul\u0000"));
    }

    @Test
    void escapesJsonArrays() {
        assertEquals(
            "[\"a\\nb\",\"c\\\"d\"]",
            VerifyMojo.jsonArray(List.of("a\nb", "c\"d")));
    }
}
