package com.printtools;

/** 兼容可执行 JAR 的无 JavaFX 继承启动入口。 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        BatchPrinterApp.main(args);
    }
}
