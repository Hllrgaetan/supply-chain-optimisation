import java.util.ArrayList;
import java.util.List;

public class Machine{
    
    public static final int INFINI = 1000000;

    private int id_machine;
    private List<Integer> tasks_in_machine;
    private List<Integer> time;
    private List<List<Integer>> service;
    private int C;

    public Machine(int id_machine, List<Integer> time, List<List<Integer>> service){
        this.id_machine = id_machine;
        this.tasks_in_machine = new ArrayList<>();
        this.time = time;
        this.service = service;
        this.C = 0;
    }

    public Machine(Machine machine) {
        this.id_machine = machine.id_machine;
        this.time = machine.time;
        this.service = machine.service;
        this.C = machine.C;
        this.tasks_in_machine = new ArrayList<>(machine.tasks_in_machine);  
    }

    public Machine copy() {
        return new Machine(this);
    }

    public int getID() {return this.id_machine;}
    public List<Integer> getTasks() {return this.tasks_in_machine;}
    public List<Integer> getTime() {return this.time;}
    public List<List<Integer>> getService() {return this.service;}
    public int getTime(int id_task) {return time.get(id_task);}
    public int getService(int id_previous_task, int id_next_task) {return service.get(id_previous_task).get(id_next_task);}

    public int getTask(int position_i) {return this.tasks_in_machine.get(position_i);}
    public int getNumberTasks() {return this.tasks_in_machine.size();}
    public int getC() {return this.C;}

    public void setTasks(List<Integer> tasks) {
        this.tasks_in_machine = new ArrayList<>(tasks); 
        computeTime();
    }

    
    public int isTaskinMachine(int task_id){
        for(int i = 0; i < getNumberTasks(); i++){
            if (getTask(i) == task_id){
                return i;
            }
        }
        return -1;
    }  

    public void computeTime(){
        if (getNumberTasks() == 0){
            this.C = 0;
            return ;
        }
        int C_machine = 0;
        for(int i = 0; i < getNumberTasks()-1 ; i++){
            C_machine += getTime(getTask(i)) + getService(getTask(i), getTask(i+1));
        }
        
        this.C = C_machine +  getTime(getTask(getNumberTasks()-1));
    }

   public void addTask(int id_task, int id_position){
        this.tasks_in_machine.add(id_position, id_task);
        computeTime();
    }

    public int addTask(int id_task){
        int best_insertion = 0;
        int best_C_max = INFINI;
        for(int i = 0 ; i < getNumberTasks()+1 ; i++){
            addTask(id_task, i);

            if (this.C < best_C_max){
                best_insertion = i;
                best_C_max = this.C;
            }
           subTask(id_task,i);
        }
        addTask(id_task, best_insertion);
        return best_insertion;
    }

    public int subTask(int id_task){
        for(int i = 0; i < getNumberTasks(); i++){
            if (getTask(i) == id_task){
                this.tasks_in_machine.remove(i);
                computeTime();
                return id_machine;
            }
        }
        return -1;
    }

    public void subTask(int id_task, int id_position){
        // override of function subtask, id_task is useless since we know the position of the task in the machine
        this.tasks_in_machine.remove(id_position);
        computeTime();
    }
}