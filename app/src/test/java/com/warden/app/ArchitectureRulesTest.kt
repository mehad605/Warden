package com.warden.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Automated architecture & maintainability boundary tests (S3.1, S3.3, S3.4).
 *
 * Enforces strict layering (`domain` <- `data`/`service`/`ui`), prevents circular package
 * dependencies, bans global mutable singletons / static `Context` holders / `!!` / `lateinit`,
 * and caps source files at 300 lines.
 */
class ArchitectureRulesTest {
    private val mainSrcDir: File = locateMainSourceDir()

    private fun locateMainSourceDir(): File =
        listOf(
            File("src/main/java/com/warden/app"),
            File("app/src/main/java/com/warden/app"),
        ).first { it.isDirectory }

    private fun allMainKotlinFiles(): List<File> = mainSrcDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.sorted().toList()

    private fun packageOf(file: File): String =
        file.useLines { lines ->
            lines.firstOrNull { it.startsWith("package ") }
                ?.removePrefix("package ")
                ?.trim()
                .orEmpty()
        }

    private fun importsOf(file: File): List<String> =
        file.useLines { lines ->
            lines.filter { it.startsWith("import ") }
                .map { it.removePrefix("import ").trim() }
                .toList()
        }

    private fun isForbiddenDomainImport(imp: String): Boolean {
        if (imp.startsWith("com.warden.app.data.models.")) return false
        return imp.startsWith("android.") ||
            imp.startsWith("androidx.") ||
            imp.contains(".ui.") ||
            imp.contains(".data.") ||
            imp.contains(".service.") ||
            imp.contains(".services.")
    }

