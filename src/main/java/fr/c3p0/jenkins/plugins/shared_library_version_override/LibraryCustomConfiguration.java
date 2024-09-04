package fr.c3p0.jenkins.plugins.shared_library_version_override;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.Util;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.util.FormValidation;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.interceptor.RequirePOST;
import org.kohsuke.stapler.verb.POST;

import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared library version override configuration
 *
 * @author Cyril Pottiers
 */
public class LibraryCustomConfiguration extends AbstractDescribableImpl<LibraryCustomConfiguration> {
    public String name;
    public String version;
    public boolean valid;

    @DataBoundConstructor
    public LibraryCustomConfiguration(String name, String version) {
        this.name = Util.fixEmptyAndTrim(name);
        this.version = Util.fixEmptyAndTrim(version);
        List<FormValidation> validations = new ArrayList<>();
        validations.add(isNameValid(this.name));
        validations.add(isVersionValid(this.version));
        this.valid = (FormValidation.aggregate(validations).kind.equals(FormValidation.Kind.OK));
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public boolean isValid() {
        return valid;
    }

    public static FormValidation isNameValid(String name) {
        List<FormValidation> validations = new ArrayList<>();
        validations.add(FormValidation.validateRequired(name));
        // Lookup GlobalLibraries
        if (name != null && !name.isBlank()) {
            GlobalLibraries libs = ExtensionList.lookupSingleton(GlobalLibraries.class);
            boolean isKnown = false;
            for (LibraryConfiguration lib : libs.getLibraries()) {
                if (lib.getName().equals(name)) {
                    if (!lib.isAllowVersionOverride()) {
                        validations.add(FormValidation.error(Messages.LibraryCustomConfiguration_ImmutableVersion()));
                    }
                    isKnown = true;
                    break;
                }
            }
            if (!isKnown) {
                validations.add(FormValidation.error(Messages.LibraryCustomConfiguration_NameUnknown()));
            }
        }
        return FormValidation.aggregate(validations);
    }

    public static FormValidation isVersionValid(String version) {
        return FormValidation.validateRequired(version);
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<LibraryCustomConfiguration> {
        @POST
        public FormValidation doCheckName(@QueryParameter("value") String name, @AncestorInPath Item item) throws IOException, ServletException {
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                item.checkPermission(Item.CONFIGURE);
            }
            return isNameValid(name);
        }

        @POST
        public FormValidation doCheckVersion(@QueryParameter("value") String version, @AncestorInPath Item item) throws IOException, ServletException {
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                item.checkPermission(Item.CONFIGURE);
            }
            return isVersionValid(version);
        }

        @POST
        public FormValidation doValidate(@QueryParameter("name") final String name, @QueryParameter("version") final String version, @AncestorInPath Item item) throws ServletException, IOException {
            if (item == null) {
                Jenkins.get().checkPermission(Jenkins.ADMINISTER);
            } else {
                item.checkPermission(Item.CONFIGURE);
            }

            List<FormValidation> validations = new ArrayList<>();
            validations.add(isNameValid(name));
            validations.add(isVersionValid(version));
            FormValidation result = FormValidation.aggregate(validations);
            if (result.kind.equals(FormValidation.Kind.OK)) {
                return FormValidation.ok("Success");
            }
            return FormValidation.error("Validation failed");
        }
    }
}
