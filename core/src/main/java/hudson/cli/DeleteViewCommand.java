/*
 * The MIT License
 *
 * Copyright (c) 2013-5 Red Hat, Inc.
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

import hudson.Extension;
import hudson.model.ViewGroup;
import hudson.model.View;

import jenkins.model.Jenkins;
import org.kohsuke.args4j.Argument;

import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author ogondza, pjanouse
 * @since 1.538
 */
@Extension
public class DeleteViewCommand extends CLICommand {

    @Argument(usage="View names to delete", required=true, multiValued=true)
    private List<String> views;

    @Override
    public String getShortDescription() {

        return Messages.DeleteViewCommand_ShortDescription();
    }

    @Override
    protected int run() throws Exception {

        boolean errorOccurred = false;

        // Remove duplicates
        HashSet<String> hs = new HashSet<String>();
        hs.addAll(views);

        for(String view_s : hs) {
            View view = null;

            ViewGroup group = Jenkins.getInstance();

            final StringTokenizer tok = new StringTokenizer(view_s, "/");
            while (tok.hasMoreTokens()) {
                String viewName = tok.nextToken();

                view = group.getView(viewName);
                if (view == null) {
                    stderr.format("No view named %s inside view %s", viewName, group.getDisplayName());
                    errorOccurred = true;
                    break;
                }

                try {
                    view.checkPermission(View.READ);
                } catch (Exception e) {
                    stderr.println(e.getMessage());
                    errorOccurred = true;
                    view = null;
                    break;
                }

                if (view instanceof ViewGroup) {
                    group = (ViewGroup) view;
                } else if (tok.hasMoreTokens()) {
                    stderr.format("%s view can not contain views", view.getViewName());
                    view = null;
                    break;
                }
            }

            if(view ==null)
                continue;

            try {
                view.checkPermission(View.DELETE);
            } catch (Exception e) {
                stderr.println(e.getMessage());
                errorOccurred = true;
                continue;
            }

            try {
                group = view.getOwner();
                if (!group.canDelete(view)) {
                    stderr.format("%s does not allow to delete '%s' view\n",
                            group.getDisplayName(),
                            view.getViewName()
                    );
                    errorOccurred = true;
                    continue;
                } else {
                    group.deleteView(view);
                }
            } catch (Exception e) {
                stderr.format("Unexpected exception occurred during deletion of view '%s': %s\n",
                        view.getViewName(),
                        e.getMessage()
                );
                errorOccurred = true;
            }
        }
        return errorOccurred ? -1 : 0;
    }
}
