package io.jenkins.plugins.shared_library_version_override;

/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

import com.cloudbees.hudson.plugins.folder.Folder;
import hudson.model.Result;
import java.util.Collections;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

public class FolderConfigurationsTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

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
    }

    @Test
    public void withoutOverride() throws Exception {
        LibraryConfiguration lc =
                new LibraryConfiguration("greet", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        lc.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));

        Folder f = r.jenkins.createProject(Folder.class, "f");

        WorkflowJob p = f.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('greet') _; greet(pkg.Clazz.whereAmI())", true));
        r.assertLogContains("hello from master", r.buildAndAssertSuccess(p));
    }

    @Test
    public void withFolderOverride() throws Exception {
        LibraryConfiguration lc =
                new LibraryConfiguration("greet", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        lc.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));

        Folder f = r.jenkins.createProject(Folder.class, "f");
        FolderConfigurations prop = new FolderConfigurations();
        LibraryCustomConfiguration item = new LibraryCustomConfiguration("greet", "develop");
        prop.setOverrides(Collections.singletonList(item));
        f.addProperty(prop);

        WorkflowJob p = f.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('greet') _; greet(pkg.Clazz.whereAmI())", true));
        r.assertLogContains("hello from develop", r.buildAndAssertSuccess(p));
    }

    @Test
    public void withAnotherFolderOverride() throws Exception {
        LibraryConfiguration lc =
                new LibraryConfiguration("greet", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        lc.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));

        Folder f = r.jenkins.createProject(Folder.class, "f");
        FolderConfigurations prop = new FolderConfigurations();
        LibraryCustomConfiguration item = new LibraryCustomConfiguration("greet", "develop");
        prop.setOverrides(Collections.singletonList(item));
        f.addProperty(prop);

        Folder f2 = r.jenkins.createProject(Folder.class, "f2");
        WorkflowJob p = f2.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('greet') _; greet(pkg.Clazz.whereAmI())", true));
        r.assertLogContains("hello from master", r.buildAndAssertSuccess(p));
    }

    @Test
    public void withJenkinsfileOverride() throws Exception {
        LibraryConfiguration lc =
                new LibraryConfiguration("greet", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        lc.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));

        Folder f = r.jenkins.createProject(Folder.class, "f");
        FolderConfigurations prop = new FolderConfigurations();
        LibraryCustomConfiguration item = new LibraryCustomConfiguration("greet", "develop");
        prop.setOverrides(Collections.singletonList(item));
        f.addProperty(prop);

        WorkflowJob p = f.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('greet@master') _; greet(pkg.Clazz.whereAmI())", true));
        r.assertLogContains("hello from master", r.buildAndAssertSuccess(p));
    }

    @Test
    public void withInvalidVersion() throws Exception {
        LibraryConfiguration lc =
                new LibraryConfiguration("greet", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        lc.setDefaultVersion("master");
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));

        Folder f = r.jenkins.createProject(Folder.class, "f");
        FolderConfigurations prop = new FolderConfigurations();
        LibraryCustomConfiguration item = new LibraryCustomConfiguration("greet", "unknown");
        prop.setOverrides(Collections.singletonList(item));
        f.addProperty(prop);

        WorkflowJob p = f.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('greet') _; greet(pkg.Clazz.whereAmI())", true));
        r.assertLogContains("ERROR: Could not resolve unknown", r.buildAndAssertStatus(Result.FAILURE, p));
    }

    @Test
    public void withImmutableLibrary() throws Exception {
        LibraryConfiguration lc =
                new LibraryConfiguration("greet", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())));
        lc.setDefaultVersion("master");
        lc.setAllowVersionOverride(false);
        GlobalLibraries.get().setLibraries(Collections.singletonList(lc));

        Folder f = r.jenkins.createProject(Folder.class, "f");
        FolderConfigurations prop = new FolderConfigurations();
        LibraryCustomConfiguration item = new LibraryCustomConfiguration("greet", "develop");
        prop.setOverrides(Collections.singletonList(item));
        f.addProperty(prop);

        WorkflowJob p = f.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@Library('greet') _; greet(pkg.Clazz.whereAmI())", true));
        r.assertLogContains("hello from master", r.buildAndAssertSuccess(p));
    }
}
