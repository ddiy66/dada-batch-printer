package com.printtools.service;

import com.printtools.model.PrintTask;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.swing.Icon;
import javax.swing.filechooser.FileSystemView;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 后台生成桌面式文件预览，避免缩略图解码阻塞 JavaFX 界面线程。 */
public final class ThumbnailService implements AutoCloseable {
    private static final int PREVIEW_SIZE = 52;
    private static final int LARGE_PREVIEW_SIZE = 720;
    private final Map<String, Image> typeIconCache = new ConcurrentHashMap<>();
    private final Set<java.nio.file.Path> loadingLarge = ConcurrentHashMap.newKeySet();
    private final LinkedHashMap<java.nio.file.Path, PrintTask> largePreviewCache = new LinkedHashMap<>(16, 0.75f, true);
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "thumbnail-loader");
        thread.setDaemon(true);
        return thread;
    });

    public void loadAsync(PrintTask task) {
        executor.submit(() -> {
            try {
                Image thumbnail = switch (task.type()) {
                    case IMAGE -> loadImage(task, PREVIEW_SIZE);
                    case PDF -> loadPdf(task, 32, PREVIEW_SIZE);
                    case WORD -> loadSystemIcon(task);
                };
                Platform.runLater(() -> task.previewProperty().set(thumbnail));
            } catch (Exception ignored) {
                // 预览失败不影响打印，回退到系统文件图标。
                try {
                    Image fallback = loadSystemIcon(task);
                    Platform.runLater(() -> task.previewProperty().set(fallback));
                } catch (Exception ignoredAgain) { /* 保持空白占位 */ }
            }
        });
    }

    /** 高清图只在鼠标悬停时生成，避免文件较多时提前占用大量内存。 */
    public void loadLargeAsync(PrintTask task, Consumer<Image> callback) {
        if (task.largePreview() != null) {
            rememberLargePreview(task);
            callback.accept(task.largePreview());
            return;
        }
        if (!loadingLarge.add(task.path())) return;
        executor.submit(() -> {
            try {
                Image large = switch (task.type()) {
                    case IMAGE -> loadImage(task, LARGE_PREVIEW_SIZE);
                    case PDF -> loadPdf(task, 160, LARGE_PREVIEW_SIZE);
                    case WORD -> loadSystemIcon(task);
                };
                Platform.runLater(() -> {
                    task.largePreviewProperty().set(large);
                    rememberLargePreview(task);
                    callback.accept(large);
                });
            } catch (Exception ignored) {
                // 高清预览失败不影响列表和打印。
            } finally { loadingLarge.remove(task.path()); }
        });
    }

    /** 只保留最近使用的 10 张高清预览，避免连续浏览大量文件导致堆内存持续增长。 */
    private void rememberLargePreview(PrintTask task) {
        largePreviewCache.put(task.path(), task);
        while (largePreviewCache.size() > 10) {
            var iterator = largePreviewCache.entrySet().iterator();
            var oldest = iterator.next();
            iterator.remove();
            oldest.getValue().largePreviewProperty().set(null);
        }
    }

    private Image loadImage(PrintTask task, int size) throws Exception {
        try (InputStream input = java.nio.file.Files.newInputStream(task.path())) {
            return new Image(input, size, size, true, true);
        }
    }

    private Image loadPdf(PrintTask task, int dpi, int size) throws Exception {
        try (var document = Loader.loadPDF(task.path().toFile())) {
            BufferedImage page = new PDFRenderer(document).renderImageWithDPI(0, dpi);
            return toFxImage(scaleToFit(page, size, size));
        }
    }

    private Image loadSystemIcon(PrintTask task) {
        String extension = task.path().getFileName().toString().replaceFirst("^.*(?=\\.)", "").toLowerCase();
        return typeIconCache.computeIfAbsent(extension, key -> {
            int iconSize = 128;
            Icon icon = FileSystemView.getFileSystemView().getSystemIcon(task.path().toFile(), iconSize, iconSize);
            BufferedImage buffered = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = buffered.createGraphics();
            try { icon.paintIcon(null, graphics, (iconSize - icon.getIconWidth()) / 2, (iconSize - icon.getIconHeight()) / 2); }
            finally { graphics.dispose(); }
            return toFxImage(buffered);
        });
    }

    private BufferedImage scaleToFit(BufferedImage source, int maxWidth, int maxHeight) {
        double scale = Math.min((double) maxWidth / source.getWidth(), (double) maxHeight / source.getHeight());
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally { graphics.dispose(); }
        return target;
    }

    private Image toFxImage(BufferedImage source) {
        int[] pixels = source.getRGB(0, 0, source.getWidth(), source.getHeight(), null, 0, source.getWidth());
        WritableImage image = new WritableImage(source.getWidth(), source.getHeight());
        image.getPixelWriter().setPixels(0, 0, source.getWidth(), source.getHeight(),
                PixelFormat.getIntArgbInstance(), pixels, 0, source.getWidth());
        return image;
    }

    @Override public void close() { executor.shutdownNow(); }
}
