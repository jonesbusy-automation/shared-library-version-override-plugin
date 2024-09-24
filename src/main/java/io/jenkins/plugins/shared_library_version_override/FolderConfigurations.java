package io.jenkins.plugins.shared_library_version_override;

import com.cloudbees.hudson.plugins.folder.AbstractFolder;
import com.cloudbees.hudson.plugins.folder.AbstractFolderProperty;
import com.cloudbees.hudson.plugins.folder.AbstractFolderPropertyDescriptor;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jenkinsci.plugins.workflow.libs.FolderLibraries;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryResolver;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A class that holds a list of overrides configurations for a folder
 *
 * @author Cyril Pottiers
 */
public class FolderConfigurations extends AbstractFolderProperty<AbstractFolder<?>> {
    private static final Logger LOGGER = Logger.getLogger(FolderConfigurations.class.getName());

    private List<LibraryCustomConfiguration> overrides = Collections.emptyList();

    @DataBoundConstructor
    public FolderConfigurations() {}

    /**
     * Returns the overrides configurations added to the folder
     *
     * @return The list of overrides configurations added to the folder
     */
    public LibraryCustomConfiguration[] getOverrides() {
        LibraryCustomConfiguration[] lcc = overrides.toArray(new LibraryCustomConfiguration[0]);
        LOGGER.log(Level.FINER, "get overrides : ({0})\n", lcc);
        return lcc;
    }

    /**
     * Adds a bunch of overrides configurations to the folder
     *
     * @param items The list of overrides configurations to be added to the folder
     */
    @DataBoundSetter
    public void setOverrides(List<LibraryCustomConfiguration> items) {
        LOGGER.log(Level.FINER, "Add new overrides : ({0})\n", items);
        this.overrides = items;
    }

    /**
     * Descriptor class.
     */
    @Extension
    public static class DescriptorImpl extends AbstractFolderPropertyDescriptor {
        @NonNull
        @Override
        public String getDisplayName() {
            return FolderConfigurations.class.getName();
        }
    }

    /**
     * Simulate a new LibraryResolver prior to global resolver
     */
    @Extension(ordinal = 1000) // Priority over pipeline-groovy-lib
    public static class CustomLibraryResolver extends LibraryResolver {
        @Override
        public boolean isTrusted() {
            return true;
        }

        public static Collection<LibraryConfiguration> getDefinedLibrariesForGroup(@CheckForNull ItemGroup<?> group) {
            // Get all global librairies
            GlobalLibraries libs = ExtensionList.lookupSingleton(GlobalLibraries.class);

            List<LibraryConfiguration> libraries = new ArrayList<>(libs.getLibraries());
            // Get all folder local libraries
            for (ItemGroup<?> g = group; g instanceof AbstractFolder; g = ((AbstractFolder<?>) g).getParent()) {
                AbstractFolder<?> f = (AbstractFolder<?>) g;
                FolderLibraries prop = f.getProperties().get(FolderLibraries.class);
                if (prop != null) {
                    libraries.addAll(prop.getLibraries());
                }
            }
            LOGGER.log(
                    Level.FINE,
                    "CustomLibraryResolver.getDefinedLibrariesForGroup {0}\n",
                    libraries.stream().map(LibraryConfiguration::getName).collect(Collectors.toList()));
            return libraries;
        }

        private Collection<LibraryConfiguration> forGroup(@CheckForNull ItemGroup<?> group, boolean checkPermission) {
            // Get all available libraries
            Collection<LibraryConfiguration> allLibs = getDefinedLibrariesForGroup(group);
            List<LibraryConfiguration> libraries = new ArrayList<>();
            for (ItemGroup<?> g = group; g instanceof AbstractFolder; g = ((AbstractFolder<?>) g).getParent()) {
                AbstractFolder<?> f = (AbstractFolder<?>) g;
                if (!checkPermission || f.hasPermission(Item.CONFIGURE)) {
                    FolderConfigurations prop = f.getProperties().get(FolderConfigurations.class);
                    if (prop != null) {
                        for (LibraryCustomConfiguration item : prop.getOverrides()) {
                            LibraryConfiguration libConfig = getLibraryConfiguration(item, allLibs);
                            if (libConfig != null) {
                                libraries.add(libConfig);
                            }
                        }
                    }
                }
            }
            LOGGER.log(
                    Level.FINE,
                    "CustomLibraryResolver.forGroup {0}\n",
                    libraries.stream().map(LibraryConfiguration::getName).collect(Collectors.toList()));
            return libraries;
        }

        private static LibraryConfiguration getLibraryConfiguration(
                LibraryCustomConfiguration item, Collection<LibraryConfiguration> libs) {
            LibraryConfiguration libConfig = null;
            for (LibraryConfiguration lib : libs) {
                if (lib.getName().equals(item.getName())) {
                    // if original library don't allow version override, so don't take it
                    if (!lib.isAllowVersionOverride()) {
                        LOGGER.log(
                                Level.FINE,
                                "CustomLibraryResolver.getLibraryConfiguration {0} don't allow version override, don't take it.\n",
                                lib.getName());
                        continue;
                    }
                    libConfig = new LibraryConfiguration(lib.getName(), lib.getRetriever());
                    libConfig.setDefaultVersion(item.getVersion());
                    libConfig.setImplicit(lib.isImplicit());
                    libConfig.setAllowVersionOverride(lib.isAllowVersionOverride());
                    libConfig.setIncludeInChangesets(lib.getIncludeInChangesets());
                    libConfig.setCachingConfiguration(lib.getCachingConfiguration());
                }
            }
            return libConfig;
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> forJob(
                @NonNull Job<?, ?> job, @NonNull Map<String, String> libraryVersions) {
            LOGGER.log(Level.FINER, "CustomLibraryResolver.forJob({0})\n", job);
            return forGroup(job.getParent(), false);
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> fromConfiguration(@NonNull StaplerRequest request) {
            LOGGER.log(Level.FINER, "CustomLibraryResolver.fromConfiguration({0})\n", request);
            return forGroup(request.findAncestorObject(AbstractFolder.class), true);
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> suggestedConfigurations(@NonNull ItemGroup<?> group) {
            LOGGER.log(Level.FINER, "CustomLibraryResolver.suggestedConfigurations({0})\n", group);
            return forGroup(group, false);
        }
    }
}
