package qa.sijill.sijill;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Guards the modular-monolith structure: the module dependency graph must stay <b>acyclic</b>, and the
 * {@code shared} module must not depend on any business module.
 *
 * <p>A module is the first package under {@code qa.sijill.sijill} (files, issues, rules, …). If this test
 * fails, a new import made two modules depend on each other; break the cycle with an event or a port
 * (see {@code issues.event.FileRechecker} and {@code shared.events}) instead of adding the import.
 */
class ModuleStructureTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/qa/sijill/sijill");
    private static final Pattern IMPORT = Pattern.compile("^import qa\\.sijill\\.sijill\\.(\\w+)\\.", Pattern.MULTILINE);

    @Test
    void moduleDependenciesHaveNoCycles() throws IOException {
        Map<String, Set<String>> graph = dependencyGraph();

        List<String> cycles = new ArrayList<>();
        Set<String> finished = new HashSet<>();
        for (String module : graph.keySet()) {
            findCycles(module, new ArrayList<>(List.of(module)), graph, finished, cycles);
        }
        assertThat(cycles).as("module dependency cycles").isEmpty();
    }

    @Test
    void sharedModuleDependsOnNoBusinessModule() throws IOException {
        assertThat(dependencyGraph().getOrDefault("shared", Set.of()))
                .as("shared must not import business modules")
                .isEmpty();
    }

    private void findCycles(String module, List<String> path, Map<String, Set<String>> graph,
                            Set<String> finished, List<String> cycles) {
        for (String dependency : graph.getOrDefault(module, Set.of())) {
            int at = path.indexOf(dependency);
            if (at >= 0) {
                cycles.add(String.join(" -> ", path.subList(at, path.size())) + " -> " + dependency);
            } else if (!finished.contains(dependency)) {
                path.add(dependency);
                findCycles(dependency, path, graph, finished, cycles);
                path.remove(path.size() - 1);
            }
        }
        finished.add(module);
    }

    /** module -> modules it imports from, read straight from the source files. */
    private Map<String, Set<String>> dependencyGraph() throws IOException {
        Map<String, Set<String>> graph = new HashMap<>();
        try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                Path relative = SOURCE_ROOT.relativize(file);
                if (relative.getNameCount() < 2) {
                    continue; // classes in the root package (the application class)
                }
                String module = relative.getName(0).toString();
                Matcher m = IMPORT.matcher(Files.readString(file));
                while (m.find()) {
                    if (!m.group(1).equals(module)) {
                        graph.computeIfAbsent(module, k -> new HashSet<>()).add(m.group(1));
                    }
                }
            }
        }
        return graph;
    }
}
