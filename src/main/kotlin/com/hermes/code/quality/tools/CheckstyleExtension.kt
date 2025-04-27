package com.hermes.code.quality.tools

open class CheckstyleExtension {
    /**
     * Ability to enable or disable only checkstyle for every subproject that is not ignored.
     * @since 0.2.0
     */
    var enabled: Boolean = true

    /** @since 0.2.0 */
    var toolVersion: String = "10.12.4"

    /** @since 0.2.0 */
    var configFile: String = "config/checkstyle/checkstyle.xml"

    /**
     * If set to false or true it overrides {@link CodeQualityToolsPluginExtension#failEarly}.
     * @since 0.3.0
     */
    var ignoreFailures: Boolean? = null

    /**
     * If set to false or true it overrides {@link CodeQualityToolsPluginExtension#failEarly}.
     * @since 0.3.0
     */
    var showViolations: Boolean? = null

    /** @since 0.4.0 */
    var source: String = "src"

    /** @since 0.4.0 */
    var include: List<String> = listOf("**/*.java")

    /** @since 0.4.0 */
    var exclude: List<String> = listOf("**/gen/**")
}
