package io.github.prakharr0.ai.rca.tests.eval;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Loads {@link EvalFixture} instances from {@code classpath:fixtures/*.json}.
 *
 * <p>Uses Spring's {@link PathMatchingResourcePatternResolver} which works without a Spring
 * context — safe to call from JUnit {@code @MethodSource} static methods.
 *
 * <p>Add a new JSON file to {@code src/test/resources/fixtures/} and it is automatically
 * picked up by all eval tests on the next run — no code changes required.
 */
public class FixtureLoader {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FixtureLoader() {}

    /**
     * Returns all fixtures as a {@link Stream} for use with {@code @MethodSource}.
     * Each element is the fixture's {@code id} and the fixture itself.
     */
    public static Stream<org.junit.jupiter.params.provider.Arguments> fixtureArguments() {
        return loadAll().stream()
                .map(f -> org.junit.jupiter.params.provider.Arguments.of(f.id(), f));
    }

    /**
     * Returns all fixtures as a list.
     */
    public static List<EvalFixture> loadAll() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath:fixtures/*.json");
            List<EvalFixture> fixtures = new ArrayList<>();
            for (Resource resource : resources) {
                EvalFixture fixture = MAPPER.readValue(resource.getInputStream(), EvalFixture.class);
                fixtures.add(fixture);
            }
            return fixtures;
        } catch (Exception e) {
            throw new RuntimeException("Failed to load eval fixtures from classpath:fixtures/", e);
        }
    }
}