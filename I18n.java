import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;

/** Traductions de l'interface graphique (français / anglais), changeables à chaud. */
public final class I18n {

    public enum Lang {
        FR("fr", "Français", Locale.FRANCE),
        EN("en", "English", Locale.UK);

        final String code;
        final String label;
        final Locale locale;

        Lang(String code, String label, Locale locale) {
            this.code = code;
            this.label = label;
            this.locale = locale;
        }
    }

    /** Argument à traduire au moment du formatage (utile pour les messages ré-affichés après un changement de langue). */
    public record Key(String name) {}

    /** Entier formaté au moment de l'affichage, avec le séparateur de milliers de la langue courante. */
    public record Num(long value) {
        @Override
        public String toString() {
            return formatNumber(value);
        }
    }

    private static final Map<Lang, Map<String, String>> TEXTS = new EnumMap<>(Lang.class);

    static {
        for (Lang lang : Lang.values()) TEXTS.put(lang, new HashMap<>());

        add("app.title", "Gantt Simulator — Ordonnancement LAHC", "Gantt Simulator — LAHC Scheduling");
        add("app.subtitle", "Ordonnancement sur machines parallèles avec temps de setup  ·  LAHC + VND",
                "Parallel machine scheduling with setup times  ·  LAHC + VND");
        add("lang.tooltip", "Langue de l'interface", "Interface language");

        add("status.chip", "●  %s", "●  %s");
        add("status.idle", "Prêt", "Ready");
        add("status.running", "Optimisation en cours", "Optimizing");
        add("status.done", "Terminée", "Completed");
        add("status.stopped", "Arrêtée", "Stopped");

        add("section.files", "Instance depuis fichiers", "Instance from files");
        add("field.taskFile", "Fichier des tâches", "Task file");
        add("field.machineDir", "Dossier des machines", "Machine folder");
        add("btn.browse", "Parcourir…", "Browse…");
        add("btn.load", "Charger", "Load");
        add("section.random", "Instance aléatoire", "Random instance");
        add("field.tasks", "Tâches", "Tasks");
        add("field.machines", "Machines", "Machines");
        add("field.setup", "Temps de setup (1 – k)", "Setup times (1 – k)");
        add("btn.generate", "Générer une instance", "Generate instance");
        add("section.lahc", "Paramètres LAHC", "LAHC parameters");
        add("field.iterations", "Itérations max", "Max iterations");
        add("field.lfa", "Longueur Lfa", "Lfa length");
        add("field.delay", "Ralenti par itération", "Delay per iteration");
        add("btn.run", "▶   Lancer l'optimisation", "▶   Run optimization");
        add("btn.stop", "■  Arrêter", "■  Stop");
        add("btn.reset", "↺  Réinitialiser", "↺  Reset");

        add("instance.none", "Aucune instance", "No instance");
        add("instance.hint", "Chargez des fichiers ou générez une instance aléatoire pour commencer.",
                "Load files or generate a random instance to get started.");
        add("instance.meta", "%d tâches  ·  %d machines  ·  solution initiale par insertion gloutonne",
                "%d tasks  ·  %d machines  ·  initial solution by greedy insertion");
        add("instance.random", "Aléatoire · n%d m%d setup%d", "Random · n%d m%d setup%d");

        add("kpi.initial", "C_max initial", "Initial C_max");
        add("kpi.initial.sub", "solution gloutonne", "greedy solution");
        add("kpi.best", "Meilleur C_max", "Best C_max");
        add("kpi.best.sub", "meilleure solution trouvée", "best solution found");
        add("kpi.gain", "Amélioration", "Improvement");
        add("kpi.gain.none", "aucun gain pour l'instant", "no gain yet");
        add("kpi.gain.sub", "−%s unités de temps", "−%s time units");
        add("kpi.iterations", "Itérations", "Iterations");
        add("kpi.iterations.none", "aucune exécution", "not run yet");
        add("kpi.iterations.of", "sur %s", "of %s");
        add("kpi.time", "Temps écoulé", "Elapsed time");
        add("kpi.time.sub", "dernière exécution", "last run");

        add("gantt.title", "Diagramme de Gantt", "Gantt chart");
        add("legend.task", "Tâche", "Task");
        add("legend.setup", "Setup", "Setup");
        add("legend.cmax", "C_max", "C_max");
        add("gantt.empty", "Aucune instance chargée", "No instance loaded");
        add("gantt.empty.hint", "Chargez des fichiers ou générez une instance aléatoire depuis le panneau de gauche.",
                "Load files or generate a random instance from the left panel.");
        add("gantt.machine", "MACHINE", "MACHINE");
        add("gantt.row", "%d t.  ·  %s", "%d tasks  ·  %s");
        add("gantt.tip.task", "Tâche T%d  ·  machine M%d\nDébut : %d\nDurée : %d\nFin : %d",
                "Task T%d  ·  machine M%d\nStart: %d\nDuration: %d\nEnd: %d");
        add("gantt.tip.setup", "Setup T%d → T%d\nDurée : %d\nDe %d à %d",
                "Setup T%d → T%d\nDuration: %d\nFrom %d to %d");

        add("chart.title", "Convergence", "Convergence");
        add("chart.x", "Itération", "Iteration");
        add("chart.y", "C_max", "C_max");
        add("chart.current", "Solution courante", "Current solution");
        add("chart.best", "Meilleure solution", "Best solution");
        add("log.title", "Journal", "Log");

        add("progress.waiting", "En attente d'une instance", "Waiting for an instance");
        add("progress.ready", "Instance prête — lancez l'optimisation", "Instance ready — run the optimization");
        add("progress.running", "Itération %s / %s   ·   C_max courant %d", "Iteration %s / %s   ·   current C_max %d");
        add("progress.done", "Terminée après %s itérations   ·   meilleur C_max %d",
                "Completed after %s iterations   ·   best C_max %d");
        add("progress.stopped", "Arrêtée après %s itérations   ·   meilleur C_max %d",
                "Stopped after %s iterations   ·   best C_max %d");

        add("log.ready", "Prêt. Chargez une instance ou générez-en une aléatoire.",
                "Ready. Load an instance or generate a random one.");
        add("log.loaded", "Instance « %s » chargée (C_max initial = %d)", "Instance “%s” loaded (initial C_max = %d)");
        add("log.reset", "Solution réinitialisée à la solution initiale", "Solution reset to the initial solution");
        add("log.start", "Lancement LAHC : %s itérations, Lfa = %d, C_max de départ = %d",
                "LAHC started: %s iterations, Lfa = %d, starting C_max = %d");
        add("log.stopRequested", "Arrêt demandé…", "Stop requested…");
        add("log.done", "Optimisation terminée : C_max %d → %d en %s", "Optimization completed: C_max %d → %d in %s");
        add("log.stopped", "Optimisation arrêtée : C_max %d → %d en %s", "Optimization stopped: C_max %d → %d in %s");
        add("log.error", "Erreur : %s", "Error: %s");

        add("error.notFound", "Fichiers introuvables", "Files not found");
        add("error.notFound.body", "Vérifiez le chemin du fichier des tâches et du dossier des machines.",
                "Check the task file path and the machine folder path.");
        add("error.generate", "Génération impossible", "Generation failed");
        add("error.load", "Erreur de chargement", "Loading error");
        add("error.load.body", "Impossible de lire l'instance : %s", "Could not read the instance: %s");
        add("error.run", "Erreur pendant l'optimisation", "Error during optimization");
        add("chooser.title", "Dossier d'instance (contenant task.txt et machine/)",
                "Instance folder (containing task.txt and machine/)");
    }

