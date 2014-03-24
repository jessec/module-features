package io.core9.plugin.features.processors;

import io.core9.plugin.admin.plugins.AdminConfigRepository;
import io.core9.plugin.server.VirtualHost;

import java.io.File;
import java.util.Map;

import net.xeoh.plugins.base.annotations.PluginImplementation;
import net.xeoh.plugins.base.annotations.injections.InjectPlugin;

@PluginImplementation
public class FeaturesConfigProcessorImpl implements FeaturesConfigProcessor {
	
	@InjectPlugin
	private AdminConfigRepository config;

	@Override
	public String getFeatureNamespace() {
		return "configuration";
	}

	@Override
	public String getProcessorAdminTemplateName() {
		return "features/processor/config.tpl.html";
	}

	@Override
	public boolean updateFeatureContent(VirtualHost vhost, File repository,	Map<String, Object> item) {
		Map<String,Object> entry = config.readConfig(vhost, (String) item.get("id"));
		if(entry != null) {
			item.put("entry", entry);
			return true;
		}
		return false;
	}

	@SuppressWarnings("unchecked")
	@Override
	public void handleFeature(VirtualHost vhost, File repository, Map<String, Object> item) {
		Map<String,Object> entry = (Map<String, Object>) item.get("entry");
		if(entry != null) {
			config.createConfig(vhost, (String) entry.get("configtype"), entry);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public void removeFeature(VirtualHost vhost, File repository, Map<String, Object> item) {
		Map<String,Object> entry = (Map<String, Object>) item.get("entry");
		if(entry != null) {
			config.deleteConfig(vhost, (String) entry.get("configtype"), (String) item.get("id"));
		}
	}

}
