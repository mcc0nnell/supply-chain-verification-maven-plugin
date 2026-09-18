package io.github.mcc0nnell.supplychain;

import java.util.List;
record Evidence(String check, Status status, String summary, List<String> locations) {
    enum Status { PASS, WARN, FAIL, UNKNOWN }
}
