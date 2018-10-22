package hudson.plugins.pmd.fireline;

import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.model.ProminentProjectAction;
import jenkins.model.Jenkins;

public class FireLineScanCodeAction implements ProminentProjectAction {

	@Override
	public String getUrlName() {
		return null;
	}

	@Override
	public String getIconFileName() {
		Jenkins jenkins = Jenkins.getInstance();
		if (jenkins != null) {
			PluginManager pluginManager = jenkins.getPluginManager();
			if (pluginManager != null) {
//				PluginWrapper wrapper = pluginManager.getPlugin("fireline");
				PluginWrapper wrapper = pluginManager.getPlugin("pmd");
				if (wrapper != null){
                    return "/plugin/" + wrapper.getShortName() + "/icons/fireLine_48x48.png";
                }
			}
		}
		return null;
	}

	@Override
	public String getDisplayName() {
		return "Xuehai Static Analysis";
	}
}
