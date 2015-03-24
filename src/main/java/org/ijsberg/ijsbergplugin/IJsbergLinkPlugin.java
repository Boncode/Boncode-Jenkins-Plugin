package org.ijsberg.ijsbergplugin;

import hudson.Extension;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.util.FormValidation;
import net.sf.json.JSONObject;
import nl.ijsberg.analysis.server.buildserver.BuildServerToMonitorLink;
import nl.ijsberg.analysis.server.buildserver.ValidationResult;
import org.ijsberg.iglu.logging.Level;
import org.ijsberg.iglu.logging.LogEntry;
import org.ijsberg.iglu.logging.Logger;
import org.ijsberg.iglu.util.collection.ArraySupport;
import org.ijsberg.iglu.util.http.HttpIoSupport;
import org.ijsberg.iglu.util.io.FSFileCollection;
import org.ijsberg.iglu.util.io.FileFilterRuleSet;
import org.ijsberg.iglu.util.io.FileSupport;
import org.ijsberg.iglu.util.io.ZipFileStreamProvider;
import org.ijsberg.iglu.util.misc.StringSupport;
import org.ijsberg.iglu.util.properties.PropertiesSupport;
import org.ijsberg.iglu.util.xml.Document;
import org.ijsberg.iglu.util.xml.Node;
import org.ijsberg.iglu.util.xml.ParseException;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.servlet.ServletException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * IJsbergLinkPlugin {@link Builder}.
 *
 * <p>
 * When the user configures the project and enables this builder,
 * {@link org.ijsberg.ijsbergplugin.IJsbergLinkPlugin.DescriptorImpl#newInstance(StaplerRequest)} is invoked
 * and a new {@link org.ijsberg.ijsbergplugin.IJsbergLinkPlugin} is created. The created
 * instance is persisted to the project configuration XML by using
 * XStream, so this allows you to use instance fields (like {@link #monitorDownloadDirectory})
 * to remember the configuration.
 *
 * <p>
 * When a build is performed, the {@link #perform(AbstractBuild, Launcher, BuildListener)}
 * method will be invoked. 
 *
 */
public class IJsbergLinkPlugin extends Builder implements Logger {

	private final String monitorUploadDirectory;
	private final String monitorDownloadDirectory;


	private final String analysisProperties;
	private final Properties properties = new Properties();

	public static final String SNAPSHOT_TIMESTAMP_FORMAT = "yyyyMMdd_HH_mm";

	// Fields in config.jelly must match the parameter names in the "DataBoundConstructor"

	@DataBoundConstructor
	public IJsbergLinkPlugin(String analysisProperties, String monitorUploadDirectory, String monitorDownloadDirectory) throws IOException {

		this.monitorUploadDirectory = monitorUploadDirectory;
		this.analysisProperties = analysisProperties;
		this.monitorDownloadDirectory = monitorDownloadDirectory;
		BuildServerToMonitorLink.throwIfPropertiesNotOk(analysisProperties, monitorUploadDirectory, monitorDownloadDirectory);
	}


	/**
	 * We'll use this from the <tt>config.jelly</tt>.
	 */
/*	public String getCustomerId() {
		return customerId;
	}

	public String getProjectId() {
		return projectId;
	} */

	public String getMonitorUploadDirectory() {
		return monitorUploadDirectory;
	}

	public String getMonitorDownloadDirectory() {
		return monitorDownloadDirectory;
	}

	public String getAnalysisProperties() {
		return analysisProperties;
	}

	private PrintStream logStream = System.out;

	@Override
	public boolean perform(AbstractBuild build, Launcher launcher, BuildListener listener) {

		logStream = listener.getLogger();

		//String uploadDir = getCurrentUploadDir(listener);

		new BuildServerToMonitorLink(analysisProperties, monitorUploadDirectory, monitorDownloadDirectory, this).perform(build.getWorkspace().getRemote());

		logStream = System.out;

		return true;
	}

/*	private String getCurrentUploadDir(BuildListener listener) {
		String uploadDir = monitorUploadDirectory;
/ *		if (getDescriptor().getAlternativeUploadDirectory() != null && !"".equals(getDescriptor().getAlternativeUploadDirectory())) {
			uploadDir = getDescriptor().getAlternativeUploadDirectory();
		} * /
		listener.getLogger().println("got request to analyze project " + projectId + " for " + customerId);
		return uploadDir;
	}*/


	// Overridden for better type safety.
	// If your plugin doesn't really define any property on Descriptor,
	// you don't have to do this.
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl)super.getDescriptor();
	}

	public void log(LogEntry entry) {
		logStream.println(entry);
	}

	public String getStatus() {
		return null;
	}

	public void addAppender(Logger appender) {
	}

	public void removeAppender(Logger appender) {
	}

	/**
	 * Descriptor for {@link IJsbergLinkPlugin}. Used as a singleton.
	 * The class is marked as public so that it can be accessed from views.
	 *
	 * <p>
	 * See <tt>src/main/resources/hudson/plugins/hello_world/HelloWorldBuilder/*.jelly</tt>
	 * for the actual HTML fragment for the configuration screen.
	 */
	@Extension // This indicates to Jenkins that this is an implementation of an extension point.
	public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
		/**
		 * To persist global configuration information,
		 * simply store it in a field and call save().
		 *
		 * <p>
		 * If you don't want fields to be persisted, use <tt>transient</tt>.
		 */
		private String alterantiveUploadDirectory;

		/**
		 * In order to load the persisted global configuration, you have to
		 * call load() in the constructor.
		 */
		public DescriptorImpl() {
			load();
		}

		/**
		 * Performs on-the-fly validation of the form field 'name'.
		 *
		 * @param value
		 *      This parameter receives the value that the user has typed.
		 * @return
		 *      Indicates the outcome of the validation. This is sent to the browser.
		 *      <p>
		 *      Note that returning {@link FormValidation#error(String)} does not
		 *      prevent the form from being saved. It just means that a message
		 *      will be displayed to the user.
		 */
		public FormValidation doCheckAnalysisProperties(@QueryParameter String value)
				throws IOException, ServletException {

			ValidationResult checkedAnalysisProperties = BuildServerToMonitorLink.checkAnalysisProperties(value);
			if(checkedAnalysisProperties.isOk()) {
				return FormValidation.ok();
			} else {
				return FormValidation.error(checkedAnalysisProperties.getLastMessage());
			}
		}


		public FormValidation doCheckMonitorUploadDirectory(@QueryParameter String value)
				throws IOException, ServletException {
			ValidationResult checkedAnalysisProperties = BuildServerToMonitorLink.checkMonitorUploadDirectory(value);
			if(checkedAnalysisProperties.isOk()) {
				return FormValidation.ok();
			} else {
				return FormValidation.error(checkedAnalysisProperties.getLastMessage());
			}
		}

		public FormValidation doCheckMonitorDownloadDirectory(@QueryParameter String value)
				throws IOException, ServletException {
			ValidationResult checkedAnalysisProperties = BuildServerToMonitorLink.checkMonitorDownloadDirectory(value);
			if(checkedAnalysisProperties.isOk()) {
				return FormValidation.ok();
			} else {
				return FormValidation.error(checkedAnalysisProperties.getLastMessage());
			}
		}

		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project types
			return true;
		}

		/**
		 * This human readable name is used in the configuration screen.
		 */
		public String getDisplayName() {
			return "Link to IJsberg Monitor Server";
		}

		@Override
		public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
			// To persist global configuration information,
			// set that to properties and call save().
			alterantiveUploadDirectory = formData.getString("alternativeUploadDirectory");
			// ^Can also use req.bindJSON(this, formData);
			//  (easier when there are many fields; need set* methods for this, like setUseFrench)
			save();
			return super.configure(req,formData);
		}

		/**
		 */
		public String getAlternativeUploadDirectory() {
			return alterantiveUploadDirectory;
		}
	}


}

