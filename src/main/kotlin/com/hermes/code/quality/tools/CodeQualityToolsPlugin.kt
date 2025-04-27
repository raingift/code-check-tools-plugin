@file:Suppress("Detekt.TooManyFunctions", "UnstableApiUsage")

package com.hermes.code.quality.tools

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LintPlugin
import com.android.build.gradle.internal.dsl.LintOptions
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File

const val GROUP_VERIFICATION = "verification"

class CodeQualityToolsPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        val extension = target.extensions.create(
            "codeQualityTools",
            CodeQualityToolsPluginExtension::class.java,
            target.objects
        )

        val hasSubProjects = target.subprojects.isNotEmpty()

        if (hasSubProjects) {
            target.subprojects { subProject ->
                subProject.afterEvaluate {
                    addCodeQualityTools(it, target, extension)
                }
            }
        } else {
            target.afterEvaluate {
                addCodeQualityTools(it, target, extension)
            }
        }
    }

    private fun addCodeQualityTools(
        project: Project,
        rootProject: Project,
        extension: CodeQualityToolsPluginExtension
    ) {
        project.addCheckstyle(rootProject, extension)
        project.addDetekt(rootProject, extension)

        project.addKtlint(rootProject, extension)
        project.addKotlin(extension)
        project.addLint(extension)
    }
}

fun hasLintPlugin(): Boolean {
    return try {
        Class.forName("com.android.build.gradle.LintPlugin")
        true
    } catch (ignored: ClassNotFoundException) {
        false
    }
}

fun Project.kotlinFiles(baseDir: String? = null): PatternFilterable =
    fileTree(baseDir ?: projectDir)
        .setIncludes(listOf("**/*.kt", "**/*.kts"))
        .setExcludes(
            listOf(
                "build/",
                "generated/",
                "src/test/snapshots/",
            ),
        )

fun Project.editorconfigFile(): ConfigurableFileTree =
    fileTree(mapOf("dir" to ".", "include" to ".editorconfig"))


fun Project.addCheckstyle(
    rootProject: Project,
    extension: CodeQualityToolsPluginExtension
): Boolean {
    val isNotIgnored = !shouldIgnore(extension)
    val isEnabled = extension.checkstyle.enabled
    val isCheckstyleSupported = isJavaProject() || isAndroidProject()

    if (isNotIgnored && isEnabled && isCheckstyleSupported) {
        plugins.apply(CheckstylePlugin::class.java)

        extensions.configure(CheckstyleExtension::class.java) {
            it.toolVersion = extension.checkstyle.toolVersion
            it.configFile = rootProject.file(extension.checkstyle.configFile)
            it.isIgnoreFailures = extension.checkstyle.ignoreFailures ?: !extension.failEarly
            it.isShowViolations = extension.checkstyle.showViolations ?: extension.failEarly
        }

        tasks.register("checkstyle", Checkstyle::class.java) {
            it.description = "Runs Java checkstyle."
            it.group = GROUP_VERIFICATION

            it.source = fileTree(extension.checkstyle.source)
            it.include(extension.checkstyle.include)
            it.exclude(extension.checkstyle.exclude)

            it.classpath = files()

            it.reports.html.required.set(extension.htmlReports)
            it.reports.xml.required.set(extension.xmlReports)
        }

        tasks.named(CHECK_TASK_NAME).configure { it.dependsOn("checkstyle") }
        return true
    }

    return false
}

@Suppress("Detekt.ComplexMethod")
fun Project.addLint(extension: CodeQualityToolsPluginExtension): Boolean {
    val isNotIgnored = !shouldIgnore(extension)
    val isEnabled = extension.lint.enabled
    val isAndroidProject = isAndroidProject()
    val isJavaProject = isJavaProject()

    if (isNotIgnored && isEnabled) {
        val lintOptions = if (isAndroidProject) {
            extensions.getByType(BaseExtension::class.java).lintOptions
        } else if (isJavaProject && hasLintPlugin()) {
            plugins.apply(LintPlugin::class.java)
            extensions.getByType(LintOptions::class.java)
        } else {
            null
        }

        if (lintOptions != null) {
            lintOptions.isWarningsAsErrors = extension.lint.warningsAsErrors ?: extension.failEarly
            lintOptions.isAbortOnError = extension.lint.abortOnError ?: extension.failEarly

            extension.lint.checkAllWarnings?.let {
                lintOptions.isCheckAllWarnings = it
            }

            extension.lint.absolutePaths?.let {
                lintOptions.isAbsolutePaths = it
            }

            extension.lint.baselineFileName?.let {
                lintOptions.baselineFile = file(it)
            }

            extension.lint.lintConfig?.let {
                lintOptions.lintConfig = it
            }

            extension.lint.checkReleaseBuilds?.let {
                lintOptions.isCheckReleaseBuilds = it
            }

            extension.lint.checkTestSources?.let {
                lintOptions.isCheckTestSources = it
            }

            extension.lint.checkDependencies?.let {
                lintOptions.isCheckDependencies = it
            }

            extension.lint.textReport?.let {
                lintOptions.textReport = it
                lintOptions.textOutput(extension.lint.textOutput)
            }

            tasks.named(CHECK_TASK_NAME).configure { it.dependsOn("lint") }
            return true
        }
    }

    return false
}

