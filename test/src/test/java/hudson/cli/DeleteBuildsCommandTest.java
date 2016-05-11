/*
 * The MIT License
 *
 * Copyright 2016 Red Hat, Inc.
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

package hudson.cli;

import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.labels.LabelAtom;
import hudson.tasks.Shell;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static hudson.cli.CLICommandInvoker.Matcher.failedWith;
import static hudson.cli.CLICommandInvoker.Matcher.hasNoStandardOutput;
import static hudson.cli.CLICommandInvoker.Matcher.succeeded;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author pjanouse
 */
public class DeleteBuildsCommandTest {

    private CLICommandInvoker command;

    @Rule public final JenkinsRule j = new JenkinsRule();

    @Before public void setUp() {
        command = new CLICommandInvoker(j, "delete-builds");
    }

    @Test public void deleteBuildsShouldFailWithoutJobReadPermission() throws Exception {
        j.createFreeStyleProject("aProject").scheduleBuild2(0).get();

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ)
                .invokeWithArgs("aProject", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'aProject'"));
    }

    @Test public void deleteBuildsShouldFailWithoutRunDeletePermission() throws Exception {
        j.createFreeStyleProject("aProject").scheduleBuild2(0).get();

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ)
                .invokeWithArgs("aProject", "1");
        assertThat(result, failedWith(6));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: user is missing the Run/Delete permission"));
    }

    @Test public void deleteBuildsShouldFailIfJobDoesNotExist() throws Exception {
        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("never_created", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job 'never_created'"));
    }

    @Test public void deleteBuildsShouldFailIfJobNameIsEmpty() throws Exception {
        j.createFreeStyleProject("aProject").scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("", "1");
        assertThat(result, failedWith(3));
        assertThat(result, hasNoStandardOutput());
        assertThat(result.stderr(), containsString("ERROR: No such job ''; perhaps you meant 'aProject'?"));
    }

    @Test public void deleteBuildsShouldSuccess() throws Exception {
        j.createFreeStyleProject("aProject").scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
    }

    @Test public void deleteBuildsShouldSuccessIfBuildDoesNotExist() throws Exception {
        j.createFreeStyleProject("aProject").scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 0 builds"));
    }

    @Test public void deleteBuildsShouldSuccessIfBuildNumberZeroSpecified() throws Exception {
        j.createFreeStyleProject("aProject").scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "0");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 0 builds"));
    }

    @Test public void deleteBuildsShouldSuccessEvenTheBuildIsRunning() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\nsleep 10s"));
        assertThat("Job wasn't scheduled properly", project.scheduleBuild(0), equalTo(true));

        // Wait until project is started (at least 1s)
        while(!project.isBuilding()) {
            System.out.println("Waiting for build to start and sleep 1s...");
            Thread.sleep(1000);
        }
        // Wait for the first sleep
        if(!project.getBuildByNumber(1).getLog().contains("echo 1")) {
            Thread.sleep(1000);
        }

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
        assertThat(project.isBuilding(), equalTo(false));
    }

    @Test public void deleteBuildsShouldSuccessEvenTheBuildIsStuckInTheQueue() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.getBuildersList().add(new Shell("echo 1\nsleep 10s"));
        project.setAssignedLabel(new LabelAtom("never_created"));
        assertThat("Job wasn't scheduled properly", project.scheduleBuild(0), equalTo(true));
        Thread.sleep(1000);
        assertThat("Job wasn't scheduled properly - it isn't in the queue", project.isInQueue(), equalTo(true));
        assertThat("Job wasn't scheduled properly - it is running on non-exist node", project.isBuilding(), equalTo(false));

        final CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 0 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).isBuilding(), equalTo(false));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).isInQueue(), equalTo(true));

        Jenkins.getInstance().getQueue().cancel(project);
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).isBuilding(), equalTo(false));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).isInQueue(), equalTo(false));
    }

    @Test public void deleteBuildsManyShouldSuccess() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(5));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 2 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(3));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "3-5");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 3 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
    }

    @Test public void deleteBuildsManyShouldSuccessEvenABuildIsSpecifiedTwice() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(2));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,1");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1-1,1-2,2-2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
    }

    @Test public void deleteBuildsManyShouldSuccessEvenLastBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(2));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "2-3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
    }

    @Test public void deleteBuildsManyShouldSuccessEvenMiddleBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.getBuildByNumber(2).delete();
        project.getBuildByNumber(5).delete();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(4));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,2,3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 2 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(2));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "4-6");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 2 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
    }

    @Test public void deleteBuildsManyShouldSuccessEvenFirstBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.getBuildByNumber(1).delete();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(2));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,2");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "2-3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
    }

    @Test public void deleteBuildsManyShouldSuccessEvenTheFirstAndLastBuildDoesNotExist() throws Exception {
        FreeStyleProject project = j.createFreeStyleProject("aProject");
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.scheduleBuild2(0).get();
        project.getBuildByNumber(1).delete();
        project.getBuildByNumber(3).delete();
        project.getBuildByNumber(5).delete();
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(2));

        CLICommandInvoker.Result result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "1,2,3");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(1));

        result = command
                .authorizedTo(Jenkins.READ, Job.READ, Run.DELETE)
                .invokeWithArgs("aProject", "3-5");
        assertThat(result, succeeded());
        assertThat(result.stdout(), containsString("Deleted 1 builds"));
        assertThat(((FreeStyleProject) j.jenkins.getItem("aProject")).getBuilds().size(), equalTo(0));
    }
}
