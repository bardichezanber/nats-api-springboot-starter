package com.example.ingest.namespace.policies;

import com.example.ingest.namespace.CommonEnvelope;
import com.example.ingest.namespace.NamespacePolicy;
import com.example.ingest.namespace.SourceKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Golden-file contract harness. Each namespace owns exactly the files under
 * {@code src/test/resources/golden/<namespace>/} — changing one namespace's
 * behavior can only ever touch that namespace's directory, which makes
 * "namespace B was not affected" a property the diff proves.
 *
 * <pre>
 *   [case].input.json + [case].expected.json  -> parse() must produce expected
 *   [case].invalid.json                       -> parse() must throw IllegalArgumentException
 * </pre>
 */
class NamespaceGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void everyPolicyHasAGoldenDirectoryAndEveryGoldenDirectoryHasAPolicy() throws IOException {
        assertThat(goldenCasesByNamespace().keySet())
                .containsExactlyInAnyOrderElementsOf(policiesByKey().keySet());
    }

    @TestFactory
    List<DynamicTest> goldenContracts() throws IOException {
        Map<String, NamespacePolicy> policies = policiesByKey();
        List<DynamicTest> tests = new ArrayList<>();
        goldenCasesByNamespace().forEach((namespace, cases) -> {
            NamespacePolicy policy = policies.get(namespace);
            if (policy == null) {
                throw new IllegalStateException("golden directory '" + namespace + "' has no NamespacePolicy");
            }
            cases.forEach((caseName, files) -> {
                if (files.containsKey("invalid")) {
                    tests.add(DynamicTest.dynamicTest(namespace + "/" + caseName + " (invalid)", () ->
                            assertThatThrownBy(() -> policy.parse(readEnvelope(files.get("invalid"))))
                                    .isInstanceOf(IllegalArgumentException.class)));
                } else {
                    tests.add(DynamicTest.dynamicTest(namespace + "/" + caseName, () ->
                            assertThat(policy.parse(readEnvelope(files.get("input"))))
                                    .isEqualTo(read(files.get("expected")))));
                }
            });
        });
        assertThat(tests).isNotEmpty();
        return tests;
    }

    /** namespace -> case -> kind(input|expected|invalid) -> file */
    private static Map<String, Map<String, Map<String, Resource>>> goldenCasesByNamespace() throws IOException {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:golden/*/*.json");
        Map<String, Map<String, Map<String, Resource>>> result = new TreeMap<>();
        for (Resource resource : resources) {
            String[] path = resource.getURI().toString().split("/");
            String namespace = path[path.length - 2];
            String[] fileName = path[path.length - 1].split("\\.");
            if (fileName.length != 3) {
                throw new IllegalStateException(
                        "golden file must be named <case>.<input|expected|invalid>.json: " + resource);
            }
            result.computeIfAbsent(namespace, k -> new TreeMap<>())
                    .computeIfAbsent(fileName[0], k -> new TreeMap<>())
                    .put(fileName[1], resource);
        }
        result.forEach((namespace, cases) -> cases.forEach((caseName, kinds) -> {
            if (kinds.containsKey("input") != kinds.containsKey("expected")) {
                throw new IllegalStateException("golden case " + namespace + "/" + caseName
                        + " needs both .input.json and .expected.json");
            }
        }));
        return result;
    }

    private static Map<String, NamespacePolicy> policiesByKey() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.scan("com.example.ingest.namespace.policies");
            context.refresh();
            return context.getBeansOfType(NamespacePolicy.class).values().stream()
                    .collect(Collectors.toUnmodifiableMap(NamespacePolicy::key, Function.identity()));
        }
    }

    private static CommonEnvelope readEnvelope(Resource resource) throws IOException {
        JsonNode node = read(resource);
        return new CommonEnvelope(
                SourceKey.valueOf(node.get("source").asText()),
                node.get("eventType").asText(),
                node.get("dedupKey").asText(),
                Instant.parse(node.get("occurredAt").asText()),
                node.get("body"));
    }

    private static JsonNode read(Resource resource) throws IOException {
        try (InputStream in = resource.getInputStream()) {
            return MAPPER.readTree(in);
        }
    }
}
