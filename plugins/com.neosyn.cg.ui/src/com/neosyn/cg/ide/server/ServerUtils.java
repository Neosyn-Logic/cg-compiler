/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * Copyright (c) Neosyn. C⏚ ("C-Ground") is the open-source continuation
 * of the Synflow Cx toolchain, originally developed by Synflow.
 */
package com.neosyn.cg.ide.server;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.eclipse.emf.common.util.URI;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;

import com.google.inject.Provider;

/**
 * Shared utility methods for the Cx Language Server.
 *
 * Contains debug logging, path manipulation, and resource loading utilities.
 */
public final class ServerUtils {

    /** Enable verbose IR debug logging. Set -Dneosyn.debug.ir=true to enable. */
    private static final boolean DEBUG_ENABLED = Boolean.parseBoolean(
            System.getProperty("neosyn.debug.ir", "false"));

    private static final String LOG_FILE = System.getProperty("user.home") + "/neosyn-ir-debug.log";

    private ServerUtils() {
        // Utility class - no instantiation
    }

    /**
     * Log a debug message to the debug log file.
     * Thread-safe file writing with timestamps.
     */
    public static synchronized void debugLog(String message) {
        if (!DEBUG_ENABLED) return;
        try (PrintWriter pw = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            pw.println("[" + LocalDateTime.now() + "] " + message);
        } catch (Exception e) {
            // Ignore logging failures
        }
    }

    /**
     * Find the project root directory from a file path.
     * Looks for .project file or src/ directory marker; failing those, derives
     * the package root from the file's own {@code package} declaration so that
     * cross-directory imports resolve (see {@link #findPackageRoot}).
     *
     * @param filePath Path to a file within the project
     * @return Project root path, or null if not found
     */
    public static String findProjectRoot(String filePath) {
        Path path = Paths.get(filePath);
        Path current = path.getParent();

        while (current != null) {
            Path projectFile = current.resolve(".project");
            if (Files.exists(projectFile)) {
                return current.toString();
            }

            // Check for src/ as marker if no .project
            if (current.getFileName() != null && "src".equals(current.getFileName().toString())) {
                return current.getParent().toString();
            }

            current = current.getParent();
        }

        // No .project / src marker: derive the root from the package declaration.
        // When directory names mirror the trailing package segments (e.g.
        // .../sha256/step1/Foo.cg with package com.neosyn.sha256.step1), the true
        // root is the parent-package directory (.../sha256/) — otherwise a sibling
        // bundle there (com.neosyn.sha256.SHACommon in .../sha256/SHACommon.cg) is
        // never loaded into the resource set and its imported symbols stay
        // unresolved proxies (null Variable → NPE in the transform).
        Path packageRoot = findPackageRoot(path);
        if (packageRoot != null) {
            return packageRoot.toString();
        }

        // Fallback: use parent of file directory
        return path.getParent() != null ? path.getParent().toString() : null;
    }

    /** Name of the per-project dependency manifest (one dependency root path per line). */
    public static final String DEPS_MANIFEST = "cg.deps";

    /**
     * Resolve the full set of source roots to compile for a project: the primary
     * root plus every project listed (transitively) in {@code cg.deps} manifests.
     *
     * <p>This is how a project cross-references another without copying sources.
     * A {@code cg.deps} file at a project root lists one dependency project root
     * per line (path relative to that manifest, or absolute; blank lines and
     * {@code #} comments ignored). Each referenced root's {@code .cg} files are
     * then loaded into the same resource set, so an {@code import} of a package
     * defined in a sibling project resolves exactly like an intra-project import.
     * Transitive and cycle-safe; the primary root is always first.
     */
    public static List<String> resolveSourceRoots(String primaryRoot) {
        List<String> roots = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(primaryRoot);
        while (!queue.isEmpty()) {
            String root = queue.poll();
            if (root == null) {
                continue;
            }
            Path rp;
            try {
                rp = Paths.get(root).toRealPath();
            } catch (Exception e) {
                rp = Paths.get(root).toAbsolutePath().normalize();
            }
            if (!visited.add(rp.toString())) {
                continue;
            }
            roots.add(rp.toString());

            Path manifest = rp.resolve(DEPS_MANIFEST);
            if (Files.isRegularFile(manifest)) {
                try {
                    for (String line : Files.readAllLines(manifest)) {
                        String entry = line.trim();
                        if (entry.isEmpty() || entry.startsWith("#")) {
                            continue;
                        }
                        Path dep = Paths.get(entry);
                        if (!dep.isAbsolute()) {
                            dep = rp.resolve(entry);
                        }
                        queue.add(dep.normalize().toString());
                    }
                } catch (Exception e) {
                    debugLog("[ROOT] Failed to read " + manifest + ": " + e.getMessage());
                }
            }
        }
        return roots;
    }

