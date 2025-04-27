package com.hermes.code.quality.tools

import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

open class CodeQualityToolsPluginExtension @Inject constructor(
  objectFactory: ObjectFactory,
) {
  /**
   * When set to true all enabled code quality tools will be configured in a way that even a single warning / error will fail the build process.
   * @since 0.2.0
   */
  var failEarly: Boolean = true

  /**
   * Global configuration which will be applied on all enabled code quality tools that support xml reports.
   * @since 0.2.0
   */
  var xmlReports: Boolean = true

  /**
   * Global configuration which will be applied on all enabled code quality tools that support html reports.
   * @since 0.2.0
   */
  var htmlReports: Boolean = false

  /**
   * List of subprojects identified by their name that should be ignored.
   * @since 0.2.0
   */
  var ignoreProjects: List<String> = emptyList()

  val checkstyle = objectFactory.newInstance(CheckstyleExtension::class.java)
  fun checkstyle(action: Action<in CheckstyleExtension>) = action.execute(checkstyle)

  val lint = objectFactory.newInstance(LintExtension::class.java)
  fun lint(action: Action<in LintExtension>) = action.execute(lint)

  val ktlint = objectFactory.newInstance(KtlintExtension::class.java)
  fun ktlint(action: Action<in KtlintExtension>) = action.execute(ktlint)

  val detekt = objectFactory.newInstance(DetektExtension::class.java)
  fun detekt(action: Action<in DetektExtension>) = action.execute(detekt)

  val kotlin = objectFactory.newInstance(KotlinExtension::class.java)
  fun kotlin(action: Action<in KotlinExtension>) = action.execute(kotlin)
}
