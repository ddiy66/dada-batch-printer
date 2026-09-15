package com.printtools.service;

import com.printtools.model.PrintFileType;
import com.printtools.model.PrintTask;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class FileCollector {
    public List<PrintTask> collect(Collection<Path> inputs, Collection<Path> existing) {
        var known = new LinkedHashSet<Path>();
        existing.stream().map(this::normalize).forEach(known::add);
        var result = new ArrayList<PrintTask>();
        for (Path input : inputs) {
            if (Files.isDirectory(input)) collectDirectory(input, known, result);
            else addFile(input, known, result);
        }
        return result;
    }

    private void collectDirectory(Path directory, LinkedHashSet<Path> known, List<PrintTask> result) {
        try (var stream = Files.walk(directory)) {
            stream.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString))
                    .forEach(path -> addFile(path, known, result));
        } catch (IOException ignored) {
            // 单个目录无法读取时跳过，其他拖入文件仍可继续处理。
        }
    }

    private void addFile(Path path, LinkedHashSet<Path> known, List<PrintTask> result) {
        Path normalized = normalize(path);
        PrintFileType.from(normalized).filter(type -> known.add(normalized))
                .ifPresent(type -> result.add(new PrintTask(normalized, type)));
    }

    private Path normalize(Path path) { return path.toAbsolutePath().normalize(); }
}
