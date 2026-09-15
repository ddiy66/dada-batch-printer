package com.printtools.service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** 将系统剪贴板图片转换成可加入打印队列的临时 PNG 文件。 */
public final class ClipboardImageService {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
    private static final long MAX_PIXELS = 50_000_000L;

    /** 在后台线程读取系统剪贴板，避免 Windows OLE 剪贴板阻塞 JavaFX 界面。 */
    public Optional<ClipboardImageData> capture() throws Exception {
        IllegalStateException lastBusyError = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
                if (contents == null || !contents.isDataFlavorSupported(DataFlavor.imageFlavor)) return Optional.empty();
                Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
                int width = image.getWidth(null);
                int height = image.getHeight(null);
                if (width <= 0 || height <= 0) return Optional.empty();
                if ((long) width * height > MAX_PIXELS) {
                    throw new IOException("剪贴板图片过大，请使用不超过 5000 万像素的图片");
                }
                BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = buffered.createGraphics();
                try { graphics.drawImage(image, 0, 0, null); }
                finally { graphics.dispose(); }
                int[] pixels = buffered.getRGB(0, 0, width, height, null, 0, width);
                return Optional.of(new ClipboardImageData(width, height, pixels));
            } catch (IllegalStateException busy) {
                lastBusyError = busy;
                Thread.sleep(120L * (attempt + 1));
            }
        }
        throw new IOException("剪贴板正被其他程序占用，请稍后重试", lastBusyError);
    }

    public Path save(ClipboardImageData data) throws IOException {
        Path directory = Path.of(System.getProperty("java.io.tmpdir"), "CartoonBatchPrinter", "clipboard");
        Files.createDirectories(directory);
        Path output = directory.resolve("剪贴板图片-" + FILE_TIME.format(LocalDateTime.now()) + ".png");
        BufferedImage image = new BufferedImage(data.width(), data.height(), BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, data.width(), data.height(), data.pixels(), 0, data.width());
        if (!ImageIO.write(image, "png", output.toFile())) throw new IOException("系统无法保存 PNG 图片");
        output.toFile().deleteOnExit();
        return output;
    }

    public record ClipboardImageData(int width, int height, int[] pixels) {
        public ClipboardImageData {
            if (width <= 0 || height <= 0 || pixels == null || pixels.length != (long) width * height) {
                throw new IllegalArgumentException("剪贴板图片数据无效");
            }
        }
    }
}