    @Test
    fun domainPackagesMustNotDependOnAndroidOrUiOrDataOrService() {
        val violations =
            allMainKotlinFiles()
                .filter { packageOf(it).endsWith(".domain") }
                .flatMap { file ->
                    val pkg = packageOf(file)
                    importsOf(file)
                        .filter(::isForbiddenDomainImport)
                        .map { imp -> "${file.name} ($pkg) imports forbidden $imp" }
                }
        assertTrue("Domain layering violations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun dataLayerMustNotDependOnUiOrServiceLayer() {
        val violations =
            allMainKotlinFiles()
                .filter {
                    val pkg = packageOf(it)
                    pkg.endsWith(".data") || pkg.endsWith(".data.models")
                }
                .flatMap { file ->
                    val pkg = packageOf(file)
                    importsOf(file)
                        .filter { it.contains(".ui.") || it.contains(".service.") || it.contains(".services.") }
                        .map { imp -> "${file.name} ($pkg) imports UI/service $imp" }
                }
        assertTrue("Data layering violations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun noCircularPackageDependencies() {
        val files = allMainKotlinFiles()
        val knownPackages = files.map(::packageOf).toSet()
        val graph = buildPackageDependencyGraph(files, knownPackages)
        val cycles = detectCycles(graph, knownPackages)
        assertTrue("Circular package dependencies found:\n${cycles.joinToString("\n")}", cycles.isEmpty())
    }

    private fun buildPackageDependencyGraph(
        files: List<File>,
        knownPackages: Set<String>,
    ): Map<String, Set<String>> {
        val graph = mutableMapOf<String, MutableSet<String>>()
        val generatedRootSymbols = setOf("com.warden.app.R", "com.warden.app.BuildConfig")
        files.forEach { file ->
            val fromPkg = packageOf(file)
            val edges = graph.getOrPut(fromPkg) { mutableSetOf() }
            importsOf(file)
                .filter { it.startsWith("com.warden.app.") && it !in generatedRootSymbols }
                .map { it.substringBeforeLast('.') }
                .filter { it != fromPkg && it in knownPackages }
                .forEach(edges::add)
        }
        return graph
    }

    private fun detectCycles(
        graph: Map<String, Set<String>>,
        nodes: Set<String>,
    ): List<String> {
        val state = mutableMapOf<String, Int>()
        val cycles = mutableListOf<String>()

        fun dfs(
            node: String,
            path: MutableList<String>,
        ) {
            state[node] = 1
            path.add(node)
            graph[node].orEmpty().forEach { next ->
                when (state[next] ?: 0) {
                    1 -> cycles.add((path.dropWhile { it != next } + next).joinToString(" -> "))
                    0 -> dfs(next, path)
                }
            }
            path.removeAt(path.lastIndex)
            state[node] = 2
        }

        nodes.sorted().forEach { pkg ->
            if ((state[pkg] ?: 0) == 0) dfs(pkg, mutableListOf())
        }
        return cycles
    }

    @Test
    fun noMainFileExceeds300LinesAndNoUnsafeNotNullOrLateinit() {
        val violations = mutableListOf<String>()
        val lateinitRegex = Regex("\\blateinit\\s+var\\b")
        allMainKotlinFiles().forEach { file ->
            val lines = file.readLines()
            if (lines.size > 300) violations.add("${file.name} has ${lines.size} lines (max 300)")
            lines.forEachIndexed { idx, line ->
                val trimmed = line.trim()
                val isComment = trimmed.startsWith("//") || trimmed.startsWith("*")
                if (!isComment && trimmed.contains("!!")) violations.add("${file.name}:${idx + 1} uses unsafe !!")
                if (!isComment && lateinitRegex.containsMatchIn(trimmed)) violations.add("${file.name}:${idx + 1} uses lateinit var")
            }
        }
        assertTrue("Code size/safety violations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun noGlobalMutableSingletonsOrStaticContextHolders() {
        val violations = mutableListOf<String>()
        val staticContextFieldRegex =
            Regex("""(?:^|\n)\s*(?:companion\s+object|object\s+\w+)[^{]*\{[^}]*\b(?:val|var)\s+\w+\s*:\s*(?:Context|Activity|View)\b""")
        val globalMutableFieldRegex = Regex("""(?:^|\n)\s*object\s+\w+[^{]*\{[^}]*\bvar\s+""")
        allMainKotlinFiles().forEach { file ->
            val text = file.readText()
            if (staticContextFieldRegex.containsMatchIn(text)) {
                violations.add("${file.name} stores Context/Activity/View field in object/companion")
            }
            if (globalMutableFieldRegex.containsMatchIn(text)) {
                violations.add("${file.name} defines mutable var in top-level object singleton")
            }
        }
        assertTrue("Global mutable state / static Context violations:\n${violations.joinToString("\n")}", violations.isEmpty())
    }

    @Test
    fun allPublicTypesAndTopLevelApisHaveKDoc() {
        val modifiers = "(?:data|sealed|enum|value|abstract|open|final|annotation|inner|suspend|inline)"
        val publicDeclRegex =
            Regex("""^(?:$modifiers\s+)*(?:class|interface|object|fun|typealias)\s+\w+""")
        val missing = mutableListOf<String>()
        allMainKotlinFiles().forEach { file ->
            val lines = file.readLines()
            lines.forEachIndexed { idx, raw ->
                val trimmed = raw.trim()
                val isTopLevelOrPublicMember =
                    !trimmed.startsWith("private ") &&
                        !trimmed.startsWith("internal ") &&
                        !trimmed.startsWith("protected ") &&
                        !trimmed.startsWith("override ") &&
                        !trimmed.startsWith("companion ") &&
                        publicDeclRegex.containsMatchIn(trimmed)
                if (isTopLevelOrPublicMember && !hasPrecedingKDoc(lines, idx)) {
                    missing.add("${file.name}:${idx + 1}: $trimmed")
                }
            }
        }
        assertTrue("Public APIs missing KDoc:\n${missing.joinToString("\n")}", missing.isEmpty())
    }

    private fun hasPrecedingKDoc(
        lines: List<String>,
        declIdx: Int,
    ): Boolean {
        var i = declIdx - 1
        while (i >= 0) {
            val t = lines[i].trim()
            if (t.isEmpty() || t.startsWith("@")) {
                i--
                continue
            }
            return t.endsWith("*/") &&
                generateSequence(i) { it - 1 }
                    .takeWhile { it >= 0 }
                    .any { lines[it].trim().startsWith("/**") }
        }
        return false
    }
}
