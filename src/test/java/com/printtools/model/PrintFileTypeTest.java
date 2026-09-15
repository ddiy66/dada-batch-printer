package com.printtools.model;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class PrintFileTypeTest {
    @Test void recognizesSupportedExtensionsIgnoringCase() {
        assertEquals(PrintFileType.PDF, PrintFileType.from(Path.of("A.PDF")).orElseThrow());
        assertEquals(PrintFileType.WORD, PrintFileType.from(Path.of("合同.docx")).orElseThrow());
        assertEquals(PrintFileType.IMAGE, PrintFileType.from(Path.of("照片.JpEg")).orElseThrow());
    }
    @Test void rejectsUnsupportedExtension() { assertTrue(PrintFileType.from(Path.of("data.xlsx")).isEmpty()); }
}
