package net.microfalx.maven.plugin;

import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.StringUtils;
import net.microfalx.lang.Version;
import net.microfalx.maven.docker.Image;
import net.microfalx.maven.docker.ImageBuilder;
import net.microfalx.resource.ClassPathResource;
import net.microfalx.resource.Resource;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.*;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.shared.artifact.filter.PatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.PatternIncludesArtifactFilter;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Mojo(name = "package", defaultPhase = LifecyclePhase.INSTALL, requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, threadSafe = true)
public class PackageMojo extends AbstractMojo {

    @Parameter(defaultValue = "true")
    private boolean boot;

    @Parameter
    private String baseImage;

    @Parameter(required = true)
    private String mainClass;

    @Parameter
    private String libraryNamespaceSeparator = "@";

    @Parameter(defaultValue = "${project.artifactId}", required = true)
    private String image;

    @Parameter(defaultValue = "true")
    private boolean includeModule;

    /**
     * A list of dependencies which will be included in the lib directory.
     */
    @Parameter
    private final Set<String> includes = new HashSet<>();

    /**
     * A list of dependencies which will be excluded from the lib directory.
     */
    @Parameter
    private final Set<String> excludes = new HashSet<>();

    @Parameter(readonly = true, property = "registry.hostname")
    private String registryHostname;

    @Parameter(readonly = true, property = "registry.username")
    private String registryUsername;

    @Parameter(readonly = true, property = "registry.password")
    private String containerRegistryPassword;

    @Component
    private RepositorySystem repoSystem;

    @Override
    public void execute() throws MojoFailureException {
        logConfiguration();
        buildImage();
    }

    private void logConfiguration() {
        getLog().info("Package module '" + project.getGroupId() + ":" + project.getArtifactId() + ":" + getVersion()
                + "' to image '" + image + "', boot '" + boot + "', main class '" + mainClass + "'");
        if (getLog().isDebugEnabled()) {
            getLog().debug(" - include dependencies: " + includes);
            getLog().debug(" - exclude dependencies: " + excludes);
        }
    }

    private void buildImage() throws MojoFailureException {
        ImageBuilder builder = new ImageBuilder(image, Version.parse(getVersion()).toTag())
                .setMainClass(mainClass).setVersion(getVersion()).setBase(boot)
                .setLibraryNamespaceSeparator(libraryNamespaceSeparator);
        try {
            addLibraries(builder);
        } catch (ArtifactResolutionException e) {
            throw new MojoFailureException("Failed to resolve libraries", e);
        }
        Image dockerImage = builder.build();
        getLog().info("Image built successfuly, " + dockerImage.getId());
    }

    private void addLibraries(ImageBuilder builder) throws ArtifactResolutionException, MojoFailureException {
        Set<Artifact> artifacts = getArtifacts();
        getLog().debug("Process dependency list, " + artifacts.size() + " artifact(s)");
        if (includeModule) artifacts.add(project.getArtifact());
        boolean bootAdded = false;
        for (Artifact artifact : artifacts) {
            if (isBoot(artifact)) {
                if (!boot) continue;
                bootAdded = true;
            }
            resolve(artifact);
            boolean included = shouldIncludeArtifact(artifact);
            getLog().debug(" - " + (included ? "Included: " : "Excluded: ") + artifact.getId() + ", scope " + artifact.getScope());
            if (included) {
                builder.addLibrary(Resource.file(artifact.getFile()), getNamespace(artifact));
            }
        }
        Resource bootArtifact = getBootArtifact();
        if (!bootAdded) {
            builder.addLibrary(bootArtifact, ImageBuilder.DOMAIN_NAME);
        }
        String extraInfo = StringUtils.EMPTY_STRING;
        if (includeModule) extraInfo = " (including current module)";
        getLog().info("Include dependencies (" + builder.getLibraries().size() + " JARs)" + extraInfo);
    }

    private Resource getBootArtifact() throws MojoFailureException {
        AtomicReference<Resource> bootResource = new AtomicReference<>();
        Resource bootLibDir = ClassPathResource.directory("boot-lib");
        try {
            bootLibDir.walk((root, child) -> {
                if (child.getFileName().startsWith("maven-boot")) {
                    bootResource.set(child);
                    return false;
                } else {
                    return true;
                }
            });
        } catch (Exception e) {
            throw new MojoFailureException("Could not resolve boot library, root cause: " + ExceptionUtils.getRootCause(e));
        }
        if (bootResource.get() == null) {
            throw new IllegalStateException("The boot library could not be located");
        } else {
            return bootResource.get();
        }
    }

    private void resolve(Artifact artifact) throws ArtifactResolutionException {
        ArtifactResolutionRequest request = new ArtifactResolutionRequest();
        request.setArtifact(artifact);
        request.setRemoteRepositories(project.getRemoteArtifactRepositories());
        request.setLocalRepository(session.getLocalRepository());
        ArtifactResolutionResult result = this.repoSystem.resolve(request);
        ResolutionErrorHandler resolutionErrorHandler = new DefaultResolutionErrorHandler();
        resolutionErrorHandler.throwErrors(request, result);
    }

    private boolean isSelfGroupId(String groupId) {
        return ImageBuilder.GROUP_ID.equals(groupId);
    }

    private boolean isBoot(Artifact artifact) {
        return isSelfGroupId(artifact.getGroupId()) && ImageBuilder.BOOT_ARTIFACT_ID.equals(artifact.getArtifactId());
    }

    private boolean shouldIncludeArtifact(Artifact artifact) {
        if (Artifact.SCOPE_RUNTIME.equals(artifact.getScope())) return false;
        if (includes.isEmpty() && excludes.isEmpty()) return true;
        if (!includes.isEmpty()) {
            PatternIncludesArtifactFilter filter = new PatternIncludesArtifactFilter(new ArrayList<>(includes));
            if (!filter.include(artifact)) return false;
        }
        if (!excludes.isEmpty()) {
            PatternExcludesArtifactFilter filter = new PatternExcludesArtifactFilter(new ArrayList<>(excludes));
            if (!filter.include(artifact)) return false;
        }
        return !"pom".equalsIgnoreCase(artifact.getType());
    }

    private String getNamespace(Artifact artifact) {
        String groupId = artifact.getGroupId();
        String[] parts = StringUtils.split(groupId, ".");
        String tld = parts[0];
        String namespace = findNamespace(groupId);
        if (namespace == null) {
            if (tld.length() > 3) {
                namespace = tld;
            } else {
                namespace = parts.length > 1 ? parts[1] : tld;
            }
        }
        return namespace;
    }

    private String findNamespace(String groupId) {
        for (Map.Entry<Pattern, String> entry : namespaces.entrySet()) {
            if (entry.getKey().matcher(groupId).matches()) return entry.getValue();
        }
        return null;
    }

    private static void registerNamespace(String pattern, String value) {
        namespaces.put(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE), value);
    }

    private static final Map<Pattern, String> namespaces = new HashMap<>();

    static {
        registerNamespace("jakarta.*", "jakarta");
    }

}