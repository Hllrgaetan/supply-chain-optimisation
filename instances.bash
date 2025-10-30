#!/bin/bash


javac Solution.java Machine.java Data.java Main.java

if [ $? -ne 0 ]; then
    echo "Compilation échouée !"
    exit 1
fi


small_n=(6 8 10 12)
small_m=(2 3 4 5)
large_n=(50 100 150 200 250)
large_m=(10 15 20 25 30)
setup_ranges=(9 49 99 124)
n_replicates=10

max_jobs=7   

run_instance() {
    instance_dir="$1"
    echo "=============================================="
    echo "Lancement de l'instance: $instance_dir"
    echo "----------------------------------------------"
    java Main "$instance_dir"
    echo "----------------------------------------------"
    echo ""
}

# Tableau pour toutes les instances
instances=()

# Petites instances
for n in "${small_n[@]}"; do
    for m in "${small_m[@]}"; do
        for setup in "${setup_ranges[@]}"; do
            for rep in $(seq 1 $n_replicates); do
                instance_dir="Instances/small_n${n}_m${m}_setup${setup}_rep${rep}"
                if [ -d "$instance_dir" ]; then
                    instances+=("$instance_dir")
                fi
            done
        done
    done
done

# Lancer les instances en parallèle (max 7 jobs)
running_jobs=0
for instance_dir in "${instances[@]}"; do
    run_instance "$instance_dir" &   # lancer en arrière-plan
    ((running_jobs++))
    if (( running_jobs >= max_jobs )); then
        wait -n    # attendre qu'un job se termine avant de lancer le suivant
        ((running_jobs--))
    fi
done

# Attendre que tous les jobs se terminent
wait
echo "Toutes les instances sont terminées."
