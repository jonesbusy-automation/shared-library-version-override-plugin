package fr.c3p0.jenkins.plugins.shared_library_version_override;

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
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.LibraryResolver;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class that holds a list of overrides configurations for a folder
 *
 * @author Cyril Pottiers
 */
public class FolderConfigurations extends AbstractFolderProperty<AbstractFolder<?>> {
    private static final Logger LOGGER = Logger.getLogger(FolderConfigurations.class.getName());

    private List<LibraryCustomConfiguration> overrides = Collections.emptyList();

    @DataBoundConstructor
    public FolderConfigurations() {
        LOGGER.log(Level.FINER, "Instantiating new FolderConfigurations\n");
    }

    /**
     * Returns the overrides configurations added to the folder
     *
     * @return The list of overrides configurations added to the folder
     */
    public LibraryCustomConfiguration[] getOverrides() {
        return overrides.toArray(new LibraryCustomConfiguration[0]);
    }

    /**
     * Adds a bunch of overrides configurations to the folder
     *
     * @param items The list of overrides configurations to be added to the folder
     */
    @DataBoundSetter
    public void setOverrides(List<LibraryCustomConfiguration> items) {
        LOGGER.log(Level.FINER, "FolderConfigurations.setOverrides({0})\n", items);
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

        private Collection<LibraryConfiguration> forGroup(@CheckForNull ItemGroup<?> group, boolean checkPermission) {
            // Lookup GlobalLibraries
            GlobalLibraries libs = ExtensionList.lookupSingleton(GlobalLibraries.class);
            // Get all available libraries
            List<LibraryConfiguration> libraries = new ArrayList<>();
            for (ItemGroup<?> g = group; g instanceof AbstractFolder; g = ((AbstractFolder<?>) g).getParent()) {
                AbstractFolder<?> f = (AbstractFolder<?>) g;
                if (!checkPermission || f.hasPermission(Item.CONFIGURE)) {
                    FolderConfigurations prop = f.getProperties().get(FolderConfigurations.class);
                    if (prop != null) {
                        for (LibraryCustomConfiguration item : prop.getOverrides()) {
                            if (item.isValid()) {
                                LibraryConfiguration libConfig = getLibraryConfiguration(item, libs);
                                if (libConfig != null) {
                                    libraries.add(libConfig);
                                }
                            }
                        }
                    }
                }
            }
            return libraries;
        }

        private static LibraryConfiguration getLibraryConfiguration(LibraryCustomConfiguration item, GlobalLibraries libs) {
            LibraryConfiguration libConfig = null;
            for (LibraryConfiguration lib : libs.getLibraries()) {
                if (lib.getName().equals(item.getName())) {
                    // if original library don't allow version override, so don't take it
                    if (! lib.isAllowVersionOverride()) {
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
        public Collection<LibraryConfiguration> forJob(@NonNull Job<?, ?> job, @NonNull Map<String, String> libraryVersions) {
            return forGroup(job.getParent(), false);
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> fromConfiguration(@NonNull StaplerRequest request) {
            return forGroup(request.findAncestorObject(AbstractFolder.class), true);
        }

        @NonNull
        @Override
        public Collection<LibraryConfiguration> suggestedConfigurations(@NonNull ItemGroup<?> group) {
            return forGroup(group, false);
        }
    }
}
