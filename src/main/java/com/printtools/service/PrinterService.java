package com.printtools.service;

import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public final class PrinterService {
    public List<PrintService> findPrinters() {
        return Arrays.stream(PrintServiceLookup.lookupPrintServices(null, null))
                .sorted(Comparator.comparing(PrintService::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public PrintService defaultPrinter() { return PrintServiceLookup.lookupDefaultPrintService(); }
}
