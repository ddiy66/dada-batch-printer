package com.printtools.model;

public enum PrintStatus {
    WAITING("等待中"), PRINTING("打印中"), SUCCESS("已提交"), FAILED("失败"), CANCELLED("已停止");

    private final String label;
    PrintStatus(String label) { this.label = label; }
    public String label() { return label; }
}
