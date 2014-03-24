package io.core9.plugin.features.processors;

import io.core9.plugin.admin.plugins.AdminConfigRepository;
import io.core9.plugin.admin.plugins.AdminContentRepository;
import io.core9.plugin.server.VirtualHost;

import java.io.File;
import java.util.Map;

import net.xeoh.plugins.base.annotations.PluginImplementation;
import net.xeoh.plugins.base.annotations.injections.InjectPlugin;

@PluginImplementation
public class FeaturesContentProcessorImpl implements FeaturesContentProcessor {
	
	@InjectPlugin
	private AdminConfigRepository config;
	
	@InjectPlugin
	private AdminContentRepository repository;
	
	@Override
	public String getFeatureNamespace() {
		return "content";
	}
	
	@Override
	public String getProcessorAdminTemplateName() {
		return "features/processor/content.tpl.html";
	}

	@Override
	public boolean updateFeatureContent(VirtualHost vhost, File repositoryPath, Map<String, Object> feature) {
		String[] id = ((String) feature.get("id")).split("-");
		if(id.length == 2) {
			Map<String,Object> entry = repository.readContent(vhost, id[0], id[1]);
			if(entry != null) {
				feature.put("entry", entry);
				return true;
			}
		}
		return false;
	}

	@Override
	public void handleFeature(VirtualHost vhost, File repository, Map<String, Object> feature) {
		@SuppressWarnings("unchecked")
		Map<String,Object> entry = (Map<String, Object>) feature.get("entry");
		this.repository.createContent(vhost, (String) entry.get("contenttype"), entry);
	}

	@Override
	public void removeFeature(VirtualHost vhost, File repository, Map<String, Object> item) {
		@SuppressWarnings("unchecked")
		Map<String,Object> entry = (Map<String, Object>) item.get("entry");
		this.repository.deleteContent(vhost, (String) entry.get("contenttype"), (String) entry.get("_id"));
	}
	
}
