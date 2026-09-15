package com.printtools.service;

import com.printtools.model.PrintFileType;
import com.printtools.model.OrientationMode;

import javax.imageio.ImageIO;
import javax.print.PrintService;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPrintable;
import org.apache.pdfbox.printing.Scaling;

public final class DocumentPrinter {
    public void print(Path file, PrintFileType type, PrintService service, int copies, OrientationMode orientation) throws Exception {
        Objects.requireNonNull(service, "打印机不能为空");
        switch (type) {
            case PDF -> printPdf(file, service, copies, orientation);
            case IMAGE -> printImage(file, service, copies, orientation);
            case WORD -> printWord(file, service, copies, orientation);
        }
    }

    private void printPdf(Path file, PrintService service, int copies, OrientationMode orientation) throws IOException, PrinterException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PrinterJob job = createJob(service, copies, file);
            var box = document.getPage(0).getCropBox();
            PageFormat format = pageFormat(job, resolveOrientation(orientation, box.getWidth(), box.getHeight()));
            job.setPrintable(new PDFPrintable(document, Scaling.SHRINK_TO_FIT), format);
            job.print();
        }
    }

    private void printImage(Path file, PrintService service, int copies, OrientationMode orientation) throws IOException, PrinterException {
        Image image = ImageIO.read(file.toFile());
        if (image == null) throw new IOException("无法读取图片内容");
        PrinterJob job = createJob(service, copies, file);
        OrientationMode resolved = resolveOrientation(orientation, image.getWidth(null), image.getHeight(null));
        job.setPrintable(new AspectRatioImagePrintable(image), pageFormat(job, resolved));
        job.print();
    }

    private void printWord(Path file, PrintService service, int copies, OrientationMode orientation) throws IOException, InterruptedException {
        Path scriptPath = Files.createTempFile("cartoon-printer-word-", ".ps1");
        scriptPath.toFile().deleteOnExit();
        try (var input = Objects.requireNonNull(getClass().getResourceAsStream("/scripts/print-word.ps1"), "Word 打印脚本缺失")) {
            Files.copy(input, scriptPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File",
                scriptPath.toString(), file.toAbsolutePath().toString(), service.getName(), String.valueOf(copies), orientation.name())
                .redirectErrorStream(true).start();
        boolean finished = process.waitFor(10, TimeUnit.MINUTES);
        if (!finished) { process.destroyForcibly(); throw new IOException("Word 打印超时"); }
        if (process.exitValue() != 0) {
            String output = new String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            throw new IOException(output.isBlank() ? "Word 打印失败，请确认已安装 Microsoft Word" : output);
        }
    }

    private PrinterJob createJob(PrintService service, int copies, Path file) throws PrinterException {
        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(service);
        job.setCopies(copies);
        job.setJobName("卡通批量打印-" + file.getFileName());
        return job;
    }

    private OrientationMode resolveOrientation(OrientationMode requested, double width, double height) {
        return requested == OrientationMode.AUTO ? (width > height ? OrientationMode.LANDSCAPE : OrientationMode.PORTRAIT) : requested;
    }

    private PageFormat pageFormat(PrinterJob job, OrientationMode orientation) {
        PageFormat format = job.defaultPage();
        format.setOrientation(orientation == OrientationMode.LANDSCAPE ? PageFormat.LANDSCAPE : PageFormat.PORTRAIT);
        return job.validatePage(format);
    }

    /** 图片按常见屏幕 96 DPI 换算为纸张点数，仅居中，不做任何缩放。 */
    static final class AspectRatioImagePrintable implements Printable {
        private final Image image;
        AspectRatioImagePrintable(Image image) { this.image = image; }

        @Override public int print(Graphics graphics, PageFormat format, int pageIndex) {
            if (pageIndex > 0) return NO_SUCH_PAGE;
            double sourceWidth = image.getWidth(null);
            double sourceHeight = image.getHeight(null);
            // 只使用一个统一比例缩小，严格保留原图宽高比，禁止分别拉伸宽和高。
            double scale = Math.min(1d, Math.min(format.getImageableWidth() / sourceWidth,
                    format.getImageableHeight() / sourceHeight));
            double width = sourceWidth * scale;
            double height = sourceHeight * scale;
            double x = format.getImageableX() + (format.getImageableWidth() - width) / 2d;
            double y = format.getImageableY() + (format.getImageableHeight() - height) / 2d;
            Graphics2D g2 = (Graphics2D) graphics.create();
            try { g2.drawImage(image, (int) Math.round(x), (int) Math.round(y), (int) Math.round(width), (int) Math.round(height), null); }
            finally { g2.dispose(); }
            return PAGE_EXISTS;
        }
    }
}
