import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        String folder;
        if (args.length < 1) {
            folder = "Instances/small_n6_m2_setup9_rep1";
        }
        else{
            folder = args[0];
        }

        
        String taskFilePath = folder + "/task.txt";
        String machineFolderPath = folder + "/machine";

        int maxIterations = 50000;
        int LFa = 1000;

        try {
            Data data = new Data(taskFilePath, machineFolderPath);
            Solution solution = new Solution(taskFilePath, machineFolderPath);

            int initialCmax = solution.getC_max();
            long startTime = System.currentTimeMillis();
            solution = LAHC(solution, maxIterations, LFa);
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime; 

            int finalCmax = solution.getC_max();

            System.out.println("Instance: " + folder);
            System.out.println("C_max initial: " + initialCmax);
            System.out.println("C_max final: " + finalCmax);
            System.out.println("LFa: " + LFa + ", maxIterations: " + maxIterations);
            System.out.println("Temps d'exécution: " + executionTime + " ms");
            
            File csvFile = new File("results.csv");
            boolean writeHeader = !csvFile.exists() || csvFile.length() == 0;

            try (FileWriter fw = new FileWriter(csvFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {

                if (writeHeader) {
                    pw.println("Instance,Cmax_initial,Cmax_final,LFa,itermax,Temps_execution(ms)");
                }

                pw.println(folder + "," + initialCmax + "," + finalCmax + "," + LFa + "," + maxIterations + "," + executionTime);
            } catch (IOException e) {
                System.out.println("Erreur lors de l'écriture du CSV: " + e.getMessage());
            }

        } catch (IOException e) {
            System.out.println("Fichier non trouvé ou erreur lecture: " + e.getMessage());
        }
    }

    public static Solution LAHC(Solution solution, int maxIterations, int LFa) {
        Random rand = new Random();

        Solution best_sol = solution.copy();
        Solution curr_sol = solution.copy();
        best_sol.computeTime();
        curr_sol.computeTime();

        int[] f = new int[LFa];
        for (int k = 0; k < LFa; k++) {
            f[k] = curr_sol.getC_max();
        }

        int i = 0;
        while (i < maxIterations) {
            Solution next_sol = curr_sol.copy();
            next_sol = next_sol.variableNeighborhoodDescent();
            next_sol.computeTime();

            int currC = curr_sol.getC_max();
            int nextC = next_sol.getC_max();
            int hVal = f[i % LFa];

            if (nextC <= currC || nextC <= hVal) {
                curr_sol = next_sol.copy();
                curr_sol.computeTime();
            }

            if (curr_sol.getC_max() < best_sol.getC_max()) {
                best_sol = curr_sol.copy();
                best_sol.computeTime();
            }

            f[i%LFa] = nextC;
            i++;
        }

        return best_sol;
    }
}
