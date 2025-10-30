import java.util.ArrayList;
import java.util.List;
import java.io.FileNotFoundException;
import java.util.Random;

public class Solution{
    public static final int INFINI = 1000000;

    private Data data;
    private int C_max;
    private List<Machine> machines;

    public Solution(String file_task_machine, String folder_machine){
        try {
            this.data = new Data(file_task_machine, folder_machine);
        } catch (FileNotFoundException e) {
            System.out.println("File not found when creating Data: " + e.getMessage());
            e.printStackTrace();
        }
        this.machines = new ArrayList<>();
        for(int id_machine = 0; id_machine < this.data.getNumberMachines(); id_machine++){
            this.machines.add(new Machine(id_machine, this.data.getTime(id_machine), this.data.getService(id_machine)));
        }
        for(int id_task  = 0; id_task < data.getNumberTasks(); id_task++){
            addTask(id_task);
        }
        computeTime();
    }

    public Solution(Solution solution) {
        this.data = solution.data;
        this.C_max = solution.C_max;
        this.machines = new ArrayList<>();
        for (Machine m : solution.getMachines()) {
            this.machines.add(new Machine(m));
        }
    }

    public Solution copy() {
        return new Solution(this);
    }

    public Data getData() {return this.data;}
    public List<Machine> getMachines() {return this.machines;}
    public Machine getMachine(int id_machine) {return this.machines.get(id_machine);}
    public int getNumberMachines() {return this.machines.size();}
    public int getC_max() {return this.C_max;}
    
    public void subTask(int id_task, int id_machine, int id_position){
        getMachine(id_machine).subTask(id_task, id_position);
    }

    public void subTask(int id_task, int id_machine){
        getMachine(id_machine).subTask(id_task);
    }

    public void subTask(int id_task){
        for(int id_machine = 0; id_machine < getNumberMachines(); id_machine++){
            int position = getMachine(id_machine).isTaskinMachine(id_task);
            if (position != -1){
                getMachine(id_machine).subTask(id_task, position);
            }
        }
    }

    public void addTask(int id_task, int id_machine, int id_position){
        getMachine(id_machine).addTask(id_task, id_position);
    }

    public void addTask(int id_task, int id_machine){
        getMachine(id_machine).addTask(id_task);
    }
 
    public void addTask(int id_task){
        int best_position = 0;
        int best_machine = 0;
        int best_C_max = INFINI;
        int position;
        for(int id_machine = 0; id_machine < getNumberMachines(); id_machine++){
            position = getMachine(id_machine).addTask(id_task);
            if (getMachine(id_machine).getC() < best_C_max){
                best_position = position;
                best_machine = id_machine;
                best_C_max = getMachine(id_machine).getC();
            }
            getMachine(id_machine).subTask(id_task, position);
        }
        getMachine(best_machine).addTask(id_task, best_position);
    }

    public void computeTime(){
        int worst_C = 0;
        for(int id_machine = 0; id_machine < getNumberMachines(); id_machine++ ){
            if (getMachine(id_machine).getC() > worst_C){
                worst_C = getMachine(id_machine).getC();
            }
        }
        C_max = worst_C;
    }

    public void internalSwap(int pos_task_min, int pos_task_max, int id_machine){
            int task2 = getMachine(id_machine).getTasks().remove(pos_task_max);
            int task1 = getMachine(id_machine).getTasks().remove(pos_task_min);           
            getMachine(id_machine).addTask(task2, pos_task_min);
            getMachine(id_machine).addTask(task1, pos_task_max);
    }

    public void externalSwap(int pos_task_1, int pos_task_2, int id_machine_1, int id_machine_2){
            int task2 = getMachine(id_machine_2).getTasks().remove(pos_task_2);
            int task1 = getMachine(id_machine_1).getTasks().remove(pos_task_1);
            getMachine(id_machine_1).addTask(task2, pos_task_1);
            getMachine(id_machine_2).addTask(task1, pos_task_2);
    }

    public void externalInsertion(int task_id, int machine_id_from, int machine_id_to){
            getMachine(machine_id_from).subTask(task_id);
            getMachine(machine_id_to).addTask(task_id);
    }

