package com.printtools.model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.image.Image;

import java.nio.file.Path;

public final class PrintTask {
    private final Path path;
    private final PrintFileType type;
    private final StringProperty status = new SimpleStringProperty(PrintStatus.WAITING.label());
    private final StringProperty message = new SimpleStringProperty("");
    private final IntegerProperty copies = new SimpleIntegerProperty(1);
    private final ObjectProperty<OrientationMode> orientation = new SimpleObjectProperty<>(OrientationMode.AUTO);
    private final ObjectProperty<Image> preview = new SimpleObjectProperty<>();
    private final ObjectProperty<Image> largePreview = new SimpleObjectProperty<>();
    private final BooleanProperty selected = new SimpleBooleanProperty(true);

    public PrintTask(Path path, PrintFileType type) { this.path = path; this.type = type; }
    public Path path() { return path; }
    public PrintFileType type() { return type; }
    public StringProperty statusProperty() { return status; }
    public StringProperty messageProperty() { return message; }
    public IntegerProperty copiesProperty() { return copies; }
    public int copies() { return copies.get(); }
    public ObjectProperty<OrientationMode> orientationProperty() { return orientation; }
    public OrientationMode orientation() { return orientation.get(); }
    public ObjectProperty<Image> previewProperty() { return preview; }
    public ObjectProperty<Image> largePreviewProperty() { return largePreview; }
    public Image largePreview() { return largePreview.get(); }
    public BooleanProperty selectedProperty() { return selected; }
    public boolean selected() { return selected.get(); }
    public void update(PrintStatus value, String detail) { status.set(value.label()); message.set(detail == null ? "" : detail); }
}
