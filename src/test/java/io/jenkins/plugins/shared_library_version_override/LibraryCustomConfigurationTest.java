package io.jenkins.plugins.shared_library_version_override;

import static org.junit.Assert.*;

import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class LibraryCustomConfigurationTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Before
    public void initNewRepository() throws Exception {
        // Create sample repo
        sampleRepo.init();
        sampleRepo.write("vars/greet.groovy", "def call(recipient) {echo(/hello from $recipient/)}");
        sampleRepo.write("src/pkg/Clazz.groovy", "package pkg; class Clazz {static String whereAmI() {'master'}}");
        sampleRepo.git("add", "vars", "src");
        sampleRepo.git("commit", "--message=init");

        sampleRepo.git("checkout", "-b", "develop");
        sampleRepo.write("src/pkg/Clazz.groovy", "package pkg; class Clazz {static String whereAmI() {'develop'}}");
        sampleRepo.git("commit", "--all", "--message=branching");

        LibraryConfiguration lc =
                new LibraryConfiguration("greet", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        lc.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));
    }

    @Test
    public void validNameAndVersion() throws Exception {
        String libraryName = "  greet   ";
        String defaultVersion = "   master   ";

        LibraryCustomConfiguration item = new LibraryCustomConfiguration(libraryName, defaultVersion);
        assertEquals("greet", item.getName());
        assertEquals("master", item.getVersion());
        assertTrue(item.isValid());
    }

    @Test
    public void invalidName() throws Exception {
        String libraryName = "groot";
        String defaultVersion = "master";

        LibraryCustomConfiguration item = new LibraryCustomConfiguration(libraryName, defaultVersion);
        assertFalse(item.isValid());
    }
}
