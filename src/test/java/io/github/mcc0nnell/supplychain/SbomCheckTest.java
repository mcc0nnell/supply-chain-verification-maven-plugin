package io.github.mcc0nnell.supplychain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.net.URI;
import org.junit.jupiter.api.Test;

class SbomCheckTest {
    @Test void emitsCycloneDxCandidate() {
        var c = new Coordinate("org.example", "demo", "1.2.3", Coordinate.Kind.DEPENDENCY);
        var e = new SbomCheck(URI.create("https://repo.maven.apache.org/maven2")).inspect(c);
        assertEquals(Evidence.Status.UNKNOWN, e.status());
        assertEquals("https://repo.maven.apache.org/maven2/org/example/demo/1.2.3/demo-1.2.3-cyclonedx.json", e.locations().get(0));
    }
}