    public void internalSwapExploration(int id_machine) {
         if (getMachine(id_machine).getNumberTasks() > 1){
        Machine machine = getMachine(id_machine);
        
        int initial_C = machine.getC();
        int best_C = INFINI;
        int best_i = 0;
        int best_j = 0;

        int n = machine.getNumberTasks();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                internalSwap(i, j, id_machine); 
    
                if (machine.getC() < best_C) {
                    best_C = machine.getC();
                    best_i = i;
                    best_j = j; 
                }
                internalSwap(i,j, id_machine); 
            }
        }
        internalSwap(best_i,best_j, id_machine); 
        computeTime();  
        }
    }

    public static int randomIntExcluding(int bound, int excluded) {
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < bound; i++) {
            if (i != excluded) {
                candidates.add(i);
            }
        }

        Random rand = new Random();
        int index = rand.nextInt(candidates.size()); 
        return candidates.get(index);
    }

    public void externalSwapExploration(int id_machine){
        Machine machine = getMachine(id_machine);
        Machine machine2 = getMachine(randomIntExcluding(getNumberMachines(), id_machine));
        if (machine2.getNumberTasks() > 0){
            
            int initial_C = machine.getC();
            int best_C = INFINI;
            int best_i = 0;;
            int best_j = 0;;

            for (int i = 0; i < machine.getNumberTasks(); i++) {
                for (int j = i + 1; j < machine2.getNumberTasks(); j++) {
                    externalSwap(i, j, id_machine, machine2.getID());  
                    if (machine.getC() < best_C) {
                        best_C = machine.getC();
                        best_i = i;
                        best_j = j; 
                    }
                    externalSwap(i,j, id_machine, machine2.getID()); 
                }
            }
            externalSwap(best_i, best_j, id_machine, machine2.getID());  
            computeTime();
        }
    }

    public void externalInsertionExploration(int id_machine){
        Machine machine = getMachine(id_machine);
        Machine machine2 = getMachine(randomIntExcluding(getNumberMachines(), id_machine));
        
        int best_task = machine.getTask(0);
        int best_position = machine2.addTask(machine.getTask(0)); 
        int best_C = machine2.getC();
        machine2.subTask(machine.getTask(0), best_position);
        int position;

        for (int i = 1; i < machine.getNumberTasks(); i++) {          
            position = machine2.addTask(machine.getTask(i));
    
            if (machine2.getC() < best_C) {
                best_C = machine2.getC();
                best_position = position;
                best_task = machine.getTask(i);
            }
            machine2.subTask(machine.getTask(i), position);           
        }
        machine2.addTask(best_task, best_position);  
        machine.subTask(best_task);
        computeTime();
    }

    public void balancing(){
        int worst_machine = 0;
        int best_machine = 0;
        for(int id_machine = 0; id_machine < this.data.getNumberMachines(); id_machine++){
            if (getMachine(worst_machine).getC() < getMachine(id_machine).getC()){
                worst_machine = id_machine;
            }
            if (getMachine(best_machine).getC() > getMachine(id_machine).getC()){
                best_machine = id_machine;
            }
        }

        while (getMachine(worst_machine).getC() > getMachine(best_machine).getC()) {
            int insertion_cost = INFINI;
            int position_best_insertion = 0;
            int best_task = 0;
            int position; 
            int old_position = 0;
            for(int i = 0; i < getMachine(worst_machine).getNumberTasks(); i++){
                position = getMachine(best_machine).addTask(getMachine(worst_machine).getTask(i));
                if (getMachine(best_machine).getC() < insertion_cost){
                    insertion_cost = getMachine(best_machine).getC();
                    position_best_insertion = position;
                    best_task = getMachine(worst_machine).getTask(i);
                    old_position = i;
                }
                getMachine(best_machine).subTask(getMachine(worst_machine).getTask(i),position);
            }
            getMachine(best_machine).addTask(best_task,position_best_insertion);
            getMachine(worst_machine).subTask(best_task, old_position);
            getMachine(worst_machine).computeTime();
            computeTime();
        }        
    }

    public void interMachineInsertion(){
        for(int id_task = 0; id_task < this.data.getNumberTasks(); id_task++){
            subTask(id_task); 
            addTask(id_task);
            computeTime();
        }
    }

    public Solution variableNeighborhoodDescent() {
        Solution currentSolution = this.copy(); 
        Random rand = new Random();
        int c = 0;
        while (c < 5) {
            Solution candidate = currentSolution.copy();
            int worstMachine = candidate.getWorstMachine();
            switch (c) {
                case 0:
                    candidate.internalSwapExploration(worstMachine);
                    break;
                case 1:
                    candidate.interMachineInsertion();
                    break;
                case 2:
                    candidate.externalInsertionExploration(worstMachine);
                    break;
                case 3:
                    candidate.externalSwapExploration(worstMachine);
                    break;
                case 4:
                    candidate.balancing();
                    break;
            }
            candidate.computeTime();

            if (candidate.getC_max() < currentSolution.getC_max()) {
                currentSolution = candidate;
                c = 0;
            } else {
                c++;
            }
            if (rand.nextDouble() < 0.1){
                return candidate;
            }
        }
        
    
        return currentSolution;
    }

    public int getWorstMachine(){
        for(int id_machine = 0; id_machine < getNumberMachines(); id_machine++){
            if (getMachine(id_machine).getC() == getC_max()){
                return id_machine;
            }
        }
        return -1;
    }
}

