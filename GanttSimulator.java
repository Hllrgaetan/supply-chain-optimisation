import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.canvas.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import java.util.List;
import java.io.File;
import java.util.Random;

public class GanttSimulator extends Application {

    private Canvas canvas;
    private Solution solution;
    private TextField taskFileField;
    private TextField machineFolderField;
    private Button loadButton;
    private Button randomButton;
    private Button startButton;
    private ProgressBar progressBar; 

    private static final double LEFT_MARGIN = 80;
    private static final double V_GAP = 12;
    private static final double TASK_HEIGHT = 24;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        taskFileField = new TextField("path/to/task.txt");
        taskFileField.setPrefWidth(360);
        machineFolderField = new TextField("path/to/machine_folder");
        machineFolderField.setPrefWidth(360);

        loadButton = new Button("Load from files");
        randomButton = new Button("Generate random instance");
        startButton = new Button("Start LAHC (when instance loaded)");

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(200); 

        HBox fileBox = new HBox(8,
                new Label("Task file:"), taskFileField,
                new Label("Machine folder:"), machineFolderField,
                loadButton);
        HBox controlBox = new HBox(8, randomButton, startButton, progressBar);

        canvas = new Canvas(1200, 700);
        VBox root = new VBox(10, fileBox, controlBox, canvas);
        VBox.setVgrow(canvas, Priority.ALWAYS);

        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty().subtract(100));

        loadButton.setOnAction(e -> {
            String taskPath = taskFileField.getText().trim();
            String machineFolder = machineFolderField.getText().trim();
            try {
                File tf = new File(taskPath);
                File mf = new File(machineFolder);
                if (!tf.exists() || !mf.exists()) {
                    showError("File/folder not found", "Check the task file path and machine folder path.");
                    return;
                }
                solution = new Solution(taskPath, machineFolder);
                drawGanttFromSolution();
            } catch (Exception ex) {
                ex.printStackTrace();
                showError("Loading error", "Could not load instance: " + ex.getMessage());
            }
        });

        randomButton.setOnAction(e -> {
            TextInputDialog d1 = new TextInputDialog("100");
            d1.setTitle("Random instance");
            d1.setHeaderText("Number of tasks");
            d1.setContentText("Enter number of tasks:");
            int n = Integer.parseInt(d1.showAndWait().orElse("100"));

            TextInputDialog d2 = new TextInputDialog("10");
            d2.setTitle("Random instance");
            d2.setHeaderText("Number of machines");
            d2.setContentText("Enter number of machines:");
            int m = Integer.parseInt(d2.showAndWait().orElse("10"));

            generateRandomSolution(n, m);
            drawGanttFromSolution();
        });

        startButton.setOnAction(e -> {
            if (solution == null) {
                showError("No instance", "Load an instance or generate a random one first.");
                return;
            }
            startButton.setDisable(true);
            progressBar.setProgress(0);
            new Thread(() -> {
                Solution before = solution.copy();
                final int initialCmax = before.getC_max();
                Platform.runLater(() -> System.out.println("Initial C_max: " + initialCmax));

                solution = LAHC(solution, 1000, 50); 

                final int finalCmax = solution.getC_max();
                Platform.runLater(() -> {
                    drawGanttFromSolution(); 
                    progressBar.setProgress(1.0);
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("LAHC finished");
                    alert.setHeaderText("Optimization completed");
                    alert.setContentText("Initial C_max: " + initialCmax + "\nFinal C_max: " + finalCmax);
                    alert.showAndWait();
                    startButton.setDisable(false);
                });
            }).start();
        });

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Gantt Simulator (using Solution / Machine) - LAHC");
        primaryStage.setMaximized(true);
        primaryStage.show();
    }

    private void drawGanttFromSolution() {
        Platform.runLater(() -> {
            if (solution == null) {
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
                return;
            }

            int M = solution.getNumberMachines();
            double w = canvas.getWidth();
            double h = canvas.getHeight();

            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.clearRect(0, 0, w, h);

            int globalMax = 0;
            for (int m = 0; m < M; m++) {
                solution.getMachine(m).computeTime();
                globalMax = Math.max(globalMax, solution.getMachine(m).getC());
            }
            double availableWidth = Math.max(200, w - LEFT_MARGIN - 20);
            double xScale = globalMax > 0 ? (availableWidth / (double) globalMax) : 1.0;

            double y = 20;
            gc.setFill(Color.BLACK);
            gc.fillText("C_max (global): " + solution.getC_max(), 10, 12);

            for (int m = 0; m < M; m++) {
                Machine mach = solution.getMachine(m);
                String label = "Machine " + m + " (C=" + mach.getC() + ")";
                gc.setFill(Color.BLACK);
                gc.fillText(label, 10, y + TASK_HEIGHT * 0.75);

                double x = LEFT_MARGIN;
                List<Integer> tasks = mach.getTasks();
                for (int taskId : tasks) {
                    int p = mach.getTime(taskId);
                    double width = Math.max(2, p * xScale);

                    gc.setFill(Color.hsb((taskId * 40) % 360, 0.65, 0.9));
                    gc.fillRect(x, y, width, TASK_HEIGHT);

                    gc.setFill(Color.BLACK);
                    gc.fillText("T" + taskId + " (" + p + ")", x + 3, y + TASK_HEIGHT * 0.75);

                    x += width + 4;
                }
                y += TASK_HEIGHT + V_GAP;
            }
        });
    }

    private void generateRandomSolution(int nTasks, int nMachines) {
        try {
            File tmpDir = new File(System.getProperty("java.io.tmpdir"), "gantt_sim_tmp");
            if (!tmpDir.exists()) tmpDir.mkdirs();
            File taskFile = new File(tmpDir, "task.txt");
            File machineFolder = new File(tmpDir, "machine");
            if (!machineFolder.exists()) machineFolder.mkdirs();

            java.io.PrintWriter pwTask = new java.io.PrintWriter(taskFile, "UTF-8");
            pwTask.println(nTasks + " " + nMachines);
            Random rand = new Random();
            for (int i = 0; i < nTasks; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < nMachines; j++) {
                    sb.append(1 + rand.nextInt(99));
                    if (j < nMachines - 1) sb.append(" ");
                }
                pwTask.println(sb.toString());
            }
            pwTask.close();

            for (int m = 0; m < nMachines; m++) {
                File mf = new File(machineFolder, "machine_" + m + ".txt");
                java.io.PrintWriter pw = new java.io.PrintWriter(mf, "UTF-8");
                pw.println(nTasks + " " + nTasks);
                for (int i = 0; i < nTasks; i++) {
                    StringBuilder sb = new StringBuilder();
                    for (int j = 0; j < nTasks; j++) {
                        sb.append(rand.nextInt(10));
                        if (j < nTasks - 1) sb.append(" ");
                    }
                    pw.println(sb.toString());
                }
                pw.close();
            }

            solution = new Solution(taskFile.getAbsolutePath(), machineFolder.getAbsolutePath());
        } catch (Exception ex) {
            ex.printStackTrace();
            showError("Error generating random instance", ex.getMessage());
            solution = null;
        }
    }

    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert a = new Alert(Alert.AlertType.ERROR);
            a.setTitle(title);
            a.setHeaderText(null);
            a.setContentText(content);
            a.showAndWait();
        });
    }

    public Solution LAHC(Solution solution, int maxIterations, int LFa) {
        Solution best_sol = solution.copy();
        Solution curr_sol = solution.copy();
        best_sol.computeTime();
        curr_sol.computeTime();
        int[] H = new int[LFa];
        for (int k = 0; k < LFa; k++) H[k] = curr_sol.getC_max();

        boolean improved = false;

        for (int i = 0; i < maxIterations; i++) {
            Solution next_sol = curr_sol.copy();
            next_sol = next_sol.variableNeighborhoodDescent();
            next_sol.computeTime();

            int currC = curr_sol.getC_max();
            int nextC = next_sol.getC_max();
            int hVal = H[i % LFa];

            if (nextC <= currC || nextC <= hVal) {
                curr_sol = next_sol.copy();
                curr_sol.computeTime();
            }

            if (curr_sol.getC_max() < best_sol.getC_max()) {
                best_sol = curr_sol.copy();
                best_sol.computeTime();
                improved = true;

                Solution snapshot = curr_sol.copy();
                final double progress = (i + 1) / (double) maxIterations;
                Platform.runLater(() -> {
                    this.solution = snapshot;
                    drawGanttFromSolution(); 
                    progressBar.setProgress(progress);
                });
            }

            H[i % LFa] = nextC;

            final double progress = (i + 1) / (double) maxIterations;
            Platform.runLater(() -> progressBar.setProgress(progress));

            improved = false;

            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }

        Platform.runLater(() -> progressBar.setProgress(1.0));
        return best_sol;
    }
}