fun Project.addKotlin(extension: CodeQualityToolsPluginExtension): Boolean {
    val isNotIgnored = !shouldIgnore(extension)
    val isKotlinProject = isKotlinProject()

    if (isNotIgnored && isKotlinProject) {
        project.tasks.withType(KotlinCompile::class.java) {
            it.kotlinOptions.allWarningsAsErrors = extension.kotlin.allWarningsAsErrors
        }
        return true
    }

    return false
}

fun Project.addKtlint(rootProject: Project, extension: CodeQualityToolsPluginExtension): Boolean {
    val isNotIgnored = !shouldIgnore(extension)
    val isEnabled = extension.ktlint.enabled
    val isKtlintSupported = isKotlinProject()

    if (isNotIgnored && isEnabled && isKtlintSupported) {
        val ktlint = "ktlint"

        val ktlintConfiguration = configurations.create(ktlint) { configuration ->
            configuration.attributes {
                it.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage::class.javaObjectType, Usage.JAVA_RUNTIME)
                )
            }

            configuration.isCanBeConsumed = false
            configuration.isVisible = false

            configuration.withDependencies {
                it.add(dependencies.create("com.pinterest.ktlint:ktlint-cli:${extension.ktlint.toolVersion}"))
            }
        }

        tasks.register(ktlint, KtLintTask::class.java) { task ->
            task.version = extension.ktlint.toolVersion
            task.classpath.from(ktlintConfiguration)
            task.outputDirectory = File(layout.buildDirectory.asFile.get(), "reports/ktlint/")
            task.inputs.files(kotlinFiles(), rootProject.editorconfigFile())
        }

        tasks.register("ktlintFormat", KtLintFormatTask::class.java) { task ->
            task.version = extension.ktlint.toolVersion
            task.classpath.from(ktlintConfiguration)
            task.outputDirectory = File(layout.buildDirectory.asFile.get(), "reports/ktlint/")
            task.inputs.files(kotlinFiles(), rootProject.editorconfigFile())
        }

        tasks.named(CHECK_TASK_NAME).configure { it.dependsOn(ktlint) }
        return true
    }

    return false
}

fun Project.addDetekt(rootProject: Project, extension: CodeQualityToolsPluginExtension): Boolean {
    val isNotIgnored = !shouldIgnore(extension)
    val isEnabled = extension.detekt.enabled
    val isDetektSupported = isKotlinProject()

    if (isNotIgnored && isEnabled && isDetektSupported) {
        val detektConfiguration = configurations.create("detekt") { configuration ->
            configuration.attributes {
                it.attribute(
                    Usage.USAGE_ATTRIBUTE,
                    objects.named(Usage::class.javaObjectType, Usage.JAVA_RUNTIME)
                )
            }

            configuration.isCanBeConsumed = false
            configuration.isVisible = false

            configuration.defaultDependencies {
                it.add(dependencies.create("io.gitlab.arturbosch.detekt:detekt-cli:${extension.detekt.toolVersion}"))
            }
        }

        tasks.register("detektCheck", DetektCheckTask::class.java) { task ->
            task.failFast = extension.detekt.failFast
            task.buildUponDefaultConfig = extension.detekt.buildUponDefaultConfig
            task.parallel = extension.detekt.parallel
            task.version = extension.detekt.toolVersion
            task.outputDirectory = File(layout.buildDirectory.asFile.get(), "reports/detekt/")
            task.configFile = rootProject.file(extension.detekt.config)
            task.inputFile = file(extension.detekt.input)
            task.classpath.from(detektConfiguration)
            task.inputs.files(kotlinFiles(baseDir = extension.detekt.input))
            task.inputs.property("baseline-file-exists", false)

            extension.detekt.baselineFileName?.let {
                val file = file(it)
                task.baselineFilePath = file.toString()
                task.inputs.property("baseline-file-exists", file.exists())
            }
        }

        tasks.named(CHECK_TASK_NAME).configure { it.dependsOn("detektCheck") }
        return true
    }

    return false
}

private fun Project.shouldIgnore(extension: CodeQualityToolsPluginExtension) =
    extension.ignoreProjects.contains(name)

private fun Project.isJavaProject(): Boolean {
    val isJava = plugins.hasPlugin("java")
    val isJavaLibrary = plugins.hasPlugin("java-library")
    val isJavaGradlePlugin = plugins.hasPlugin("java-gradle-plugin")
    return isJava || isJavaLibrary || isJavaGradlePlugin
}

private fun Project.isAndroidProject(): Boolean {
    val isAndroidLibrary = plugins.hasPlugin("com.android.library")
    val isAndroidApp = plugins.hasPlugin("com.android.application")
    val isAndroidTest = plugins.hasPlugin("com.android.test")
    val isAndroidInstantApp = plugins.hasPlugin("com.android.instantapp")
    return isAndroidLibrary || isAndroidApp || isAndroidTest || isAndroidInstantApp
}

private fun Project.isKotlinProject(): Boolean {
    val isKotlin = plugins.hasPlugin("kotlin")
    val isKotlinAndroid = plugins.hasPlugin("kotlin-android")
    val isKotlinMultiPlatform = plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")
    val isKotlinPlatformCommon = plugins.hasPlugin("kotlin-platform-common")
    val isKotlinPlatformJvm = plugins.hasPlugin("kotlin-platform-jvm")
    val isKotlinPlatformJs = plugins.hasPlugin("kotlin-platform-js")
    return isKotlin || isKotlinAndroid || isKotlinMultiPlatform || isKotlinPlatformCommon || isKotlinPlatformJvm || isKotlinPlatformJs
}
