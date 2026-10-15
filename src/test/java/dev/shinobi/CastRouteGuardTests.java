package dev.shinobi;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Structural guard: technique effects may only be invoked from AbilityEngine, which performs the clan, bloodline,
 * progression, cooldown and chakra checks. Any new code that calls an effect directly (a bypass) fails this test.
 * Run from the project directory (Gradle's default working dir).
 */
public final class CastRouteGuardTests {
    public static void main(String[] args) throws IOException {
        Path src = Path.of("src", "main", "java", "dev", "shinobi");
        List<String> offenders = new ArrayList<>();
        int engineHits = 0;
        try (Stream<Path> files = Files.list(src)) {
            for (Path f : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String name = f.getFileName().toString();
                String text = Files.readString(f);
                for (String needle : new String[]{".effect.cast(", ".chargeEffect.release("}) {
                    int idx = text.indexOf(needle);
                    while (idx >= 0) {
                        if (name.equals("AbilityEngine.java")) engineHits++;
                        else offenders.add(name + " calls " + needle);
                        idx = text.indexOf(needle, idx + 1);
                    }
                }
            }
        }
        // Susanoo must stay independent: its code may not touch cooldowns, charging sessions, the casting engine or the
        // whole-player effect cleanup (which would cancel unrelated charges).
        String sus = Files.readString(src.resolve("SusanooManager.java"));
        for (String banned : new String[]{".cooldowns", "charging()", "effects().cleanup", "casting()", "abilities()", "ledger"}) {
            if (sus.contains(banned)) offenders.add("SusanooManager.java touches '" + banned + "'");
        }
        int failed = 0;
        for (String o : offenders) { System.out.println("FAIL: bypass of AbilityEngine: " + o); failed++; }
        if (engineHits < 2) { System.out.println("FAIL: expected the engine to invoke effect and chargeEffect (found " + engineHits + ")"); failed++; }
        System.out.println((8 + offenders.size()) + " route/independence checks, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
