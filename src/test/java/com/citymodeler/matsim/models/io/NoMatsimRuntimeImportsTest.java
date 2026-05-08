package com.citymodeler.matsim.models.io;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ensures no source file imports MATSim runtime classes.
 * This library must remain free of MATSim binary dependencies.
 */
class NoMatsimRuntimeImportsTest {

    @Test
    void noMatsimRuntimeImportsInSourceFiles() throws IOException {
        Path sourceRoot = Paths.get("src", "main", "java");
        
        List<String> violations;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            violations = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .flatMap(p -> {
                    try {
                        return Files.lines(p);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(line -> line.trim().startsWith("import org.matsim"))
                .collect(Collectors.toList());
        }
        
        assertTrue(violations.isEmpty(), 
            "Found MATSim runtime imports in source files:\n" + String.join("\n", violations));
    }
}
