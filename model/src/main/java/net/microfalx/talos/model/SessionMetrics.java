package net.microfalx.talos.model;

import com.esotericsoftware.kryo.serializers.VersionFieldSerializer;
import net.microfalx.jvm.model.Server;
import net.microfalx.jvm.model.VirtualMachine;
import net.microfalx.metrics.SeriesStore;
import net.microfalx.resource.Resource;
import org.apache.maven.execution.MavenSession;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.unmodifiableCollection;
import static java.util.Collections.unmodifiableMap;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.StringUtils.NA_STRING;

/**
 * Holds metrics about a Maven session.
 */
public class SessionMetrics extends AbstractSessionMetrics<SessionMetrics> {

    private final Collection<ArtifactMetrics> artifacts = new ArrayList<>();
    private final Collection<DependencyMetrics> dependencies = new ArrayList<>();
    private final Collection<PluginMetrics> plugins = new ArrayList<>();
    private final Collection<TestMetrics> tests = new ArrayList<>();
    private final Collection<TrendMetrics> trends = new ArrayList<>();
    @VersionFieldSerializer.Since(2)
    private final Collection<LifecycleMetrics> extensionEvents = new ArrayList<>();

    private VirtualMachine virtualMachine;
    private final Map<String, String> systemProperties = new HashMap<>();
    private Server server;

    private SeriesStore virtualMachineMetrics = SeriesStore.memory();
    private SeriesStore serverMetrics = SeriesStore.memory();

    private String logs;
    private transient boolean testsUpdated;

    public static SessionMetrics load(Resource resource) throws IOException {
        return AbstractSessionMetrics.load(resource, SessionMetrics.class);
    }

    public static SessionMetrics load(InputStream inputStream) throws IOException {
        return AbstractSessionMetrics.load(inputStream, SessionMetrics.class);
    }

    protected SessionMetrics() {
    }

    public SessionMetrics(MavenSession session) {
        super(session);
    }

    public Collection<ArtifactMetrics> getArtifacts() {
        return unmodifiableCollection(artifacts);
    }

    private Collection<ArtifactMetrics> getTrimmedArtifacts() {
        if (isVerbose()) {
            return getArtifacts();
        } else {
            return getArtifacts().stream()
                    .filter(a -> a.getDuration().toMillis() > 5)
                    .collect(Collectors.toList());
        }
    }

    public void setArtifacts(Collection<ArtifactMetrics> artifacts) {
        requireNonNull(artifacts);
        this.artifacts.addAll(artifacts);
    }

    public Collection<DependencyMetrics> getDependencies() {
        return unmodifiableCollection(dependencies);
    }

    public void setDependencies(Collection<DependencyMetrics> dependencies) {
        requireNonNull(dependencies);
        this.dependencies.addAll(dependencies);
    }

    public Collection<PluginMetrics> getPlugins() {
        return unmodifiableCollection(plugins);
    }

    public void setPlugins(Collection<PluginMetrics> plugins) {
        requireNonNull(plugins);
        this.plugins.addAll(plugins);
    }

    public Collection<TrendMetrics> getTrends() {
        return unmodifiableCollection(trends);
    }

    public void setTrends(Collection<TrendMetrics> trends) {
        requireNonNull(trends);
        this.trends.addAll(trends);
    }

    public Collection<TestMetrics> getTests() {
        if (!testsUpdated) {
            tests.forEach(this::updateTestMetrics);
            testsUpdated = true;
        }
        return unmodifiableCollection(tests);
    }

    public void setTests(Collection<TestMetrics> tests) {
        requireNonNull(tests);
        this.tests.addAll(tests);
    }

    public Collection<LifecycleMetrics> getExtensionEvents() {
        return unmodifiableCollection(extensionEvents);
    }

    public void setExtensionsEvents(Collection<LifecycleMetrics> extensionEvents) {
        requireNonNull(extensionEvents);
        this.extensionEvents.addAll(extensionEvents);
    }

    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    public void setVirtualMachine(VirtualMachine virtualMachine) {
        this.virtualMachine = virtualMachine;
    }

    public Server getServer() {
        return server;
    }

    public void setServer(Server server) {
        this.server = server;
    }

    public Map<String, String> getSystemProperties() {
        return unmodifiableMap(systemProperties);
    }

    public void setSystemProperties(Map<String, String> systemProperties) {
        requireNonNull(systemProperties);
        this.systemProperties.putAll(systemProperties);
    }

    public SeriesStore getVirtualMachineMetrics() {
        return virtualMachineMetrics;
    }

    public void setVirtualMachineMetrics(SeriesStore virtualMachineMetrics) {
        this.virtualMachineMetrics = virtualMachineMetrics;
    }

    public SeriesStore getServerMetrics() {
        return serverMetrics;
    }

    public void setServerMetrics(SeriesStore serverMetrics) {
        this.serverMetrics = serverMetrics;
    }


    public String getLogs() {
        return logs;
    }

    public void setLogs(String logs) {
        requireNonNull(logs);
        this.logs = logs;
    }

    private void updateTestMetrics(TestMetrics test) {
        if (test.getModuleId() != null && test.getModule() == null) {
            test.module = getModule(test.getModuleId());
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", SessionMetrics.class.getSimpleName() + "[", "]")
                .add(super.toString())
                .add("artifacts=" + artifacts.size())
                .add("dependencies=" + dependencies.size())
                .add("plugins=" + plugins.size())
                .add("log='" + (logs != null ? logs.length() : NA_STRING) + "'")
                .toString();
    }


}
