package com.printtools;

import com.printtools.model.PrintStatus;
import com.printtools.model.PrintTask;
import com.printtools.model.OrientationMode;
import com.printtools.service.DocumentPrinter;
import com.printtools.service.FileCollector;
import com.printtools.service.PrinterService;
import com.printtools.service.AppSettings;
import com.printtools.service.AutoStartService;
import com.printtools.service.ThumbnailService;
import com.printtools.service.WindowsWindowService;
import com.printtools.service.ClipboardImageService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Dragboard;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Popup;
import javafx.geometry.Bounds;

import javax.print.PrintService;
import java.io.File;
import java.io.IOException;
import java.awt.AWTException;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.imageio.ImageIO;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class BatchPrinterApp extends Application {
    private static final String OPEN_SOURCE_URL = "https://github.com/ddiy66/dada-batch-printer";
    private final ObservableList<PrintTask> tasks = FXCollections.observableArrayList();
    private final PrinterService printerService = new PrinterService();
    private final FileCollector fileCollector = new FileCollector();
    private final DocumentPrinter documentPrinter = new DocumentPrinter();
    private final AppSettings appSettings = new AppSettings();
    private final AutoStartService autoStartService = new AutoStartService();
    private final ThumbnailService thumbnailService = new ThumbnailService();
    private final WindowsWindowService windowsWindowService = new WindowsWindowService();
    private final ClipboardImageService clipboardImageService = new ClipboardImageService();
    private final ExecutorService printExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "print-queue");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService backgroundExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "app-background");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final ExecutorService clipboardExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "clipboard-worker");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final AtomicBoolean printing = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final ComboBox<PrinterItem> printerBox = new ComboBox<>();
    private final Label summaryLabel = new Label("拖入文件，开始轻松打印");
    private Stage mainStage;
    private Stage floatingStage;
    private TrayIcon trayIcon;
    private final Popup previewPopup = new Popup();
    private ContextMenu trayContextMenu;
    private Stage trayMenuOwner;
    private String floatingWindowTitle;

    public static void main(String[] args) { launch(args); }

    @Override public void start(Stage stage) {
        // 隐藏最后一个窗口时仍保持托盘进程，只有托盘 Exit 才真正退出。
        Platform.setImplicitExit(false);
        this.mainStage = stage;
        stage.setTitle("打打印机");
        stage.getIcons().add(loadLogo());
        stage.setScene(createMainScene());
        stage.setMinWidth(820);
        stage.setMinHeight(560);
        stage.setWidth(1080);
        stage.setHeight(720);
        stage.setOnCloseRequest(event -> {
            event.consume();
            hideMainWindow();
        });
        stage.iconifiedProperty().addListener((observable, oldValue, minimized) -> {
            if (minimized && appSettings.minimizeToTray()) Platform.runLater(this::hideToTray);
        });
        stage.show();
        createFloatingPrinter();
        installTrayIcon();
        refreshPrinters();
    }

    private Scene createMainScene() {
        Label title = new Label("轻松批量打印");
        title.getStyleClass().add("title");
        Label subtitle = new Label("拖入文件，设置每份参数，然后一键交给打印机");
        subtitle.getStyleClass().add("subtitle");
        VBox heading = new VBox(4, title, subtitle);

        Button add = new Button("＋ 添加文件");
        add.getStyleClass().add("secondary-button");
        add.setOnAction(event -> chooseFiles());
        Button refresh = new Button("刷新打印机");
        refresh.getStyleClass().add("ghost-button");
        refresh.setOnAction(event -> refreshPrinters());
        Button settings = new Button("⚙ 设置");
        settings.getStyleClass().add("ghost-button");
        settings.setOnAction(event -> showSettings());
        HBox topActions = new HBox(10, add, refresh, settings);
        HBox header = new HBox(20, heading, new Region(), topActions);
        HBox.setHgrow(header.getChildren().get(1), Priority.ALWAYS);
        header.setAlignment(Pos.CENTER_LEFT);

        printerBox.setPrefWidth(330);
        Label onlineDot = new Label("●");
        onlineDot.getStyleClass().add("online-dot");
        Label printerHint = new Label("自动读取 Windows 打印机");
        printerHint.getStyleClass().add("subtitle");
        HBox printerState = new HBox(8, onlineDot, printerHint);
        printerState.setAlignment(Pos.CENTER_LEFT);
        HBox controls = new HBox(18, labeled("当前打印机", printerBox), printerState);
        controls.setAlignment(Pos.BOTTOM_LEFT);

        ImageView dropLogo = new ImageView(loadLogo());
        dropLogo.setFitWidth(58);
        dropLogo.setFitHeight(58);
        dropLogo.setPreserveRatio(true);
        Label dropTitle = new Label("把文件或文件夹拖到这里");
        dropTitle.getStyleClass().add("drop-title");
        Label dropSubtitle = new Label("支持 PDF、Word、PNG、JPG、BMP、GIF · 重复文件自动跳过");
        dropSubtitle.getStyleClass().add("subtitle");
        VBox dropText = new VBox(3, dropTitle, dropSubtitle);
        HBox dropZone = new HBox(15, dropLogo, dropText);
        dropZone.setAlignment(Pos.CENTER_LEFT);
        dropZone.getStyleClass().add("drop-zone");

        TableView<PrintTask> table = createTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        Button remove = new Button("移除选中");
        remove.getStyleClass().add("ghost-button");
        remove.setOnAction(event -> {
            if (!printing.get()) tasks.removeAll(table.getSelectionModel().getSelectedItems());
            updateSummary();
        });
        Button clear = new Button("清空");
        clear.getStyleClass().add("ghost-button");
        clear.setOnAction(event -> { if (!printing.get()) tasks.clear(); updateSummary(); });
        Button stop = new Button("停止后续任务");
        stop.getStyleClass().add("danger-button");
        stop.setOnAction(event -> stopRequested.set(true));
        Button print = new Button("打印已勾选文件");
        print.getStyleClass().add("primary-button");
        print.setOnAction(event -> printAll());
        HBox bottom = new HBox(10, summaryLabel, new Region(), remove, clear, stop, print);
        HBox.setHgrow(bottom.getChildren().get(1), Priority.ALWAYS);
        bottom.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(16, header, controls, dropZone, table, bottom);
        root.setPadding(new Insets(26));
        root.getStyleClass().add("app-root");
        configureDrop(root);
        Scene scene = new Scene(root);
        scene.getStylesheets().add(resource("/styles/app.css"));
        return scene;
    }

    private TableView<PrintTask> createTable() {
        TableView<PrintTask> table = new TableView<>(tasks);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("把文件拖到这里，或拖到桌面上的小打印机"));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        installClipboardContextMenu(table);
        CheckBox selectAll = new CheckBox();
        selectAll.setSelected(true);
        selectAll.setTooltip(new Tooltip("全选 / 取消全选"));
        selectAll.setOnAction(event -> {
            tasks.forEach(task -> task.selectedProperty().set(selectAll.isSelected()));
            updateSummary();
        });
        TableColumn<PrintTask, Boolean> selected = new TableColumn<>();
        selected.setGraphic(selectAll);
        selected.setCellValueFactory(data -> data.getValue().selectedProperty());
        selected.setCellFactory(column -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();
            @Override protected void updateItem(Boolean value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
                PrintTask task = getTableRow().getItem();
                checkBox.setSelected(task.selected());
                checkBox.setOnAction(event -> {
                    task.selectedProperty().set(checkBox.isSelected());
                    selectAll.setSelected(!tasks.isEmpty() && tasks.stream().allMatch(PrintTask::selected));
                    updateSummary();
                });
                setGraphic(checkBox);
                setAlignment(Pos.CENTER);
            }
        });
        selected.setPrefWidth(48);
        selected.setMaxWidth(48);
        TableColumn<PrintTask, Image> preview = new TableColumn<>("预览");
        preview.setCellValueFactory(data -> data.getValue().previewProperty());
        preview.setCellFactory(column -> new TableCell<>() {
            private final ImageView imageView = new ImageView();
            private PrintTask currentTask;
            {
                imageView.setFitWidth(48);
                imageView.setFitHeight(48);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
                setOnMouseEntered(event -> showHoverPreview(this, currentTask));
                setOnMouseMoved(event -> {
                    if (!previewPopup.isShowing()) showHoverPreview(this, currentTask);
                });
                setOnMouseExited(event -> previewPopup.hide());
            }
            @Override protected void updateItem(Image image, boolean empty) {
                super.updateItem(image, empty);
                currentTask = empty || getTableRow() == null ? null : getTableRow().getItem();
                setUserData(currentTask);
                imageView.setImage(empty ? null : image);
                setGraphic(empty || image == null ? null : imageView);
                setAlignment(Pos.CENTER);
            }
        });
        preview.setPrefWidth(76);
        TableColumn<PrintTask, String> name = new TableColumn<>("文件名");
        name.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().path().getFileName().toString()));
        name.setPrefWidth(300);
        TableColumn<PrintTask, String> path = new TableColumn<>("位置");
        path.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().path().getParent().toString()));
        path.setPrefWidth(280);
        TableColumn<PrintTask, String> type = new TableColumn<>("类型");
        type.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().type().name()));
        type.setPrefWidth(75);
        TableColumn<PrintTask, Number> copies = new TableColumn<>("份数");
        copies.setCellValueFactory(data -> data.getValue().copiesProperty());
        copies.setCellFactory(column -> new CopiesCell());
        copies.setPrefWidth(90);
        TableColumn<PrintTask, OrientationMode> orientation = new TableColumn<>("方向");
        orientation.setCellValueFactory(data -> data.getValue().orientationProperty());
        orientation.setCellFactory(column -> new OrientationCell());
        orientation.setPrefWidth(105);
        TableColumn<PrintTask, String> status = new TableColumn<>("状态");
        status.setCellValueFactory(data -> data.getValue().statusProperty());
        status.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                getStyleClass().removeAll("status-waiting", "status-printing", "status-success", "status-failed");
                if (!empty && value != null) {
                    if (value.equals(PrintStatus.SUCCESS.label())) getStyleClass().add("status-success");
                    else if (value.equals(PrintStatus.FAILED.label())) getStyleClass().add("status-failed");
                    else if (value.equals(PrintStatus.PRINTING.label())) getStyleClass().add("status-printing");
                    else getStyleClass().add("status-waiting");
                }
            }
        });
        status.setPrefWidth(90);
        table.getColumns().addAll(selected, preview, name, path, type, copies, orientation, status);
        return table;
    }

    private VBox labeled(String text, Control control) {
        Label label = new Label(text);
        label.getStyleClass().add("field-label");
        return new VBox(6, label, control);
    }

    private void createFloatingPrinter() {
        floatingStage = new Stage(StageStyle.TRANSPARENT);
        floatingWindowTitle = "CartoonBatchPrinterFloatingLogo-" + ProcessHandle.current().pid();
        floatingStage.setTitle(floatingWindowTitle);
        floatingStage.setAlwaysOnTop(true);
        ImageView logo = new ImageView(loadLogo());
        logo.setFitWidth(108);
        logo.setFitHeight(108);
        logo.setPreserveRatio(true);
        logo.setScaleX(-1);
        logo.setScaleX(-1);
        Label hint = new Label("拖文件到这里");
        hint.getStyleClass().add("float-hint");
        VBox box = new VBox(2, logo, hint);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("floating-printer");
        Scene scene = new Scene(box, 138, 146);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        scene.getStylesheets().add(resource("/styles/app.css"));
        floatingStage.setScene(scene);
        floatingStage.setX(javafx.stage.Screen.getPrimary().getVisualBounds().getMaxX() - 170);
        floatingStage.setY(javafx.stage.Screen.getPrimary().getVisualBounds().getMaxY() - 210);
        final double[] offset = new double[2];
        box.setOnMousePressed(e -> { offset[0] = e.getScreenX() - floatingStage.getX(); offset[1] = e.getScreenY() - floatingStage.getY(); });
        box.setOnMouseDragged(e -> { floatingStage.setX(e.getScreenX() - offset[0]); floatingStage.setY(e.getScreenY() - offset[1]); });
        box.setOnMouseClicked(e -> { if (e.getClickCount() == 2) showMain(); });
        installClipboardContextMenu(box);
        configureDrop(box);
        if (appSettings.floatingLogo()) {
            floatingStage.show();
            Platform.runLater(() -> windowsWindowService.hideFromTaskbar(floatingWindowTitle));
        }
    }

    private void configureDrop(javafx.scene.Node node) {
        node.setOnDragOver(event -> { if (event.getGestureSource() != node && event.getDragboard().hasFiles()) event.acceptTransferModes(TransferMode.COPY); event.consume(); });
        node.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            boolean accepted = board.hasFiles();
            if (accepted) { addPaths(board.getFiles().stream().map(File::toPath).toList()); showMain(); }
            event.setDropCompleted(accepted);
            event.consume();
        });
    }

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择要打印的文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("可打印文件", "*.pdf", "*.doc", "*.docx", "*.png", "*.jpg", "*.jpeg", "*.bmp", "*.gif"));
        List<File> selected = chooser.showOpenMultipleDialog(mainStage);
        if (selected != null) addPaths(selected.stream().map(File::toPath).toList());
    }

    private void addPaths(List<Path> paths) {
        var existing = tasks.stream().map(PrintTask::path).toList();
        summaryLabel.setText("正在读取文件，请稍候…");
        backgroundExecutor.submit(() -> {
            List<PrintTask> added = fileCollector.collect(paths, existing);
            Platform.runLater(() -> {
                tasks.addAll(added);
                added.forEach(thumbnailService::loadAsync);
                updateSummary();
            });
        });
    }

    private void installClipboardContextMenu(javafx.scene.Node node) {
        ContextMenu menu = new ContextMenu();
        MenuItem paste = new MenuItem("从剪贴板粘贴图片");
        paste.setOnAction(event -> pasteClipboardImage());
        menu.getItems().add(paste);
        node.setOnContextMenuRequested(event -> {
            menu.show(node, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void pasteClipboardImage() {
        summaryLabel.setText("正在读取并保存剪贴板图片…");
        clipboardExecutor.submit(() -> {
            try {
                var captured = clipboardImageService.capture();
                if (captured.isEmpty()) {
                    Platform.runLater(() -> { updateSummary(); alert("剪贴板中没有可粘贴的图片"); });
                    return;
                }
                Path image = clipboardImageService.save(captured.get());
                Platform.runLater(() -> addPaths(List.of(image)));
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    updateSummary();
                    alert("粘贴图片失败：" + ex.getMessage());
                });
            }
        });
    }

    private void refreshPrinters() {
        PrinterItem previous = printerBox.getValue();
        printerBox.setDisable(true);
        printerBox.setPromptText("正在识别打印机…");
        backgroundExecutor.submit(() -> {
            PrintService defaultPrinter = printerService.defaultPrinter();
            var items = printerService.findPrinters().stream().map(PrinterItem::new).toList();
            Platform.runLater(() -> {
                printerBox.getItems().setAll(items);
                PrinterItem selected = items.stream().filter(item -> previous != null && item.name().equals(previous.name())).findFirst()
                        .orElseGet(() -> items.stream().filter(item -> defaultPrinter != null && item.name().equals(defaultPrinter.getName())).findFirst().orElse(null));
                printerBox.setValue(selected);
                printerBox.setDisable(false);
                updateSummary();
            });
        });
    }

    private void printAll() {
        PrinterItem printer = printerBox.getValue();
        List<PrintTask> selectedTasks = tasks.stream().filter(PrintTask::selected).toList();
        if (tasks.isEmpty()) { alert("请先添加至少一个可打印文件"); return; }
        if (selectedTasks.isEmpty()) { alert("请至少勾选一个需要打印的文件"); return; }
        if (printer == null) { alert("未检测到打印机，请检查连接后刷新"); return; }
        if (!printing.compareAndSet(false, true)) return;
        stopRequested.set(false);
        selectedTasks.forEach(task -> task.update(PrintStatus.WAITING, ""));
        updateSummary();
        printExecutor.submit(() -> {
            for (PrintTask task : selectedTasks) {
                if (stopRequested.get()) { Platform.runLater(() -> task.update(PrintStatus.CANCELLED, "未提交")); continue; }
                Platform.runLater(() -> task.update(PrintStatus.PRINTING, "正在提交到打印机"));
                try {
                    documentPrinter.print(task.path(), task.type(), printer.service(), task.copies(), task.orientation());
                    Platform.runLater(() -> task.update(PrintStatus.SUCCESS, "已发送到打印队列"));
                } catch (Exception ex) {
                    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    Platform.runLater(() -> task.update(PrintStatus.FAILED, message));
                }
            }
            printing.set(false);
            Platform.runLater(this::updateSummary);
        });
    }

    private void showHoverPreview(javafx.scene.Node anchor, PrintTask task) {
        if (task == null || previewPopup.isShowing()) return;
        if (task.largePreview() == null) {
            thumbnailService.loadLargeAsync(task, image -> {
                if (anchor.isHover() && anchor.getUserData() == task) showHoverPreview(anchor, task);
            });
            return;
        }
        Bounds anchorBounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (anchorBounds == null) return;
        var screens = javafx.stage.Screen.getScreensForRectangle(anchorBounds.getMinX(), anchorBounds.getMinY(),
                anchorBounds.getWidth(), anchorBounds.getHeight());
        var screen = screens.isEmpty() ? javafx.stage.Screen.getPrimary() : screens.get(0);
        double previewSize = Math.max(420, Math.min(720, screen.getVisualBounds().getHeight() - 180));
        ImageView largeImage = new ImageView(task.largePreview());
        largeImage.setFitWidth(previewSize);
        largeImage.setFitHeight(previewSize);
        largeImage.setPreserveRatio(true);
        largeImage.setSmooth(true);
        Label fileName = new Label(task.path().getFileName().toString());
        fileName.setMaxWidth(previewSize);
        fileName.setWrapText(true);
        fileName.getStyleClass().add("preview-file-name");
        VBox card = new VBox(10, largeImage, fileName);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("large-preview-card");
        card.getStylesheets().add(resource("/styles/app.css"));
        previewPopup.getContent().setAll(card);
        previewPopup.setAutoFix(true);
        previewPopup.setAutoHide(true);
        previewPopup.setHideOnEscape(true);
        previewPopup.show(anchor, anchorBounds.getMaxX() + 12, anchorBounds.getMinY() - 24);
    }

    private void showMain() {
        mainStage.show();
        mainStage.toFront();
        applyFloatingLogoVisibility();
    }

    private void applyFloatingLogoVisibility() {
        if (floatingStage == null) return;
        if (appSettings.floatingLogo()) {
            floatingStage.show();
            Platform.runLater(() -> windowsWindowService.hideFromTaskbar(floatingWindowTitle));
        }
        else floatingStage.hide();
    }

    private void hideMainWindow() {
        if (trayIcon != null) hideToTray();
        else { mainStage.hide(); floatingStage.show(); }
    }

    private void hideToTray() {
        mainStage.setIconified(false);
        mainStage.hide();
        // 悬浮 Logo 是否显示只由托盘菜单开关决定。
        Platform.runLater(this::applyFloatingLogoVisibility);
        if (trayIcon != null) trayIcon.displayMessage("打打印机", "程序已缩小到系统托盘", TrayIcon.MessageType.INFO);
    }

    private void installTrayIcon() {
        if (!SystemTray.isSupported()) return;
        try {
            java.awt.Image image = ImageIO.read(java.util.Objects.requireNonNull(getClass().getResourceAsStream("/images/printer-mascot.png")));
            createChineseTrayMenu();
            trayIcon = new TrayIcon(image, "打打印机");
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(event -> Platform.runLater(this::showMain));
            trayIcon.addMouseListener(new MouseAdapter() {
                @Override public void mouseReleased(MouseEvent event) {
                    if (event.getButton() == MouseEvent.BUTTON3) {
                        Platform.runLater(() -> showChineseTrayMenu(event.getX(), event.getY()));
                    }
                }
            });
            SystemTray.getSystemTray().add(trayIcon);
        } catch (IOException | AWTException ex) {
            trayIcon = null;
        }
    }

    /** 使用 JavaFX 渲染中文托盘菜单，避免 AWT 原生菜单在部分 Windows 环境显示方框。 */
    private void createChineseTrayMenu() {
        trayMenuOwner = new Stage(StageStyle.UTILITY);
        trayMenuOwner.setAlwaysOnTop(true);
        trayMenuOwner.setOpacity(0.01);
        trayMenuOwner.setWidth(1);
        trayMenuOwner.setHeight(1);
        trayMenuOwner.setScene(new Scene(new Pane(), 1, 1, javafx.scene.paint.Color.TRANSPARENT));

        MenuItem open = new MenuItem("打开主界面");
        open.setOnAction(event -> showMain());
        CheckMenuItem floatingLogo = new CheckMenuItem("显示悬浮 Logo");
        floatingLogo.setOnAction(event -> {
            appSettings.floatingLogo(floatingLogo.isSelected());
            applyFloatingLogoVisibility();
        });
        CheckMenuItem autoStart = new CheckMenuItem("开机自动启动");
        autoStart.setOnAction(event -> {
            boolean enabled = autoStart.isSelected();
            try {
                autoStartService.configure(enabled);
                appSettings.autoStart(enabled);
            } catch (Exception ex) {
                autoStart.setSelected(appSettings.autoStart());
                alert("设置开机启动失败：" + ex.getMessage());
            }
        });
        MenuItem exit = new MenuItem("退出程序");
        exit.setOnAction(event -> { printExecutor.shutdownNow(); Platform.exit(); });
        trayContextMenu = new ContextMenu(open, floatingLogo, autoStart, new SeparatorMenuItem(), exit);
        trayContextMenu.getStyleClass().add("tray-menu");
        trayContextMenu.setOnShowing(event -> {
            floatingLogo.setSelected(appSettings.floatingLogo());
            autoStart.setSelected(appSettings.autoStart());
        });
        trayContextMenu.setOnHidden(event -> trayMenuOwner.hide());
    }

    private void showChineseTrayMenu(double screenX, double screenY) {
        if (trayContextMenu == null || trayMenuOwner == null) return;
        trayMenuOwner.setX(screenX);
        trayMenuOwner.setY(screenY);
        trayMenuOwner.show();
        trayContextMenu.show(trayMenuOwner, screenX, screenY);
    }

    private void showSettings() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(mainStage);
        dialog.setTitle("设置");
        dialog.setHeaderText("设置与支持");
        CheckBox autoStart = new CheckBox("开机自动启动");
        autoStart.setSelected(appSettings.autoStart());
        CheckBox minimizeToTray = new CheckBox("最小化时缩小到系统托盘（关闭始终进入托盘）");
        minimizeToTray.setSelected(appSettings.minimizeToTray());
        Label note = new Label("托盘图标双击可重新打开，右键可退出程序。");
        note.getStyleClass().add("subtitle");

        Label supportTitle = new Label("开源与支持");
        supportTitle.getStyleClass().add("settings-section-title");
        Hyperlink sourceLink = new Hyperlink(OPEN_SOURCE_URL);
        sourceLink.getStyleClass().add("opensource-url");
        sourceLink.setOnAction(event -> getHostServices().showDocument(OPEN_SOURCE_URL));

        Button openSource = new Button("打开开源项目");
        openSource.getStyleClass().add("secondary-button");
        openSource.setOnAction(event -> getHostServices().showDocument(OPEN_SOURCE_URL));
        Button copySource = new Button("复制开源地址");
        copySource.getStyleClass().add("ghost-button");
        copySource.setOnAction(event -> {
            ClipboardContent clipboardContent = new ClipboardContent();
            clipboardContent.putString(OPEN_SOURCE_URL);
            Clipboard.getSystemClipboard().setContent(clipboardContent);
            copySource.setText("已复制");
        });
        Button donate = new Button("鼓励作者 / 打赏");
        donate.getStyleClass().add("donate-button");
        donate.setOnAction(event -> showDonationDialog());
        HBox supportButtons = new HBox(10, openSource, copySource, donate);
        Label supportNote = new Label("项目完全开源免费，欢迎分享、Star 和提出建议。您的支持会帮助软件持续更新。");
        supportNote.setWrapText(true);
        supportNote.getStyleClass().add("subtitle");

        VBox content = new VBox(14, autoStart, minimizeToTray, note, new Separator(),
                supportTitle, sourceLink, supportButtons, supportNote);
        content.setPadding(new Insets(12, 4, 4, 4));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(560);
        dialog.getDialogPane().getStylesheets().add(resource("/styles/app.css"));
        dialog.getDialogPane().getStyleClass().add("settings-dialog");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.showAndWait().filter(ButtonType.OK::equals).ifPresent(result -> {
            try {
                if (autoStart.isSelected() != appSettings.autoStart()) autoStartService.configure(autoStart.isSelected());
                appSettings.autoStart(autoStart.isSelected());
                appSettings.minimizeToTray(minimizeToTray.isSelected());
            } catch (Exception ex) {
                alert("保存设置失败：" + ex.getMessage());
            }
        });
    }

    private void showDonationDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(mainStage);
        dialog.setTitle("鼓励作者");
        dialog.setHeaderText("感谢您支持“打打印机”持续更新");

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().addAll(
                donationTab("微信支付", "/images/donate-wechat.jpg"),
                donationTab("支付宝", "/images/donate-alipay.jpg")
        );
        tabs.setPrefSize(430, 590);
        dialog.getDialogPane().setContent(tabs);
        dialog.getDialogPane().getStylesheets().add(resource("/styles/app.css"));
        dialog.getDialogPane().getStyleClass().add("donation-dialog");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.showAndWait();
    }

    private Tab donationTab(String title, String imagePath) {
        ImageView imageView = new ImageView(new Image(resource(imagePath), true));
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setFitWidth(390);
        imageView.setFitHeight(520);
        StackPane imagePane = new StackPane(imageView);
        imagePane.setPadding(new Insets(12));
        return new Tab(title, imagePane);
    }
    private void alert(String message) { new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK).showAndWait(); }
    private void updateSummary() {
        String printerText = printerBox.getItems().isEmpty() ? "未检测到打印机" : "已识别 " + printerBox.getItems().size() + " 台打印机";
        long selectedCount = tasks.stream().filter(PrintTask::selected).count();
        summaryLabel.setText(tasks.size() + " 个文件 · 已选 " + selectedCount + " 个 · " + printerText + (printing.get() ? " · 正在打印" : ""));
    }
    private Image loadLogo() { return new Image(resource("/images/printer-mascot.png")); }
    private String resource(String path) { return java.util.Objects.requireNonNull(getClass().getResource(path), "资源缺失: " + path).toExternalForm(); }

    @Override public void stop() {
        stopRequested.set(true);
        printExecutor.shutdownNow();
        backgroundExecutor.shutdownNow();
        clipboardExecutor.shutdownNow();
        thumbnailService.close();
        if (trayIcon != null) SystemTray.getSystemTray().remove(trayIcon);
    }

    public record PrinterItem(PrintService service) {
        public String name() { return service.getName(); }
        @Override public String toString() { return name(); }
    }

    private final class CopiesCell extends TableCell<PrintTask, Number> {
        private final Spinner<Integer> spinner = new Spinner<>(1, 99, 1);
        CopiesCell() { spinner.setEditable(true); spinner.setPrefWidth(72); }
        @Override protected void updateItem(Number value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
            PrintTask task = getTableRow().getItem();
            spinner.getValueFactory().setValue(task.copies());
            spinner.valueProperty().addListener((observable, oldValue, newValue) -> {
                if (getTableRow().getItem() == task && newValue != null) task.copiesProperty().set(newValue);
            });
            setGraphic(spinner);
        }
    }

    private final class OrientationCell extends TableCell<PrintTask, OrientationMode> {
        private final ComboBox<OrientationMode> box = new ComboBox<>(FXCollections.observableArrayList(OrientationMode.values()));
        OrientationCell() { box.setPrefWidth(92); }
        @Override protected void updateItem(OrientationMode value, boolean empty) {
            super.updateItem(value, empty);
            if (empty || getTableRow() == null || getTableRow().getItem() == null) { setGraphic(null); return; }
            PrintTask task = getTableRow().getItem();
            box.setValue(task.orientation());
            box.setOnAction(event -> { if (getTableRow().getItem() == task) task.orientationProperty().set(box.getValue()); });
            setGraphic(box);
        }
    }
}
