package com.hermes.code.quality.tools

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor
import java.io.File
import javax.inject.Inject

@CacheableTask
abstract class KtLintTask : DefaultTask() {
    @Input
    lateinit var version: String

    @get:Classpath
    abstract val classpath: ConfigurableFileCollection

    @OutputDirectory
    lateinit var outputDirectory: File

    init {
        group = "verification"
        description = "Runs ktlint."
    }

    @get:Inject
    internal abstract val workerExecutor: WorkerExecutor

    @TaskAction
    fun run() {
        val queue = workerExecutor.noIsolation()

        queue.submit(KtLintWorker::class.java) {
            it.classpath.from(classpath)
            it.outputDirectory.set(outputDirectory)
        }
    }
}

internal interface KtLintParameters : WorkParameters {
    val classpath: ConfigurableFileCollection
    val outputDirectory: RegularFileProperty
}

internal abstract class KtLintWorker @Inject internal constructor(
    private val execOperations: ExecOperations,
) : WorkAction<KtLintParameters> {
    override fun execute() {
        println("execOperations: $execOperations")
        println("classpath: ${parameters.classpath.asPath}")

        execOperations.javaexec { task ->
            task.mainClass.set("com.pinterest.ktlint.Main")
            task.classpath = parameters.classpath
            println("mainClass: ${task.mainClass.get()}")
            println("classpath: ${task.classpath.asPath}")

            task.args(
                "--reporter=plain",
                "--reporter=checkstyle,output=${
                    File(
                        parameters.outputDirectory.asFile.get(),
                        "ktlint-checkstyle-report.xml"
                    )
                }",
                "**/*.kt",
                "**/*.kts",
                "!build/",
                "!build/**",
            )
        }
    }
}
