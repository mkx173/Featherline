import com.github.jk1.license.filter.LicenseBundleNormalizer
import com.github.jk1.license.render.CsvReportRenderer
import com.github.jk1.license.render.InventoryHtmlReportRenderer
import com.github.jk1.license.render.ReportRenderer

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.google.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.dependency.license.report)
}

licenseReport {
    outputDir = layout.projectDirectory.dir("docs/generated").asFile.path
    projects = arrayOf(project(":app"))
    configurations = arrayOf(
        "playReleaseRuntimeClasspath",
        "arm64ReleaseRuntimeClasspath",
        "x64ReleaseRuntimeClasspath",
    )
    renderers = arrayOf<ReportRenderer>(
        InventoryHtmlReportRenderer(
            "dependency-license-report.html",
            "Featherline runtime dependencies",
        ),
        CsvReportRenderer("dependency-license-report.csv"),
    )
    filters = arrayOf(LicenseBundleNormalizer())
}
