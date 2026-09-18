package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonFactory;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VerifyMojoJsonTest {
    @Test
    void ndjsonEscapesControlCharactersFromUntrustedMetadata() throws Exception {
        var component = new ResolvedComponent(
            "org.example", "demo", "1", "1",
            "jar", null, "jar", ResolvedComponent.Kind.DEPENDENCY,
            URI.create("https://repo.example.test/maven2"), "example", "abc123");
        var evidence = new Evidence(
            "test",
            Evidence.Status.UNKNOWN,
            "line one\nline two\ttab",
            List.of("scm-value\nsecond-line"),
            Map.of("key", "value\rcontrol"));

        String json = VerifyMojo.toJson(component, evidence);

        assertEquals(1, json.lines().count());
        assertFalse(json.contains("scm-value\nsecond-line"));
        assertTrue(json.contains("scm-value\\nsecond-line"));
        assertTrue(json.contains("line one\\nline two\\ttab"));

        try (var parser = new JsonFactory().createParser(json)) {
            while (parser.nextToken() != null) {
                // A complete parse without exception is the assertion.
            }
        }
    }
}
