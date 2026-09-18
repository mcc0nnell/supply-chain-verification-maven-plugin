package io.github.mcc0nnell.supplychain;

import java.util.List;
import java.util.Map;

record Evidence(
    String check,
    Status status,
    String summary,
    List<String> locations,
    Map<String, String> attributes) {

    Evidence(String check, Status status, String summary, List<String> locations) {
        this(check, status, summary, locations, Map.of());
    }

    enum Status { PASS, WARN, FAIL, UNKNOWN }
}
