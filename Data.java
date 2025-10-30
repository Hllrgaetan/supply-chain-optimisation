import java.util.ArrayList;
import java.util.List;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Data {
    private List<List<Integer>> task_machine;
    private List<List<List<Integer>>> serviceTime;
    private int numberTasks;
    private int numberMachines;

    public Data(String file_task_machine, String folder_machine) throws FileNotFoundException {
        File fichier = new File(file_task_machine);
        Scanner scanner = new Scanner(fichier);

        this.numberTasks = scanner.nextInt(); 
        this.numberMachines = scanner.nextInt(); 
        this.task_machine = new ArrayList<>();
        for (int i = 0; i < this.numberTasks; i++) {
            List<Integer> ligne = new ArrayList<>();
            for (int j = 0; j < this.numberMachines; j++) {
                if (scanner.hasNextInt()) {
                    ligne.add(scanner.nextInt());
                }
            }
            this.task_machine.add(ligne);
        }
        scanner.close();

        this.serviceTime = new ArrayList<>();

        for (int k = 0; k < this.numberMachines; k++) {
            String cheminFichier = folder_machine + "/machine_" + k + ".txt";
            File file = new File(cheminFichier);
            Scanner sc = new Scanner(file);
            List<List<Integer>> matriceMachine = new ArrayList<>(); 
            for (int i = 0; i < this.numberTasks; i++) {
                List<Integer> ligne = new ArrayList<>();
                for (int j = 0; j < this.numberTasks; j++) {
                    if (sc.hasNextInt()) {
                        ligne.add(sc.nextInt());
                    }
                }
                matriceMachine.add(ligne);
            }
            this.serviceTime.add(matriceMachine);
            sc.close();
        }
    }
    
    public List<Integer> getTime(int id_machine) {
        List<Integer> result = new ArrayList<>();
        for(int id_task = 0; id_task < getNumberTasks(); id_task++){
            result.add(this.task_machine.get(id_task).get(id_machine));
        }
        return result;
    }
       
    public List<List<Integer>>  getService(int id_machine) {
        return this.serviceTime.get(id_machine);
    }

    public int getTime(int id_task, int id_machine) {
        return this.task_machine.get(id_task).get(id_machine);
    }

    public int getServiceTime(int id_previous_task, int id_next_task, int id_machine) {
        return this.serviceTime.get(id_machine).get(id_previous_task).get(id_next_task);
    }

    public int getNumberTasks() {
        return this.numberTasks;
    }

    public int getNumberMachines() {
        return this.numberMachines;
    }
}
