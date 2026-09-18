import groovy.json.JsonSlurper
import java.security.MessageDigest

def report = new File(basedir, "target/evidence.ndjson")
assert report.isFile()
def rows = report.readLines("UTF-8").findAll { !it.isBlank() }.collect {
    new JsonSlurper().parseText(it)
}

def fixture = rows.find {
    it.component.gav == "org.example:evidence-fixture:1.0.0" &&
        it.check == "public-sbom-sidecar"
}
assert fixture != null
assert fixture.status == "PASS"
assert fixture.component.artifactRepositoryId == "fixture-repository"
assert fixture.component.pomRepositoryId == "fixture-repository"
assert fixture.component.artifactRepositoryUrl.startsWith("file:")
assert fixture.component.pomRepositoryUrl.startsWith("file:")
assert fixture.component.pomSha256 != null
assert fixture.component.sha256 == sha256(
    new File(basedir, "repo/org/example/evidence-fixture/1.0.0/evidence-fixture-1.0.0.jar"))
assert fixture.locations.size() == 1
assert fixture.locations[0].contains("/repo/org/example/evidence-fixture/1.0.0/")
assert !fixture.locations[0].contains("repo.maven.apache.org")

def absent = rows.find {
    it.component.gav == "org.example:no-sidecar:1.0.0" &&
        it.check == "public-sbom-sidecar"
}
assert absent != null
assert absent.status == "FAIL"
assert absent.component.artifactRepositoryId == "fixture-repository"
assert absent.component.pomRepositoryId == "fixture-repository"
assert absent.locations.every { it.startsWith("file:") }
assert absent.locations.every { !it.contains("repo.maven.apache.org") }


def splitSbom = rows.find {
    it.component.gav == "org.example:split-provenance:1.0.0" &&
        it.check == "public-sbom-sidecar"
}
assert splitSbom != null
assert splitSbom.component.artifactRepositoryId == "fixture-jar-repository"
assert splitSbom.component.pomRepositoryId == "fixture-pom-repository"
assert splitSbom.status == "FAIL"
assert splitSbom.locations.every { it.contains("/repo-jar/") }

def splitScorecard = rows.find {
    it.component.gav == "org.example:split-provenance:1.0.0" &&
        it.check == "openssf-scorecard-current"
}
assert splitScorecard != null
assert splitScorecard.status == "UNKNOWN"
assert splitScorecard.summary.contains("different repositories")
assert splitScorecard.locations.contains("artifact-repository:fixture-jar-repository")
assert splitScorecard.locations.contains("pom-repository:fixture-pom-repository")
assert splitScorecard.locations.every { !it.contains("api.securityscorecards.dev") }

def scm = rows.find {
    it.component.gav == "org.example:evidence-fixture:1.0.0" &&
        it.check == "openssf-scorecard-current"
}
assert scm != null
assert scm.status == "UNKNOWN"
assert scm.summary.contains("does not declare SCM metadata")

static String sha256(File file) {
    def digest = MessageDigest.getInstance("SHA-256")
    file.withInputStream { input ->
        byte[] buffer = new byte[8192]
        for (int n = input.read(buffer); n >= 0; n = input.read(buffer)) {
            if (n > 0) digest.update(buffer, 0, n)
        }
    }
    digest.digest().encodeHex().toString()
}