    private static final ObjectProperty<Lang> LANGUAGE = new SimpleObjectProperty<>(initialLanguage());

    static {
        LANGUAGE.addListener((obs, old, lang) -> {
            try {
                prefs().put("language", lang.code);
            } catch (Exception ignored) {
            }
        });
    }

    private I18n() {
    }

    private static void add(String key, String fr, String en) {
        TEXTS.get(Lang.FR).put(key, fr);
        TEXTS.get(Lang.EN).put(key, en);
    }

    private static Preferences prefs() {
        return Preferences.userNodeForPackage(I18n.class);
    }

    private static Lang initialLanguage() {
        try {
            String saved = prefs().get("language", null);
            for (Lang lang : Lang.values()) if (lang.code.equals(saved)) return lang;
        } catch (Exception ignored) {
        }
        return "fr".equals(Locale.getDefault().getLanguage()) ? Lang.FR : Lang.EN;
    }

    public static ObjectProperty<Lang> languageProperty() {
        return LANGUAGE;
    }

    public static Locale locale() {
        return LANGUAGE.get().locale;
    }

    /** Entier avec séparateur de milliers selon la langue (5 000 / 5,000). */
    public static String formatNumber(long value) {
        String s = String.format(Locale.ROOT, "%,d", value);
        return LANGUAGE.get() == Lang.FR ? s.replace(',', ' ') : s;
    }

    public static String t(String key, Object... args) {
        String text = TEXTS.get(LANGUAGE.get()).getOrDefault(key, key);
        if (args.length == 0) return text;
        Object[] resolved = args.clone();
        for (int i = 0; i < resolved.length; i++) {
            if (resolved[i] instanceof Key k) resolved[i] = t(k.name());
        }
        return String.format(locale(), text, resolved);
    }

    /** Texte qui se met à jour automatiquement lors d'un changement de langue. */
    public static StringBinding bind(String key, Object... args) {
        return Bindings.createStringBinding(() -> t(key, args), LANGUAGE);
    }

    public static StringBinding bindUpper(String key) {
        return Bindings.createStringBinding(() -> t(key).toUpperCase(locale()), LANGUAGE);
    }
}
