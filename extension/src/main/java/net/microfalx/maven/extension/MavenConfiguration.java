package net.microfalx.maven.extension;

import org.apache.maven.execution.MavenSession;

import java.time.Duration;

import static java.time.Duration.ofMillis;
import static net.microfalx.maven.core.MavenUtils.getProperty;

/**
 * Resolves various Maven related configuration.
 */
public class MavenConfiguration extends net.microfalx.maven.core.MavenConfiguration {

    private Duration minimumDuration;
    private Boolean extensionEnabled;
    private Boolean performanceEnabled;

    public MavenConfiguration(MavenSession session) {
        super(session);
    }

    /**
     * Returns the minimum duration for a task to be a candidate for visualization.
     *
     * @return a non-null instance
     */
    public final Duration getMinimumDuration() {
        if (minimumDuration == null) {
            minimumDuration = getProperty(getSession(), "minimumDuration", ofMillis(100));
        }
        return minimumDuration;
    }

    /**
     * Returns whether the console is enabled and should display reports and summaries.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public Boolean isConsoleEnabled() {
        return getProperty(getSession(), "console.enabled", true);
    }

    /**
     * Returns whether the performance tracking is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public Boolean isPerformanceEnabled() {
        if (performanceEnabled == null) {
            performanceEnabled = getProperty(getSession(), "performance.enabled", true);
        }
        return isExtensionEnabled() && performanceEnabled;
    }

    /**
     * Returns whether the extension is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public Boolean isExtensionEnabled() {
        if (extensionEnabled == null) {
            extensionEnabled = getProperty(getSession(), "extension.enabled", true);
        }
        return extensionEnabled;
    }

    /**
     * Returns whether the extension is enabled.
     *
     * @return {@code true} if enabled, {@code false} otherwise
     */
    public Boolean isOpenReportEnabled() {
        return getProperty(getSession(), "report.open", false);
    }
}
