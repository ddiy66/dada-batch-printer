package com.printtools.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** 使用当前用户注册表配置开机启动，不需要管理员权限。 */
public final class AutoStartService {
    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "打打印机";
    private static final String LEGACY_VALUE_NAME = "CartoonBatchPrinter";

    public void configure(boolean enabled) throws IOException, InterruptedException {
        String executable = System.getProperty("jpackage.app-path");
        if (enabled && (executable == null || executable.isBlank())) {
            throw new IOException("开发运行模式无法设置开机启动，请在 EXE 中设置");
        }
        if (enabled) {
            run(new ProcessBuilder("reg.exe", "ADD", RUN_KEY, "/v", VALUE_NAME, "/t", "REG_SZ", "/d",
                    quote(Path.of(executable).toAbsolutePath().toString()), "/f"), true);
        } else {
            run(new ProcessBuilder("reg.exe", "DELETE", RUN_KEY, "/v", VALUE_NAME, "/f"), false);
        }
        // 清理旧版本名称，避免升级后开机重复启动两个版本。
        run(new ProcessBuilder("reg.exe", "DELETE", RUN_KEY, "/v", LEGACY_VALUE_NAME, "/f"), false);
    }

    private void run(ProcessBuilder builder, boolean failureMatters) throws IOException, InterruptedException {
        Process process = builder.redirectErrorStream(true).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("设置开机启动超时");
        }
        if (failureMatters && process.exitValue() != 0) {
            throw new IOException(new String(process.getInputStream().readAllBytes(), java.nio.charset.Charset.defaultCharset()).trim());
        }
    }

    private String quote(String value) { return '"' + value + '"'; }
}
