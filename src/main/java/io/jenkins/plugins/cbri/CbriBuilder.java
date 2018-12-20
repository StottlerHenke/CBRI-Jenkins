package io.jenkins.plugins.cbri;

import hudson.Launcher;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Descriptor;
import hudson.util.FormValidation;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.tasks.BuildStepDescriptor;
import net.sf.json.JSONObject;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.servlet.ServletException;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A build step that will calculate the CBRI metrics
 */
public class CbriBuilder extends Builder implements SimpleBuildStep {

    private final String repoId;
    private final String lang;
    private final String baseUrl;
    private final String username;
    private final String password;

    @DataBoundConstructor
    public CbriBuilder(String repoId, String lang, String baseUrl, String username, String password) {

        this.repoId = repoId;
        this.lang = lang;
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
    }

    public String getRepoId() {
        return repoId;
    }

    public String getLang() {
        return lang;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    @Override
    public void perform(Run<?, ?> run, FilePath workspace, Launcher launcher, TaskListener listener) throws InterruptedException, IOException {

        DescriptorImpl desc = (DescriptorImpl) this.getDescriptor();

        //Perform the Understand measurements
        UnderstandWrapper undWrapper = new UnderstandWrapper(desc.getUndPath(), desc.getUndPerl(), desc.getPluginPath());
        CbriAction action = undWrapper.runUnderstand(lang, workspace, launcher, listener);

        //Post the actions
        CbriWrapper cbriWrapper = new CbriWrapper(baseUrl, username, password, repoId);
        cbriWrapper.postAction(action, listener);

        run.addAction(action);

    }

    /**
     * The descriptor stores meta-data used by all instances.
     */
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {

        protected final static String SUPPORTED_LANGUAGES = "Ada, C, C#, C++, FORTRAN, Java";

        private String undPath;
        private String undPerl;
        private String pluginPath;

        public String getUndPath() {
            return undPath;
        }
        public String getUndPerl() {
            return undPerl;
        }
        public String getPluginPath() { return pluginPath; }

        public DescriptorImpl() {
            super(CbriBuilder.class);
            load();
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject json) throws Descriptor.FormException {
            json = json.getJSONObject("cbri");
            undPath = json.getString("undPath");
            undPerl = json.getString("undPerl");
            pluginPath = json.getString("pluginPath");
            save();
            return true;
        }

        public FormValidation doCheckRepoId(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Missing repo id");

            return FormValidation.ok();
        }

        public FormValidation doCheckBaseUrl(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Missing CBRI REST API url");
            if (!value.contains("/api"))
                return FormValidation.error("Incorrectly formatted url must end in '/api'");

            return FormValidation.ok();
        }

        public FormValidation doCheckPassword(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Missing password");

            return FormValidation.ok();
        }

        public FormValidation doCheckUsername(@QueryParameter String value)
                throws IOException, ServletException {
            if (value.length() == 0)
                return FormValidation.error("Missing username");

            return FormValidation.ok();
        }

        public FormValidation doCheckLang(@QueryParameter String value)
                throws IOException, ServletException {

            if (value.length() == 0)
                return FormValidation.error("Missing lang");
            if (!SUPPORTED_LANGUAGES.contains(value))
                return FormValidation.error("Language must be one of: " + SUPPORTED_LANGUAGES);

            return FormValidation.ok();
        }

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Calculate CBRI Core Metrics";
        }

    }

}