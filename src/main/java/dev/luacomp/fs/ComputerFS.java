package dev.luacomp.fs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Sistema de arquivos de um computador: uma pasta no mundo, com caminhos sanitizados
 * (nunca sai da pasta raiz) e cota de espaço.
 */
public class ComputerFS {
    public static final long QUOTA = 1_000_000L;

    private record Mount(Path root, long quota) {}

    private final Path root;
    private final Map<String, Mount> mounts = new ConcurrentHashMap<>();
    private final Set<Runnable> writers = ConcurrentHashMap.newKeySet();

    public ComputerFS(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new FSException("Cannot create computer folder");
        }
    }

    // ---------- caminhos ----------

    public static String clean(String path, boolean strict) {
        ArrayDeque<String> parts = new ArrayDeque<>();
        for (String s : path.replace('\\', '/').split("/")) {
            if (s.isEmpty() || s.equals(".")) continue;
            if (s.equals("..")) {
                if (!parts.isEmpty()) parts.removeLast();
                else if (strict) throw new FSException("Invalid path");
                continue;
            }
            if (s.length() > 128) throw new FSException("Invalid path");
            for (char c : s.toCharArray()) {
                if (c < 32 || "\":*?<>|".indexOf(c) >= 0) throw new FSException("Invalid path");
            }
            parts.addLast(s);
        }
        return String.join("/", parts);
    }

    private Path resolve(String path) {
        String c = clean(path, true);
        if (!c.isEmpty()) {
            int slash = c.indexOf('/');
            String first = slash < 0 ? c : c.substring(0, slash);
            Mount m = mounts.get(first);
            if (m != null) {
                String rest = slash < 0 ? "" : c.substring(slash + 1);
                Path p = rest.isEmpty() ? m.root() : m.root().resolve(rest).normalize();
                if (!p.startsWith(m.root())) throw new FSException("Invalid path");
                return p;
            }
        }
        Path p = c.isEmpty() ? root : root.resolve(c).normalize();
        if (!p.startsWith(root)) throw new FSException("Invalid path");
        return p;
    }

    /** Pasta raiz (do computador ou de um disquete) a que um caminho resolvido pertence. */
    private Mount mountOf(Path p) {
        for (Mount m : mounts.values()) if (p.startsWith(m.root())) return m;
        return new Mount(root, QUOTA);
    }

    private boolean isProtected(Path p) {
        if (p.equals(root)) return true;
        for (Mount m : mounts.values()) if (p.equals(m.root())) return true;
        return false;
    }

    public void mount(String name, Path mountRoot, long quota) {
        Path r = mountRoot.toAbsolutePath().normalize();
        try {
            Files.createDirectories(r);
        } catch (IOException e) {
            throw new FSException("Cannot create disk folder");
        }
        mounts.put(name, new Mount(r, quota));
    }

    public void unmount(String name) {
        mounts.remove(name);
    }

    // ---------- consultas ----------

    public boolean exists(String p) { return Files.exists(resolve(p)); }

    public boolean isDir(String p) { return Files.isDirectory(resolve(p)); }

    public long size(String p) {
        Path f = resolve(p);
        if (!Files.exists(f)) throw new FSException("No such file");
        try {
            return Files.isDirectory(f) ? 0 : Files.size(f);
        } catch (IOException e) {
            throw new FSException("Cannot read file size");
        }
    }

    public List<String> list(String p) {
        Path d = resolve(p);
        if (!Files.isDirectory(d)) throw new FSException("Not a directory");
        try (Stream<Path> s = Files.list(d)) {
            List<String> names = new ArrayList<>(s.map(x -> x.getFileName().toString()).toList());
            if (clean(p, true).isEmpty()) {
                for (String m : mounts.keySet()) if (!names.contains(m)) names.add(m);
            }
            names.sort(String::compareTo);
            return names;
        } catch (IOException e) {
            throw new FSException("Cannot list directory");
        }
    }

    public long used(Path base) {
        try (Stream<Path> s = Files.walk(base)) {
            return s.filter(Files::isRegularFile).mapToLong(f -> {
                try { return Files.size(f); } catch (IOException e) { return 0; }
            }).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    public long freeSpace(String p) {
        Mount m = mountOf(resolve(p));
        return Math.max(0, m.quota() - used(m.root()));
    }

    // ---------- modificações ----------

    public void makeDir(String p) {
        try {
            Files.createDirectories(resolve(p));
        } catch (FileAlreadyExistsException e) {
            throw new FSException("File exists");
        } catch (IOException e) {
            throw new FSException("Cannot create directory");
        }
    }

    public void delete(String p) {
        Path f = resolve(p);
        if (isProtected(f)) throw new FSException("Access denied");
        if (Files.exists(f)) deleteRec(f);
    }

    private void deleteRec(Path f) {
        try {
            if (Files.isDirectory(f)) {
                try (Stream<Path> s = Files.list(f)) {
                    for (Path c : s.toList()) deleteRec(c);
                }
            }
            Files.delete(f);
        } catch (IOException e) {
            throw new FSException("Failed to delete");
        }
    }

    public void move(String from, String to) {
        Path a = resolve(from), b = resolve(to);
        if (isProtected(a) || isProtected(b)) throw new FSException("Access denied");
        if (!Files.exists(a)) throw new FSException("No such file");
        if (Files.exists(b)) throw new FSException("File exists");
        try {
            if (b.getParent() != null) Files.createDirectories(b.getParent());
            Files.move(a, b);
        } catch (IOException e) {
            throw new FSException("Failed to move");
        }
    }

    public void copy(String from, String to) {
        Path a = resolve(from), b = resolve(to);
        if (!Files.exists(a)) throw new FSException("No such file");
        if (Files.exists(b)) throw new FSException("File exists");
        if (b.startsWith(a)) throw new FSException("Cannot copy into itself");
        copyRec(a, b);
    }

    private void copyRec(Path a, Path b) {
        try {
            if (Files.isDirectory(a)) {
                Files.createDirectories(b);
                try (Stream<Path> s = Files.list(a)) {
                    for (Path c : s.toList()) copyRec(c, b.resolve(c.getFileName().toString()));
                }
            } else {
                Mount dest = mountOf(b);
                if (used(dest.root()) + Files.size(a) > dest.quota()) throw new FSException("Out of space");
                if (b.getParent() != null) Files.createDirectories(b.getParent());
                Files.copy(a, b);
            }
        } catch (IOException e) {
            throw new FSException("Failed to copy");
        }
    }

    // ---------- conteúdo ----------

    public String read(String p) {
        Path f = resolve(p);
        if (!Files.isRegularFile(f)) throw new FSException("No such file");
        try {
            return new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FSException("Failed to read file");
        }
    }

    public void write(String p, String content) {
        Path f = resolve(p);
        if (isProtected(f) || Files.isDirectory(f)) throw new FSException("Cannot write to a directory");
        if (f.getParent() == null || !Files.isDirectory(f.getParent())) throw new FSException("Parent directory does not exist");
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        try {
            long existing = Files.isRegularFile(f) ? Files.size(f) : 0;
            Mount dest = mountOf(f);
            if (used(dest.root()) - existing + data.length > dest.quota()) throw new FSException("Out of space");
            Files.write(f, data);
        } catch (IOException e) {
            throw new FSException("Failed to write file");
        }
    }

    // ---------- arquivos abertos para escrita (gravados ao desligar se esquecerem de fechar) ----------

    public void registerWriter(Runnable flusher) { writers.add(flusher); }

    public void unregisterWriter(Runnable flusher) { writers.remove(flusher); }

    public void flushAll() {
        for (Runnable r : writers) {
            try { r.run(); } catch (RuntimeException ignored) { }
        }
        writers.clear();
    }
}
