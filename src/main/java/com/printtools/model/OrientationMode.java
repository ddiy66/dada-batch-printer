package com.printtools.model;

public enum OrientationMode {
    AUTO("自动"), PORTRAIT("竖向"), LANDSCAPE("横向");

    private final String label;
    OrientationMode(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
