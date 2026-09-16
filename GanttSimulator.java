import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.file.Files;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class GanttSimulator extends Application {

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private record LogEntry(String time, String key, Object[] args) {}

    // Instance courante
    private Solution baseline;
    private Solution solution;

    // Etat de l'optimisation, partagé avec le thread de calcul
    private volatile boolean running = false;
    private volatile boolean stopRequested = false;
    private volatile int delayMs = 2;
    private volatile Solution latestBest;
    private volatile int latestIteration;
    private volatile int latestCurrentCmax;
    private final ConcurrentLinkedQueue<int[]> pendingPoints = new ConcurrentLinkedQueue<>();
    private long startNanos;
    private int maxIterations;

    // Composants
    private GanttView gantt;
    private TextField taskField, machineField;
    private Spinner<Integer> tasksSpinner, machinesSpinner, iterSpinner, lfaSpinner;
    private ComboBox<Integer> setupCombo;
    private Slider delaySlider;
    private Button browseButton, loadButton, generateButton, runButton, stopButton, resetButton;
    private Label statusChip, instanceTitle, instanceMeta;
    private Label kpiInitial, kpiBest, kpiGain, kpiGainSub, kpiIter, kpiIterSub, kpiTime;
    private ProgressBar progressBar;
    private Label progressLabel;
    private XYChart.Series<Number, Number> bestSeries, currentSeries;
    private ListView<LogEntry> logView;
    private final List<Control> configControls = new ArrayList<>();

    private static String t(String key, Object... args) {
        return I18n.t(key, args);
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        BorderPane root = new BorderPane();
        root.setTop(buildHeader());
        root.setLeft(buildSidebar(stage));
        root.setCenter(buildContent());
        root.setBottom(buildStatusBar());

        Scene scene = new Scene(root, 1440, 900);
        URL css = GanttSimulator.class.getResource("gantt.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setScene(scene);
        stage.titleProperty().bind(I18n.bind("app.title"));
        stage.setMinWidth(1100);
        stage.setMinHeight(700);
        stage.setMaximized(true);
        stage.show();

        I18n.languageProperty().addListener((obs, old, lang) -> {
            gantt.redraw();
            logView.refresh();
        });

        new AnimationTimer() {
            @Override
            public void handle(long now) {
                refreshLiveState();
            }
        }.start();

        updateControls();
        log("log.ready");
    }

    @Override
    public void stop() {
        stopRequested = true;
    }

    // ------------------------------------------------------------------ Construction de l'interface

    private Node buildHeader() {
        Label logo = new Label("GS");
        logo.getStyleClass().add("logo");
        Label title = new Label("Gantt Simulator");
        title.getStyleClass().add("app-title");
        Label subtitle = label("app.subtitle", "app-subtitle");
        VBox titles = new VBox(1, title, subtitle);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        statusChip = new Label();
        statusChip.getStyleClass().add("chip");
        setStatus("status.idle", "idle");

        HBox header = new HBox(12, logo, titles, spacer, statusChip, buildLanguageMenu());
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("header");
        return header;
    }

    private Node buildLanguageMenu() {
        MenuButton menu = new MenuButton();
        menu.getStyleClass().add("lang-menu");
        menu.textProperty().bind(Bindings.createStringBinding(
                () -> I18n.languageProperty().get().code.toUpperCase(Locale.ROOT), I18n.languageProperty()));
        Tooltip tooltip = new Tooltip();
        tooltip.textProperty().bind(I18n.bind("lang.tooltip"));
        menu.setTooltip(tooltip);

        ToggleGroup group = new ToggleGroup();
        for (I18n.Lang lang : I18n.Lang.values()) {
            RadioMenuItem item = new RadioMenuItem(lang.code.toUpperCase(Locale.ROOT) + "  —  " + lang.label);
            item.setToggleGroup(group);
            item.setSelected(lang == I18n.languageProperty().get());
            item.setOnAction(e -> I18n.languageProperty().set(lang));
            menu.getItems().add(item);
        }
        return menu;
    }

    private Node buildSidebar(Stage stage) {
        taskField = new TextField();
        taskField.setPromptText("…/task.txt");
        machineField = new TextField();
        machineField.setPromptText("…/machine");
        browseButton = button("btn.browse", "secondary", e -> browseInstance(stage));
        loadButton = button("btn.load", "secondary", e -> loadFromFields());
        HBox fileButtons = new HBox(8, browseButton, loadButton);
        HBox.setHgrow(browseButton, Priority.ALWAYS);
        HBox.setHgrow(loadButton, Priority.ALWAYS);
        VBox fileSection = section("section.files",
                field("field.taskFile", taskField),
                field("field.machineDir", machineField),
                fileButtons);

        tasksSpinner = intSpinner(2, 500, 30, 1);
        machinesSpinner = intSpinner(1, 50, 5, 1);
        setupCombo = new ComboBox<>(FXCollections.observableArrayList(9, 49, 99, 124));
        setupCombo.setValue(49);
        generateButton = button("btn.generate", "secondary", e -> generateRandom());
        VBox randomSection = section("section.random",
                new HBox(8, field("field.tasks", tasksSpinner), field("field.machines", machinesSpinner)),
                field("field.setup", setupCombo),
                generateButton);

        iterSpinner = intSpinner(100, 1_000_000, 5000, 500);
        lfaSpinner = intSpinner(1, 100_000, 200, 10);
        delaySlider = new Slider(0, 50, delayMs);
        delaySlider.valueProperty().addListener((obs, old, v) -> delayMs = v.intValue());
        Label delayValue = new Label();
        delayValue.getStyleClass().add("field-value");
        delayValue.textProperty().bind(Bindings.createStringBinding(
                () -> (int) delaySlider.getValue() + " ms", delaySlider.valueProperty()));
        HBox delayRow = new HBox(10, delaySlider, delayValue);
        delayRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(delaySlider, Priority.ALWAYS);
        VBox paramSection = section("section.lahc",
                new HBox(8, field("field.iterations", iterSpinner), field("field.lfa", lfaSpinner)),
                field("field.delay", delayRow));

        runButton = button("btn.run", "primary", e -> startOptimisation());
        stopButton = button("btn.stop", "danger", e -> requestStop());
        resetButton = button("btn.reset", "ghost", e -> resetSolution());
        HBox secondaryActions = new HBox(8, stopButton, resetButton);
        HBox.setHgrow(stopButton, Priority.ALWAYS);
        HBox.setHgrow(resetButton, Priority.ALWAYS);
        VBox actions = new VBox(8, runButton, secondaryActions);

        VBox content = new VBox(22, fileSection, randomSection, paramSection, actions);
        content.getStyleClass().add("sidebar-content");

        configControls.addAll(List.of(taskField, machineField, browseButton, loadButton,
                tasksSpinner, machinesSpinner, setupCombo, generateButton, iterSpinner, lfaSpinner));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("sidebar");
        scroll.setPrefWidth(330);
        scroll.setMinWidth(300);
        return scroll;
    }

    private Node buildContent() {
        instanceTitle = label("instance.none", "instance-title");
        instanceMeta = label("instance.hint", "instance-meta");
        VBox instanceBox = new VBox(2, instanceTitle, instanceMeta);

        kpiInitial = new Label("—");
        kpiBest = new Label("—");
        kpiGain = new Label("—");
        kpiGainSub = new Label(" ");
        kpiIter = new Label("—");
        kpiIterSub = new Label(" ");
        kpiTime = new Label("—");
        HBox kpis = new HBox(12,
                kpiCard("kpi.initial", kpiInitial, label("kpi.initial.sub", null), null),
                kpiCard("kpi.best", kpiBest, label("kpi.best.sub", null), "kpi-accent"),
                kpiCard("kpi.gain", kpiGain, kpiGainSub, "kpi-good"),
                kpiCard("kpi.iterations", kpiIter, kpiIterSub, null),
                kpiCard("kpi.time", kpiTime, label("kpi.time.sub", null), null));

        gantt = new GanttView();
        ScrollPane ganttScroll = new ScrollPane(gantt);
        ganttScroll.setFitToWidth(true);
        ganttScroll.setFitToHeight(true);
        ganttScroll.getStyleClass().add("gantt-scroll");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox ganttHeader = new HBox(18, label("gantt.title", "card-title"), spacer,
                legendItem("legend.task", 0), legendItem("legend.setup", 1), legendItem("legend.cmax", 2));
        ganttHeader.setAlignment(Pos.CENTER_LEFT);
        VBox ganttCard = card(ganttHeader, ganttScroll);
        VBox.setVgrow(ganttScroll, Priority.ALWAYS);
        ganttCard.setMinHeight(220);

        NumberAxis xAxis = new NumberAxis();
        xAxis.labelProperty().bind(I18n.bind("chart.x"));
        NumberAxis yAxis = new NumberAxis();
        yAxis.labelProperty().bind(I18n.bind("chart.y"));
        applyTickFormat(xAxis, yAxis);
        I18n.languageProperty().addListener((obs, old, lang) -> applyTickFormat(xAxis, yAxis));
        yAxis.setForceZeroInRange(false);
        LineChart<Number, Number> chart = new LineChart<>(xAxis, yAxis);
        chart.setAnimated(false);
        chart.setCreateSymbols(false);
        chart.setMinHeight(150);
        currentSeries = new XYChart.Series<>();
        currentSeries.nameProperty().bind(I18n.bind("chart.current"));
        bestSeries = new XYChart.Series<>();
        bestSeries.nameProperty().bind(I18n.bind("chart.best"));
        chart.getData().add(currentSeries);
        chart.getData().add(bestSeries);
        VBox chartCard = card(label("chart.title", "card-title"), chart);
        VBox.setVgrow(chart, Priority.ALWAYS);
        HBox.setHgrow(chartCard, Priority.ALWAYS);

        logView = new ListView<>();
        logView.getStyleClass().add("log");
        logView.setCellFactory(list -> new ListCell<>() {
            {
                setWrapText(true);
                prefWidthProperty().bind(list.widthProperty().subtract(24));
            }

            @Override
            protected void updateItem(LogEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(empty || entry == null ? null : entry.time() + "   " + t(entry.key(), entry.args()));
            }
        });
        VBox logCard = card(label("log.title", "card-title"), logView);
        VBox.setVgrow(logView, Priority.ALWAYS);
        logCard.setPrefWidth(400);
        logCard.setMinWidth(280);

        HBox bottom = new HBox(12, chartCard, logCard);
        bottom.setMinHeight(190);

        SplitPane split = new SplitPane(ganttCard, bottom);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.66);

        VBox content = new VBox(14, instanceBox, kpis, split);
        VBox.setVgrow(split, Priority.ALWAYS);
        content.getStyleClass().add("content");
        return content;
    }

    private Node buildStatusBar() {
        progressLabel = label("progress.waiting", "status-text");
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(280);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(12, progressLabel, spacer, progressBar);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    // ------------------------------------------------------------------ Chargement des instances

    private void browseInstance(Stage stage) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(t("chooser.title"));
        File instances = new File("Instances").getAbsoluteFile();
        chooser.setInitialDirectory(instances.isDirectory() ? instances : new File(".").getAbsoluteFile());
        File dir = chooser.showDialog(stage);
        if (dir == null) return;
        taskField.setText(new File(dir, "task.txt").getPath());
        machineField.setText(new File(dir, "machine").getPath());
        loadFromFields();
    }

    private void loadFromFields() {
        File taskFile = new File(taskField.getText().trim());
        File machineDir = new File(machineField.getText().trim());
        if (!taskFile.isFile() || !machineDir.isDirectory()) {
            showError("error.notFound", "error.notFound.body");
            return;
        }
        String name = taskFile.getAbsoluteFile().getParentFile().getName();
        loadInstance(taskFile.getPath(), machineDir.getPath(), "%s", name);
    }

    private void generateRandom() {
        commitSpinners();
        int n = tasksSpinner.getValue();
        int m = machinesSpinner.getValue();
        int setupMax = setupCombo.getValue();
        try {
            File dir = Files.createTempDirectory("gantt_random_").toFile();
            File taskFile = new File(dir, "task.txt");
            File machineDir = new File(dir, "machine");
            machineDir.mkdirs();
            Random rand = new Random();

            try (PrintWriter pw = new PrintWriter(taskFile, "UTF-8")) {
                pw.println(n + " " + m);
                for (int i = 0; i < n; i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < m; j++) sb.append(j > 0 ? " " : "").append(1 + rand.nextInt(99));
                    pw.println(sb);
                }
            }
            // Pas de ligne d'en-tête : Data lit directement la matrice n × n
            for (int k = 0; k < m; k++) {
                try (PrintWriter pw = new PrintWriter(new File(machineDir, "machine_" + k + ".txt"), "UTF-8")) {
                    for (int i = 0; i < n; i++) {
                        StringBuilder sb = new StringBuilder();
                        for (int j = 0; j < n; j++) sb.append(j > 0 ? " " : "").append(1 + rand.nextInt(setupMax));
                        pw.println(sb);
                    }
                }
            }

            taskField.setText(taskFile.getPath());
            machineField.setText(machineDir.getPath());
            loadInstance(taskFile.getPath(), machineDir.getPath(), "instance.random", n, m, setupMax);
        } catch (Exception ex) {
            showError("error.generate", "%s", String.valueOf(ex.getMessage()));
        }
    }

    /** Le nom de l'instance est donné sous forme de clé de traduction (ou "%s") et de ses arguments. */
    private void loadInstance(String taskPath, String machinePath, String nameKey, Object... nameArgs) {
        try {
            Data data = new Data(taskPath, machinePath);
            Solution loaded = new Solution(taskPath, machinePath);
            baseline = loaded.copy();
            solution = loaded;
            instanceTitle.textProperty().bind(I18n.bind(nameKey, nameArgs));
            instanceMeta.textProperty().bind(I18n.bind("instance.meta", data.getNumberTasks(), data.getNumberMachines()));
            resetRunState();
            log("log.loaded", new InstanceName(nameKey, nameArgs), solution.getC_max());
        } catch (Exception ex) {
            showError("error.load", "error.load.body", String.valueOf(ex));
        }
    }

    /** Nom d'instance traduit au moment de l'affichage du journal. */
    private record InstanceName(String key, Object[] args) {
        @Override
        public String toString() {
            return t(key, args);
        }
    }

    private void resetSolution() {
        if (baseline == null) return;
        solution = baseline.copy();
        resetRunState();
        log("log.reset");
    }

    private void resetRunState() {
        pendingPoints.clear();
        latestBest = null;
        bestSeries.getData().clear();
        currentSeries.getData().clear();
        kpiInitial.setText(fmt(baseline.getC_max()));
        updateBestKpis(solution.getC_max());
        kpiIter.setText("0");
        kpiIterSub.textProperty().bind(I18n.bind("kpi.iterations.none"));
        kpiTime.setText(formatDuration(0));
        progressBar.setProgress(0);
        progressLabel.textProperty().bind(I18n.bind("progress.ready"));
        gantt.setSolution(solution);
        setStatus("status.idle", "idle");
        updateControls();
    }

    // ------------------------------------------------------------------ Optimisation LAHC

    private void startOptimisation() {
        if (solution == null || running) return;
        commitSpinners();
        maxIterations = iterSpinner.getValue();
        int lfa = lfaSpinner.getValue();
        Solution start = solution.copy();
        start.computeTime();

        pendingPoints.clear();
        bestSeries.getData().clear();
        currentSeries.getData().clear();
        latestBest = null;
        latestIteration = 0;
        stopRequested = false;
        running = true;
        startNanos = System.nanoTime();
        kpiIterSub.textProperty().bind(I18n.bind("kpi.iterations.of", new I18n.Num(maxIterations)));
        progressLabel.textProperty().unbind();

        setStatus("status.running", "running");
        updateControls();
        log("log.start", new I18n.Num(maxIterations), lfa, start.getC_max());

        Thread worker = new Thread(() -> runLahc(start, maxIterations, lfa), "lahc-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void requestStop() {
        if (!running) return;
        stopRequested = true;
        stopButton.setDisable(true);
        log("log.stopRequested");
    }

    /** Late Acceptance Hill Climbing (même logique que Main.LAHC), exécuté hors du thread JavaFX. */
    private void runLahc(Solution start, int iterations, int lfa) {
        Solution best = start.copy();
        Solution curr = start.copy();
        best.computeTime();
        curr.computeTime();
        int[] history = new int[lfa];
        Arrays.fill(history, curr.getC_max());

        latestBest = best;
        latestCurrentCmax = curr.getC_max();
        pendingPoints.add(new int[]{0, best.getC_max(), curr.getC_max()});
        int sampleEvery = Math.max(1, iterations / 400);

        int i = 0;
        try {
            for (; i < iterations && !stopRequested; i++) {
                Solution next = curr.copy().variableNeighborhoodDescent();
                next.computeTime();

                int nextC = next.getC_max();
                if (nextC <= curr.getC_max() || nextC <= history[i % lfa]) {
                    curr = next;
                }
                boolean improved = false;
                if (curr.getC_max() < best.getC_max()) {
                    best = curr.copy();
                    best.computeTime();
                    latestBest = best;
                    improved = true;
                }
                history[i % lfa] = nextC;

                latestIteration = i + 1;
                latestCurrentCmax = curr.getC_max();
                if (improved || (i + 1) % sampleEvery == 0) {
                    pendingPoints.add(new int[]{i + 1, best.getC_max(), curr.getC_max()});
                }
                if (delayMs > 0) Thread.sleep(delayMs);
            }
        } catch (InterruptedException ignored) {
        } catch (Exception ex) {
            Platform.runLater(() -> showError("error.run", "%s", String.valueOf(ex)));
        }

        final Solution result = best;
        final int done = i;
        final boolean stopped = stopRequested;
        Platform.runLater(() -> finishOptimisation(result, done, stopped));
    }

    private void refreshLiveState() {
        int[] p;
        while ((p = pendingPoints.poll()) != null) {
            currentSeries.getData().add(new XYChart.Data<>(p[0], p[2]));
            bestSeries.getData().add(new XYChart.Data<>(p[0], p[1]));
        }
        if (!running) return;

        Solution best = latestBest;
        if (best != null && best != gantt.getSolution()) {
            solution = best;
            gantt.setSolution(best);
            updateBestKpis(best.getC_max());
        }
        int it = latestIteration;
        kpiIter.setText(fmt(it));
        kpiTime.setText(formatDuration((System.nanoTime() - startNanos) / 1_000_000));
        progressBar.setProgress(it / (double) maxIterations);
        progressLabel.setText(t("progress.running", fmt(it), fmt(maxIterations), latestCurrentCmax));
    }

    private void finishOptimisation(Solution result, int done, boolean stopped) {
        refreshLiveState();
        running = false;
        long elapsed = (System.nanoTime() - startNanos) / 1_000_000;

        solution = result;
        gantt.setSolution(result);
        updateBestKpis(result.getC_max());
        kpiIter.setText(fmt(done));
        kpiTime.setText(formatDuration(elapsed));
        progressBar.setProgress(done / (double) maxIterations);
        progressLabel.textProperty().bind(I18n.bind(stopped ? "progress.stopped" : "progress.done", new I18n.Num(done), result.getC_max()));
        setStatus(stopped ? "status.stopped" : "status.done", stopped ? "stopped" : "done");
        log(stopped ? "log.stopped" : "log.done", baseline.getC_max(), result.getC_max(), formatDuration(elapsed));
        updateControls();
    }

    // ------------------------------------------------------------------ Mise à jour de l'affichage

    private void updateBestKpis(int best) {
        int initial = baseline.getC_max();
        double gain = initial > 0 ? 100.0 * (initial - best) / initial : 0;
        kpiBest.setText(fmt(best));
        kpiGain.textProperty().bind(Bindings.createStringBinding(
                () -> String.format(I18n.locale(), "%.1f %%", gain), I18n.languageProperty()));
        kpiGainSub.textProperty().bind(initial == best
                ? I18n.bind("kpi.gain.none")
                : I18n.bind("kpi.gain.sub", new I18n.Num(initial - best)));
    }

    private static void applyTickFormat(NumberAxis... axes) {
        for (NumberAxis axis : axes) {
            axis.setTickLabelFormatter(new javafx.util.StringConverter<>() {
                @Override
                public String toString(Number n) {
                    return fmt(n.longValue());
                }

                @Override
                public Number fromString(String s) {
                    return Long.parseLong(s.replaceAll("[\\s,]", ""));
                }
            });
        }
    }

    private void updateControls() {
        boolean hasInstance = solution != null;
        for (Control c : configControls) c.setDisable(running);
        runButton.setDisable(running || !hasInstance);
        stopButton.setDisable(!running);
        resetButton.setDisable(running || !hasInstance);
    }

    private void setStatus(String key, String kind) {
        statusChip.textProperty().bind(I18n.bind("status.chip", new I18n.Key(key)));
        statusChip.getStyleClass().removeAll("idle", "running", "done", "stopped");
        statusChip.getStyleClass().add(kind);
    }

    private void log(String key, Object... args) {
        logView.getItems().add(new LogEntry(LocalTime.now().format(CLOCK), key, args));
        logView.scrollTo(logView.getItems().size() - 1);
    }

    private void showError(String titleKey, String bodyKey, Object... args) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(t(titleKey));
        alert.setHeaderText(t(titleKey));
        alert.setContentText(t(bodyKey, args));
        alert.show();
        log("log.error", new I18n.Key(titleKey));
    }

    private void commitSpinners() {
        for (Spinner<Integer> s : List.of(tasksSpinner, machinesSpinner, iterSpinner, lfaSpinner)) commitSpinner(s);
    }

    // ------------------------------------------------------------------ Fabriques de composants

    private static Label label(String key, String style) {
        Label l = new Label();
        l.textProperty().bind(I18n.bind(key));
        if (style != null) l.getStyleClass().add(style);
        return l;
    }

    private static Button button(String key, String style, javafx.event.EventHandler<javafx.event.ActionEvent> action) {
        Button b = new Button();
        b.textProperty().bind(I18n.bind(key));
        b.getStyleClass().add(style);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setOnAction(action);
        return b;
    }

    private static VBox section(String key, Node... children) {
        Label label = new Label();
        label.textProperty().bind(I18n.bindUpper(key));
        label.getStyleClass().add("section-title");
        VBox box = new VBox(10, label);
        box.getChildren().addAll(children);
        return box;
    }

    private static VBox field(String key, Node control) {
        if (control instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        VBox box = new VBox(5, label(key, "field-label"), control);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private static Spinner<Integer> intSpinner(int min, int max, int initial, int step) {
        Spinner<Integer> s = new Spinner<>(min, max, initial, step);
        s.setEditable(true);
        s.focusedProperty().addListener((obs, was, is) -> {
            if (!is) commitSpinner(s);
        });
        return s;
    }

    private static void commitSpinner(Spinner<Integer> s) {
        try {
            s.getValueFactory().setValue(Integer.parseInt(s.getEditor().getText().replaceAll("[\\s,.]", "")));
        } catch (NumberFormatException e) {
            s.getEditor().setText(String.valueOf(s.getValue()));
        }
    }

    private static VBox card(Node... children) {
        VBox box = new VBox(10, children);
        box.getStyleClass().add("card");
        return box;
    }

    private static VBox kpiCard(String key, Label value, Label sub, String extraStyle) {
        Label l = new Label();
        l.textProperty().bind(I18n.bindUpper(key));
        l.getStyleClass().add("kpi-label");
        value.getStyleClass().add("kpi-value");
        sub.getStyleClass().add("kpi-sub");
        VBox card = new VBox(3, l, value, sub);
        card.getStyleClass().addAll("card", "kpi");
        if (extraStyle != null) card.getStyleClass().add(extraStyle);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinWidth(150);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private static Node legendItem(String key, int kind) {
        Canvas swatch = new Canvas(20, 12);
        GraphicsContext g = swatch.getGraphicsContext2D();
        switch (kind) {
            case 0 -> {
                g.setFill(GanttView.taskColor(2));
                g.fillRoundRect(0, 0, 20, 12, 4, 4);
            }
            case 1 -> GanttView.drawSetup(g, 0, 2, 20, 8);
            default -> {
                g.setStroke(GanttView.CRITICAL);
                g.setLineWidth(2);
                g.setLineDashes(4, 3);
                g.strokeLine(10, 0, 10, 12);
            }
        }
        HBox box = new HBox(6, swatch, label(key, "legend-label"));
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    static String fmt(long v) {
        return I18n.formatNumber(v);
    }

    private static String formatDuration(long ms) {
        if (ms < 60_000) return String.format(I18n.locale(), "%.1f s", ms / 1000.0);
        return String.format(I18n.locale(), "%d min %02d s", ms / 60_000, (ms / 1000) % 60);
    }

    // ------------------------------------------------------------------ Vue Gantt

    static class GanttView extends Pane {
        static final Color BACKGROUND = Color.WHITE;
        static final Color ROW_ALT = Color.web("#f8fafc");
        static final Color GRID = Color.web("#e8edf3");
        static final Color AXIS = Color.web("#cbd5e1");
        static final Color TEXT = Color.web("#0f172a");
        static final Color MUTED = Color.web("#64748b");
        static final Color CRITICAL = Color.web("#e11d48");
        static final Color ACCENT = Color.web("#6366f1");
        static final Color SETUP_FILL = Color.web("#eef2f7");
        static final Color SETUP_LINE = Color.web("#94a3b8");

        private static final double GUTTER = 160, AXIS_H = 46, PAD_R = 36, PAD_B = 12;
        private static final double MIN_ROW = 28, MAX_ROW = 64;
        private static final Font AXIS_FONT = Font.font("System", 11);
        private static final Font LABEL_FONT = Font.font("System", FontWeight.BOLD, 12.5);
        private static final Font SMALL_FONT = Font.font("System", 11);
        private static final Font TASK_FONT = Font.font("System", FontWeight.BOLD, 10.5);

        private record Hit(double x, double y, double w, double h, int task, String text) {}

        private final Canvas canvas = new Canvas();
        private final Tooltip tooltip = new Tooltip();
        private final List<Hit> hits = new ArrayList<>();
        private Solution solution;
        private int hoveredTask = -1;

        GanttView() {
            canvas.setManaged(false);
            getChildren().add(canvas);
            tooltip.getStyleClass().add("gantt-tooltip");
            canvas.setOnMouseMoved(e -> onHover(e.getX(), e.getY(), e.getScreenX(), e.getScreenY()));
            canvas.setOnMouseExited(e -> {
                tooltip.hide();
                if (hoveredTask != -1) {
                    hoveredTask = -1;
                    redraw();
                }
            });
        }

        Solution getSolution() {
            return solution;
        }

        void setSolution(Solution s) {
            solution = s;
            int machines = s == null ? 0 : s.getNumberMachines();
            setMinHeight(AXIS_H + machines * MIN_ROW + PAD_B);
            redraw();
        }

        @Override
        protected double computePrefWidth(double height) {
            return 400;
        }

        @Override
        protected double computePrefHeight(double width) {
            return getMinHeight();
        }

        @Override
        protected void layoutChildren() {
            if (canvas.getWidth() != getWidth() || canvas.getHeight() != getHeight()) {
                canvas.setWidth(getWidth());
                canvas.setHeight(getHeight());
                redraw();
            }
        }

        private void onHover(double x, double y, double screenX, double screenY) {
            Hit found = null;
            for (Hit h : hits) {
                if (x >= h.x() && x <= h.x() + h.w() && y >= h.y() && y <= h.y() + h.h()) {
                    found = h;
                    break;
                }
            }
            int task = found == null ? -1 : found.task();
            if (task != hoveredTask) {
                hoveredTask = task;
                redraw();
            }
            if (found == null) {
                tooltip.hide();
            } else {
                tooltip.setText(found.text());
                tooltip.show(canvas, screenX + 14, screenY + 16);
            }
        }

        static Color taskColor(int task) {
            return Color.hsb((task * 137.508) % 360, 0.48, 0.90);
        }

        static void drawSetup(GraphicsContext g, double x, double y, double w, double h) {
            g.setFill(SETUP_FILL);
            g.fillRect(x, y, w, h);
            if (w < 3) return;
            g.save();
            g.beginPath();
            g.rect(x, y, w, h);
            g.closePath();
            g.clip();
            g.setStroke(SETUP_LINE);
            g.setLineWidth(1);
            for (double d = -h; d < w; d += 5) g.strokeLine(x + d, y + h, x + d + h, y);
            g.restore();
        }

        private static double snap(double v) {
            return Math.floor(v) + 0.5;
        }

        private static double niceStep(double range, int targetTicks) {
            double raw = range / targetTicks;
            double magnitude = Math.pow(10, Math.floor(Math.log10(raw)));
            double r = raw / magnitude;
            double nice = r < 1.5 ? 1 : r < 3 ? 2 : r < 7 ? 5 : 10;
            return Math.max(1, nice * magnitude);
        }

        void redraw() {
            double w = canvas.getWidth(), h = canvas.getHeight();
            GraphicsContext g = canvas.getGraphicsContext2D();
            hits.clear();
            if (w <= 0 || h <= 0) return;
            g.setFill(BACKGROUND);
            g.fillRect(0, 0, w, h);
            g.setTextBaseline(VPos.CENTER);

            if (solution == null) {
                g.setTextAlign(TextAlignment.CENTER);
                g.setFill(TEXT);
                g.setFont(Font.font("System", FontWeight.BOLD, 15));
                g.fillText(t("gantt.empty"), w / 2, h / 2 - 12);
                g.setFill(MUTED);
                g.setFont(SMALL_FONT);
                g.fillText(t("gantt.empty.hint"), w / 2, h / 2 + 12);
                return;
            }

            int machines = solution.getNumberMachines();
            int horizon = 1;
            for (int m = 0; m < machines; m++) horizon = Math.max(horizon, solution.getMachine(m).getC());

            double rowH = Math.max(MIN_ROW, Math.min(MAX_ROW, (h - AXIS_H - PAD_B) / machines));
            double plotW = Math.max(50, w - GUTTER - PAD_R);
            double step = niceStep(horizon, Math.max(2, (int) (plotW / 90)));
            double axisMax = Math.ceil(horizon / step) * step;
            double scale = plotW / axisMax;
            double plotBottom = AXIS_H + machines * rowH;

            // Lignes alternées
            for (int m = 0; m < machines; m++) {
                if (m % 2 == 1) {
                    g.setFill(ROW_ALT);
                    g.fillRect(0, AXIS_H + m * rowH, w, rowH);
                }
            }

            // Axe du temps et grille
            g.setFont(AXIS_FONT);
            g.setTextAlign(TextAlignment.CENTER);
            g.setLineWidth(1);
            for (double t = 0; t <= axisMax + 1e-9; t += step) {
                double x = GUTTER + t * scale;
                g.setStroke(GRID);
                g.strokeLine(snap(x), AXIS_H, snap(x), plotBottom);
                g.setFill(MUTED);
                g.fillText(fmt((long) t), x, AXIS_H - 11);
            }
            g.setStroke(AXIS);
            g.strokeLine(0, snap(AXIS_H), w, snap(AXIS_H));
            g.strokeLine(snap(GUTTER - 10), AXIS_H, snap(GUTTER - 10), plotBottom);
            g.setTextAlign(TextAlignment.LEFT);
            g.setFont(TASK_FONT);
            g.fillText(t("gantt.machine"), 18, AXIS_H - 11);

            for (int m = 0; m < machines; m++) {
                Machine machine = solution.getMachine(m);
                double y = AXIS_H + m * rowH;
                double barY = y + rowH * 0.17;
                double barH = rowH * 0.66;
                boolean critical = machine.getC() == horizon;

                // Libellés de la machine
                if (critical) {
                    g.setFill(CRITICAL);
                    g.fillOval(7, y + rowH / 2 - 3, 6, 6);
                }
                g.setTextAlign(TextAlignment.LEFT);
                g.setFont(LABEL_FONT);
                g.setFill(critical ? CRITICAL : TEXT);
                g.fillText("M" + m, 18, y + rowH / 2);
                g.setTextAlign(TextAlignment.RIGHT);
                g.setFont(SMALL_FONT);
                g.setFill(MUTED);
                g.fillText(t("gantt.row", machine.getNumberTasks(), fmt(machine.getC())), GUTTER - 20, y + rowH / 2);
                if (rowH >= 38) {
                    double loadW = GUTTER - 38;
                    g.setFill(GRID);
                    g.fillRoundRect(18, y + rowH - 9, loadW, 3, 3, 3);
                    g.setFill(critical ? CRITICAL : ACCENT);
                    g.fillRoundRect(18, y + rowH - 9, loadW * machine.getC() / horizon, 3, 3, 3);
                }

                // Tâches et temps de setup
                List<Integer> tasks = machine.getTasks();
                int time = 0;
                for (int k = 0; k < tasks.size(); k++) {
                    int task = tasks.get(k);
                    int duration = machine.getTime(task);
                    double x = GUTTER + time * scale;
                    double bw = Math.max(1.5, duration * scale);
                    Color color = taskColor(task);
                    boolean hovered = task == hoveredTask;

                    g.setGlobalAlpha(hoveredTask >= 0 && !hovered ? 0.35 : 1);
                    g.setFill(color);
                    g.fillRoundRect(x, barY, bw, barH, 5, 5);
                    g.setStroke(hovered ? TEXT : color.deriveColor(0, 1.1, 0.72, 1));
                    g.setLineWidth(hovered ? 2 : 1);
                    g.strokeRoundRect(x + 0.5, barY + 0.5, bw - 1, barH - 1, 5, 5);
                    String label = "T" + task;
                    if (bw > label.length() * 6.5 + 8) {
                        double lum = 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
                        g.setFill(lum > 0.6 ? TEXT : Color.WHITE);
                        g.setFont(TASK_FONT);
                        g.setTextAlign(TextAlignment.CENTER);
                        g.fillText(label, x + bw / 2, barY + barH / 2);
                    }
                    g.setGlobalAlpha(1);
                    hits.add(new Hit(x, barY, bw, barH, task,
                            t("gantt.tip.task", task, m, time, duration, time + duration)));
                    time += duration;

                    if (k < tasks.size() - 1) {
                        int next = tasks.get(k + 1);
                        int setup = machine.getService(task, next);
                        double sx = GUTTER + time * scale;
                        double sw = setup * scale;
                        if (sw > 0) {
                            drawSetup(g, sx, barY + barH * 0.25, sw, barH * 0.5);
                            hits.add(new Hit(sx, barY, sw, barH, -1,
                                    t("gantt.tip.setup", task, next, setup, time, time + setup)));
                        }
                        time += setup;
                    }
                }
            }

            // Ligne du C_max
            double cx = GUTTER + horizon * scale;
            g.setStroke(CRITICAL);
            g.setLineWidth(1.5);
            g.setLineDashes(6, 4);
            g.strokeLine(snap(cx), AXIS_H, snap(cx), plotBottom);
            g.setLineDashes(null);
            String chip = "C_max = " + fmt(horizon);
            double chipW = chip.length() * 6.6 + 14;
            double chipX = Math.min(w - chipW - 4, cx - chipW / 2);
            g.setFill(CRITICAL);
            g.fillRoundRect(chipX, 4, chipW, 18, 9, 9);
            g.setFill(Color.WHITE);
            g.setFont(TASK_FONT);
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(chip, chipX + chipW / 2, 13);
        }
    }
}
