/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Tom Huybrechts
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
package hudson.model;

import jenkins.model.Jenkins;
import org.jvnet.hudson.test.Bug;
import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequestSettings;
import com.gargoylesoftware.htmlunit.html.HtmlAnchor;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlLabel;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlRadioButtonInput;
import hudson.XmlFile;
import hudson.matrix.AxisList;
import hudson.matrix.LabelAxis;
import hudson.matrix.MatrixProject;
import org.jvnet.hudson.test.Email;
import org.w3c.dom.Text;

import static hudson.model.Messages.Hudson_ViewName;
import hudson.util.HudsonIsLoading;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsRule.WebClient;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class ViewTest {

    @Rule public JenkinsRule j = new JenkinsRule();

    @Bug(7100)
    @Test public void xHudsonHeader() throws Exception {
        assertNotNull(j.createWebClient().goTo("").getWebResponse().getResponseHeaderValue("X-Hudson"));
    }

    /**
     * Creating two views with the same name.
     */
    @Email("http://d.hatena.ne.jp/ssogabe/20090101/1230744150")
    @Test public void conflictingName() throws Exception {
        assertNull(j.jenkins.getView("foo"));

        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("foo");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        j.submit(form);
        assertNotNull(j.jenkins.getView("foo"));

        // do it again and verify an error
        try {
            j.submit(form);
            fail("shouldn't be allowed to create two views of the same name.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Test public void privateView() throws Exception {
        j.createFreeStyleProject("project1");
        User user = User.get("me", true); // create user

        WebClient wc = j.createWebClient();
        HtmlPage userPage = wc.goTo("user/me");
        HtmlAnchor privateViewsLink = userPage.getFirstAnchorByText("My Views");
        assertNotNull("My Views link not available", privateViewsLink);

        HtmlPage privateViewsPage = (HtmlPage) privateViewsLink.click();

        Text viewLabel = (Text) privateViewsPage.getFirstByXPath("//table[@id='viewList']//td[@class='active']/text()");
        assertTrue("'All' view should be selected", viewLabel.getTextContent().contains(Hudson_ViewName()));

        View listView = new ListView("listView", j.jenkins);
        j.jenkins.addView(listView);

        HtmlPage newViewPage = wc.goTo("user/me/my-views/newView");
        HtmlForm form = newViewPage.getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("proxy-view");
        ((HtmlRadioButtonInput) form.getInputByValue("hudson.model.ProxyView")).setChecked(true);
        HtmlPage proxyViewConfigurePage = j.submit(form);
        View proxyView = user.getProperty(MyViewsProperty.class).getView("proxy-view");
        assertNotNull(proxyView);
        form = proxyViewConfigurePage.getFormByName("viewConfig");
        form.getSelectByName("proxiedViewName").setSelectedAttribute("listView", true);
        j.submit(form);

        assertTrue(proxyView instanceof ProxyView);
        assertEquals(((ProxyView) proxyView).getProxiedViewName(), "listView");
        assertEquals(((ProxyView) proxyView).getProxiedView(), listView);
    }

    @Test public void deleteView() throws Exception {
        WebClient wc = j.createWebClient();

        ListView v = new ListView("list", j.jenkins);
        j.jenkins.addView(v);
        HtmlPage delete = wc.getPage(v, "delete");
        j.submit(delete.getFormByName("delete"));
        assertNull(j.jenkins.getView("list"));

        User user = User.get("user", true);
        MyViewsProperty p = user.getProperty(MyViewsProperty.class);
        v = new ListView("list", p);
        p.addView(v);
        delete = wc.getPage(v, "delete");
        j.submit(delete.getFormByName("delete"));
        assertNull(p.getView("list"));

    }

    @Bug(9367)
    @Test public void persistence() throws Exception {
        ListView view = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);

        ListView v = (ListView) Jenkins.XSTREAM.fromXML(Jenkins.XSTREAM.toXML(view));
        System.out.println(v.getProperties());
        assertNotNull(v.getProperties());
    }

    @Bug(9367)
    @Test public void allImagesCanBeLoaded() throws Exception {
        User.get("user", true);
        WebClient webClient = j.createWebClient();
        webClient.setJavaScriptEnabled(false);
        j.assertAllImageLoadSuccessfully(webClient.goTo("asynchPeople"));
    }

    @Bug(16608)
    @Test public void notAllowedName() throws Exception {
        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        form.getInputByName("name").setValueAttribute("..");
        form.getRadioButtonsByName("mode").get(0).setChecked(true);

        try {
            j.submit(form);
            fail("\"..\" should not be allowed.");
        } catch (FailingHttpStatusCodeException e) {
            assertEquals(400, e.getStatusCode());
        }
    }

    @Ignore("verified manually in Winstone but org.mortbay.JettyResponse.sendRedirect (6.1.26) seems to mangle the location")
    @Bug(18373)
    @Test public void unicodeName() throws Exception {
        HtmlForm form = j.createWebClient().goTo("newView").getFormByName("createItem");
        String name = "I ♥ NY";
        form.getInputByName("name").setValueAttribute(name);
        form.getRadioButtonsByName("mode").get(0).setChecked(true);
        j.submit(form);
        View view = j.jenkins.getView(name);
        assertNotNull(view);
        j.submit(j.createWebClient().getPage(view, "configure").getFormByName("viewConfig"));
    }

    @Bug(17302)
    @Test public void doConfigDotXml() throws Exception {
        ListView view = new ListView("v", j.jenkins);
        view.description = "one";
        j.jenkins.addView(view);
        WebClient wc = j.createWebClient();
        String xml = wc.goToXml("view/v/config.xml").getContent();
        assertTrue(xml, xml.contains("<description>one</description>"));
        xml = xml.replace("<description>one</description>", "<description>two</description>");
        WebRequestSettings req = new WebRequestSettings(wc.createCrumbedUrl("view/v/config.xml"), HttpMethod.POST);
        req.setRequestBody(xml);
        wc.getPage(req);
        assertEquals("two", view.getDescription());
        xml = new XmlFile(Jenkins.XSTREAM, new File(j.jenkins.getRootDir(), "config.xml")).asString();
        assertTrue(xml, xml.contains("<description>two</description>"));
    }
    
    @Test
    public void testGetQueueItems() throws IOException, Exception{
        ListView view = new ListView("foo", j.jenkins);
        ListView view2 =new ListView("foo2", j.jenkins);
        j.jenkins.addView(view);
        j.jenkins.addView(view2);
        FreeStyleProject job1 = j.jenkins.createProject(FreeStyleProject.class, "free");
        MatrixProject job2 = j.jenkins.createProject(MatrixProject.class, "matrix");
        FreeStyleProject job = j.jenkins.createProject(FreeStyleProject.class, "not-in-view");
        FreeStyleProject job3 = j.jenkins.createProject(FreeStyleProject.class, "in-other-view");
        view.filterQueue=true;
        view.jobNames.add(job1.getDisplayName());
        view.jobNames.add(job2.getDisplayName());
        view2.filterQueue=true;
        view2.jobNames.add(job3.getDisplayName());
        job1.setAssignedLabel(j.jenkins.getLabelAtom("without-any-slave"));
        job2.setAssignedLabel(j.jenkins.getLabelAtom("without-any-slave"));
        job.setAssignedLabel(j.jenkins.getLabelAtom("without-any-slave"));
        job3.setAssignedLabel(j.jenkins.getLabelAtom("without-any-slave"));
        Queue.Item item = Queue.getInstance().schedule(job, 0);
        Queue.Item item1 = Queue.getInstance().schedule(job1, 0);
        Queue.Item item2 = Queue.getInstance().schedule(job2, 0);
        Queue.Item item3 = Queue.getInstance().schedule(job3, 0);
        Thread.sleep(1000);
        assertTrue("Queued items for view " + view.getDisplayName() + " should contain job " + job1.getDisplayName(),view.getQueueItems().contains(Queue.getInstance().getItem(job1)));
        assertTrue("Queued items for view " + view.getDisplayName() + " should contain job " + job2.getDisplayName(),view.getQueueItems().contains(Queue.getInstance().getItem(job2)));
        assertTrue("Queued items for view " + view2.getDisplayName() + " should contain job " + job3.getDisplayName(),view2.getQueueItems().contains(Queue.getInstance().getItem(job3)));
        assertFalse("Queued items for view " + view.getDisplayName() + " should not contain job " + job.getDisplayName(), view.getQueueItems().contains(Queue.getInstance().getItem(job)));
        assertFalse("Queued items for view " + view.getDisplayName() + " should not contain job " + job3.getDisplayName(), view.getQueueItems().contains(Queue.getInstance().getItem(job3)));
        assertFalse("Queued items for view " + view2.getDisplayName() + " should not contain job " + job.getDisplayName(), view2.getQueueItems().contains(Queue.getInstance().getItem(job)));
        assertFalse("Queued items for view " + view2.getDisplayName() + " should not contain job " + job1.getDisplayName(), view2.getQueueItems().contains(Queue.getInstance().getItem(job1)));
        assertFalse("Queued items for view " + view2.getDisplayName() + " should not contain job " + job2.getDisplayName(), view2.getQueueItems().contains(Queue.getInstance().getItem(job2)));
    }
    
    @Test
    public void testGetComputers() throws IOException, Exception{
        ListView view1 = new ListView("view1", j.jenkins);
        ListView view2 = new ListView("view2", j.jenkins);
        ListView view3 = new ListView("view3", j.jenkins);
        j.jenkins.addView(view1);
        j.jenkins.addView(view2);
        j.jenkins.addView(view3);
        view1.filterExecutors=true;
        view2.filterExecutors=true;
        view3.filterExecutors=true;

        Slave slave0 = j.createOnlineSlave(j.jenkins.getLabel("label0"));
        Slave slave1 = j.createOnlineSlave(j.jenkins.getLabel("label1"));
        Slave slave2 = j.createOnlineSlave(j.jenkins.getLabel("label2"));
        Slave slave3 = j.createOnlineSlave(j.jenkins.getLabel("label0"));
        Slave slave4 = j.createOnlineSlave(j.jenkins.getLabel("label4"));

        FreeStyleProject freestyleJob = j.jenkins.createProject(FreeStyleProject.class, "free");
        view1.add(freestyleJob);
        freestyleJob.setAssignedLabel(j.jenkins.getLabel("label0||label2"));

        MatrixProject matrixJob = j.jenkins.createProject(MatrixProject.class, "matrix");
        view1.add(matrixJob);
        matrixJob.setAxes(new AxisList(
                new LabelAxis("label", Arrays.asList("label1"))
        ));

        FreeStyleProject noLabelJob = j.jenkins.createProject(FreeStyleProject.class, "not-assigned-label");
        view3.add(noLabelJob);
        noLabelJob.setAssignedLabel(null);

        FreeStyleProject foreignJob = j.jenkins.createProject(FreeStyleProject.class, "in-other-view");
        view2.add(foreignJob);
        foreignJob.setAssignedLabel(j.jenkins.getLabel("label0||label1"));

        // contains all slaves having labels associated with freestyleJob or matrixJob
        assertContains(view1, slave0, slave1, slave2, slave3);
        assertNotContains(view1, slave4);

        // contains all slaves having labels associated with foreignJob
        assertContains(view2, slave0, slave1, slave3);
        assertNotContains(view2, slave2, slave4);

        // contains all slaves as it contains noLabelJob that can run everywhere
        assertContains(view3, slave0, slave1, slave2, slave3, slave4);
    }

    private void assertContains(View view, Node... slaves) {
        for (Node slave: slaves) {
            assertTrue(
                    "Filtered executors for view " + view.getDisplayName() + " should contain slave " + slave.getDisplayName(),
                    view.getComputers().contains(slave.toComputer())
            );
        }
    }

    private void assertNotContains(View view, Node... slaves) {
        for (Node slave: slaves) {
            assertFalse(
                    "Filtered executors for view " + view.getDisplayName() + " should not contain slave " + slave.getDisplayName(),
                    view.getComputers().contains(slave.toComputer())
            );
        }
    }

    @Test
    public void testGetItem() throws Exception{
        ListView view = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);
        FreeStyleProject job1 = j.jenkins.createProject(FreeStyleProject.class, "free");
        MatrixProject job2 = j.jenkins.createProject(MatrixProject.class, "matrix");
        FreeStyleProject job3 = j.jenkins.createProject(FreeStyleProject.class, "not-included");
        view.jobNames.add(job2.getDisplayName());
        view.jobNames.add(job1.getDisplayName());
        assertEquals("View should return job " + job1.getDisplayName(),job1,  view.getItem("free"));
        assertNotNull("View should return null.", view.getItem("not-included"));
    }
    
    @Test
    public void testRename() throws Exception {
        ListView view = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);
        view.rename("renamed");
        assertEquals("View should have name foo.", "renamed", view.getDisplayName());
        ListView view2 = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);
        try{
            view2.rename("renamed");
            fail("Attemt to rename job with a name used by another view with the same owner should throw exception");
        }
        catch(Exception Exception){
        }
        assertEquals("View should not be renamed if required name has another view with the same owner", "foo", view2.getDisplayName());
    }
    
    @Test
    public void testGetOwnerItemGroup() throws Exception {
        ListView view = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);
        assertEquals("View should have owner jenkins.",j.jenkins.getItemGroup(), view.getOwnerItemGroup());
    }
    
    @Test
    public void testGetOwnerPrimaryView() throws Exception{
        ListView view = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);
        j.jenkins.setPrimaryView(view);
        assertEquals("View should have primary view " + view.getDisplayName(),view, view.getOwnerPrimaryView());
    }
    
    @Test
    public void testSave() throws Exception{
        ListView view = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);
        FreeStyleProject job = j.jenkins.createProject(FreeStyleProject.class, "free");
        view.jobNames.add("free");
        view.save();
        j.jenkins.doReload();
        //wait until all configuration are reloaded
        if(j.jenkins.servletContext.getAttribute("app") instanceof HudsonIsLoading){
            Thread.sleep(500);
        }
        assertTrue("View does not contains job free after load.", j.jenkins.getView(view.getDisplayName()).contains(j.jenkins.getItem(job.getName())));       
    }
    
    @Test
    public void testGetProperties() throws Exception {
        View view = new ListView("foo", j.jenkins);
        j.jenkins.addView(view);
        Thread.sleep(100000);
        HtmlForm f = j.createWebClient().getPage(view, "configure").getFormByName("viewConfig");
        ((HtmlLabel)f.selectSingleNode(".//LABEL[text()='Test property']")).click();
        j.submit(f);
        assertNotNull("View should contains ViewPropertyImpl property.", view.getProperties().get(PropertyImpl.class));
    }

    public static class PropertyImpl extends ViewProperty {
        public String name;

        @DataBoundConstructor
        public PropertyImpl(String name) {
            this.name = name;
        }

        @TestExtension
        public static class DescriptorImpl extends ViewPropertyDescriptor {
            @Override
            public String getDisplayName() {
                return "Test property";
            }
        }
    }


}
