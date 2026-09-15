package com.printtools.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FileCollectorTest {
    @TempDir Path tempDir;
    @Test void collectsSupportedFilesAndDeduplicatesExistingPaths() throws Exception {
        Path pdf = Files.createFile(tempDir.resolve("one.pdf"));
        Files.createFile(tempDir.resolve("skip.txt"));
        Path image = Files.createFile(tempDir.resolve("two.png"));
        var tasks = new FileCollector().collect(List.of(tempDir, pdf), List.of(image));
        assertEquals(1, tasks.size());
        assertEquals(pdf.toAbsolutePath(), tasks.get(0).path());
    }
}
