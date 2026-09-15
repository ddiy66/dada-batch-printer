package com.printtools.service;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

class ClipboardImageServiceTest {
    @Test void savesCapturedPixelsAsPng() throws Exception {
        int[] pixels = {0xffff0000, 0xff00ff00, 0xff0000ff, 0xffffffff};
        var data = new ClipboardImageService.ClipboardImageData(2, 2, pixels);
        var path = new ClipboardImageService().save(data);
        assertTrue(Files.exists(path));
        var image = ImageIO.read(path.toFile());
        assertEquals(2, image.getWidth());
        assertEquals(2, image.getHeight());
        assertEquals(0xffff0000, image.getRGB(0, 0));
    }
}