    /**
     * Collect the URIs of every {@code .cg} file under the given source roots
     * (hidden directories excluded), de-duplicated and order-preserving. Used to
     * build the compile set spanning a project and its {@code cg.deps}
     * dependencies.
     */
    public static List<String> collectCgUris(List<String> roots) {
        Set<String> uris = new LinkedHashSet<>();
        for (String root : roots) {
            Path rp = Paths.get(root);
            if (!Files.isDirectory(rp)) {
                continue;
            }
            try (Stream<Path> paths = Files.walk(rp)) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".cg"))
                        .filter(p -> !p.toString().contains(File.separator + "."))
                        .forEach(p -> uris.add(p.toUri().toString()));
            } catch (Exception e) {
                debugLog("[ROOT] Failed to walk source root " + root + ": " + e.getMessage());
            }
        }
        return new ArrayList<>(uris);
    }

    /**
     * Derives the package root directory for a {@code .cg} file by walking up
     * from the file's directory while each directory's name equals the
     * corresponding trailing segment of the file's {@code package} declaration.
     *
     * <p>For {@code .../sha256/step1/Foo.cg} declaring
     * {@code package com.neosyn.sha256.step1}, the {@code step1} and {@code sha256}
     * directories match the two trailing segments, so the root is {@code .../sha256/}.
     * When the immediate directory name does not match the last package segment
     * (no directory/package mirroring), this returns the file's own directory —
     * identical to the previous fallback, so unrelated layouts are unaffected.
     *
     * @param file path to a .cg file
     * @return the package root directory, or the file's directory if no package
     *         declaration is found
     */
    private static Path findPackageRoot(Path file) {
        Path dir = file.getParent();
        if (dir == null) {
            return null;
        }
        String pkg = readPackageName(file);
        if (pkg == null || pkg.isEmpty()) {
            return dir;
        }
        String[] segments = pkg.split("\\.");
        Path root = dir;
        Path cursor = dir;
        for (int i = segments.length - 1; i >= 0; i--) {
            if (cursor == null || cursor.getFileName() == null
                    || !cursor.getFileName().toString().equals(segments[i])) {
                break;
            }
            root = cursor;
            cursor = cursor.getParent();
        }
        return root;
    }

    /**
     * Reads the {@code package} declaration from a {@code .cg} file (the first
     * line beginning with {@code "package "}). Returns null if none is found.
     */
    private static String readPackageName(Path file) {
        try {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("package ")) {
                    String rest = trimmed.substring("package ".length()).trim();
                    int semi = rest.indexOf(';');
                    if (semi >= 0) {
                        rest = rest.substring(0, semi);
                    }
                    return rest.trim();
                }
            }
        } catch (Exception e) {
            debugLog("[ROOT] Failed to read package from " + file + ": " + e.getMessage());
        }
        return null;
    }

    /**
     * Check if a directory contains .cg files.
     */
    public static boolean hasCgFilesInSrc(Path srcDir) {
        if (!Files.exists(srcDir)) {
            return false;
        }
        try (var stream = Files.walk(srcDir)) {
            return stream.anyMatch(p -> p.toString().endsWith(".cg"));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Clean a directory by deleting all files recursively.
     */
    public static void cleanDirectory(String dirPath) {
        Path dir = Paths.get(dirPath);
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before dirs
                .forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (Exception e) {
                        debugLog("[CLEAN] Failed to delete: " + p + " - " + e.getMessage());
                    }
                });
        } catch (Exception e) {
            debugLog("[CLEAN] Failed to clean directory: " + dirPath + " - " + e.getMessage());
        }
    }

    /**
     * Convert a URI string to a filesystem path.
     * Handles both file:// URIs and regular paths.
     */
    public static String convertUriToPath(String uriString) {
        if (uriString == null) {
            return null;
        }
        if (uriString.startsWith("file:")) {
            try {
                return Paths.get(java.net.URI.create(uriString)).toString();
            } catch (Exception e) {
                debugLog("[URI] Failed to convert URI: " + uriString + " - " + e.getMessage());
                // Strip file:// or file:/ prefix as fallback
                if (uriString.startsWith("file://")) {
                    return uriString.substring(7);
                }
                return uriString.substring(5);
            }
        }
        return uriString;
    }

    /**
     * Get an XtextResource from the given URI using the provided resource set.
     *
     * @param uri URI of the resource
     * @param resourceSetProvider Provider for XtextResourceSet
     * @return The loaded XtextResource, or null if not found
     */
    public static XtextResource getResource(URI uri, Provider<XtextResourceSet> resourceSetProvider) {
        try {
            XtextResourceSet resourceSet = resourceSetProvider.get();
            resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
            return (XtextResource) resourceSet.getResource(uri, true);
        } catch (Exception e) {
            debugLog("[RESOURCE] Failed to load: " + uri + " - " + e.getMessage());
            return null;
        }
    }
}
