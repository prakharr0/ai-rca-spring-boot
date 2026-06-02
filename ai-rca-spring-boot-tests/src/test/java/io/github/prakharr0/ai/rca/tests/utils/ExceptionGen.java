package io.github.prakharr0.ai.rca.tests.utils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class ExceptionGen {

    public static void main(String[] args) throws IOException {
        try {
            new Orchestrator().start();
        } catch (Exception e) {
            System.out.println("===== FINAL STACK TRACE =====");
            e.printStackTrace();

            final var bos = new ByteArrayOutputStream();

            try (final var oos = new ObjectOutputStream(bos)) {
                oos.writeObject(e);
            }

            byte[] bytes = bos.toByteArray();

            try (final var fos = new FileOutputStream("throwable.txt")) {
                fos.write(bytes);
            }
        }
    }
}

class Orchestrator {

    public void start() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);

        Future<?> future = executor.submit(() -> {
            try {
                invokeProcessing();
            } catch (Exception e) {
                throw new RuntimeException("Async processing failed", e);
            }
        });

        try {
            future.get();
        } catch (ExecutionException e) {
            throw new PipelineException("Pipeline execution failure", e);
        } finally {
            executor.shutdown();
        }
    }

    private void invokeProcessing() throws Exception {
        Class<?> clazz = DataPipeline.class;
        Object instance = clazz.getDeclaredConstructor().newInstance();

        Method method = clazz.getDeclaredMethod("runPipeline", List.class);

        List<String> data = Arrays.asList("42", "17", "notANumber", "58");

        try {
            method.invoke(instance, data);
        } catch (Exception e) {
            throw new ReflectionLayerException("Reflection invocation failed", e);
        }
    }
}

class DataPipeline {

    public void runPipeline(List<String> data) {
        try {
            List<Integer> processed = data.stream()
                    .map(StageOne::transform)
                    .map(StageTwo::compute)
                    .collect(Collectors.toList());

            System.out.println(processed);
        } catch (Exception e) {
            RuntimeException wrapper = new RuntimeException("Pipeline transformation error");
            wrapper.addSuppressed(e);
            throw wrapper;
        }
    }
}

class StageOne {

    public static int transform(String value) {
        try {
            return HiddenParser.parse(value);
        } catch (Exception e) {
            throw new DataTransformException("StageOne failed for value: " + value, e);
        }
    }
}

class StageTwo {

    public static int compute(int input) {
        try {
            return ComplexMath.heavyCalculation(input);
        } catch (Exception e) {
            throw new ComputationException("StageTwo computation failed", e);
        }
    }
}

class HiddenParser {

    public static int parse(String value) {
        try {
            Optional<String> v = Optional.ofNullable(value)
                    .map(String::trim)
                    .filter(s -> !s.isEmpty());

            return v.map(Integer::parseInt)
                    .orElseThrow(() -> new IllegalArgumentException("Empty value"));
        } catch (NumberFormatException e) {
            throw new ParsingLayerException("Invalid numeric format detected", e);
        }
    }
}

class ComplexMath {

    public static int heavyCalculation(int x) {
        try {
            return recursiveCompute(x, 3);
        } catch (Exception e) {
            throw new RuntimeException("Math subsystem failure", e);
        }
    }

    private static int recursiveCompute(int x, int depth) {
        if (depth == 0) {
            return riskyOperation(x);
        }
        return recursiveCompute(x + depth, depth - 1);
    }

    private static int riskyOperation(int value) {
        Map<Integer, Integer> cache = new HashMap<>();
        cache.put(50, 2);

        Integer divisor = cache.get(value);

        return 100 / divisor; // <-- NullPointerException hidden here
    }
}

class PipelineException extends Exception {
    public PipelineException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

class ReflectionLayerException extends Exception {
    public ReflectionLayerException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

class ParsingLayerException extends RuntimeException {
    public ParsingLayerException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

class DataTransformException extends RuntimeException {
    public DataTransformException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

class ComputationException extends RuntimeException {
    public ComputationException(String msg, Throwable cause) {
        super(msg, cause);
    }
}