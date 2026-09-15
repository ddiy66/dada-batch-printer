package com.printtools.model;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

public enum PrintFileType {
    PDF, WORD, IMAGE;

    public static Optional<PrintFileType> from(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return Optional.of(PDF);
        if (name.endsWith(".doc") || name.endsWith(".docx")) return Optional.of(WORD);
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".bmp") || name.endsWith(".gif")) return Optional.of(IMAGE);
        return Optional.empty();
    }
}
