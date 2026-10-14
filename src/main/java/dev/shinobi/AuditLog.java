package dev.shinobi;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.logging.Level;

/**
 * Append-only JSON-lines audit log of administrative changes ({@code plugins/ShinobiSMP/audit/audit.jsonl}).
 * Rotates by size and keeps a bounded number of old files. A logging failure is reported but never blocks the command.
 */
public final class AuditLog {
    private final ShinobiPlugin plugin;
    private final Path dir;
    private final Path file;

    public AuditLog(ShinobiPlugin plugin) {
        this.plugin = plugin;
        this.dir = plugin.getDataFolder().toPath().resolve("audit");
        this.file = dir.resolve("audit.jsonl");
    }

    public void record(AuditEntry e) {
        if (!plugin.getConfig().getBoolean("admin.audit.enabled", true)) return;
        try {
            Files.createDirectories(dir);
            rotateIfNeeded();
            Files.writeString(file, e.toJson() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not write the audit log (change " + e.action() + " was still applied)", ex);
        }
        plugin.getLogger().info("[audit] " + e.adminName() + " " + e.action() + " " + e.targetName() + " " + e.previous() + " -> " + e.now()
                + (e.forced() ? " (forced)" : "") + (e.success() ? "" : " FAILED: " + e.reason()));
    }

    private void rotateIfNeeded() throws IOException {
        long max = Math.max(10_000L, plugin.getConfig().getLong("admin.audit.max-bytes", 1_000_000L));
        if (!Files.exists(file) || Files.size(file) < max) return;
        Files.move(file, dir.resolve("audit-" + Instant.now().toEpochMilli() + ".jsonl"));
        int keep = Math.max(1, plugin.getConfig().getInt("admin.audit.keep-files", 10));
        try (Stream<Path> s = Files.list(dir)) {
            List<Path> old = new ArrayList<>(s.filter(p -> p.getFileName().toString().startsWith("audit-")).sorted().toList());
            while (old.size() > keep) Files.deleteIfExists(old.remove(0));
        }
    }

    /** Last {@code n} entries (newest last) from the current file. */
    public List<String> tail(int n) {
        try {
            if (!Files.exists(file)) return List.of();
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            return lines.subList(Math.max(0, lines.size() - Math.max(1, Math.min(50, n))), lines.size());
        } catch (IOException | RuntimeException ex) {
            return List.of("(could not read audit log: " + ex.getMessage() + ")");
        }
    }
}
