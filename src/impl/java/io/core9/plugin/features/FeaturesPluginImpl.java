package io.core9.plugin.features;

import io.core9.plugin.admin.AbstractAdminPlugin;
import io.core9.plugin.admin.plugins.AdminConfigRepository;
import io.core9.plugin.server.VirtualHost;
import io.core9.plugin.server.request.Method;
import io.core9.plugin.server.request.Request;
import io.core9.plugin.server.vertx.VertxServer;
import io.core9.plugin.session.Session;
import io.core9.plugin.session.SessionManager;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.xeoh.plugins.base.annotations.PluginImplementation;
import net.xeoh.plugins.base.annotations.injections.InjectPlugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@PluginImplementation
public class FeaturesPluginImpl extends AbstractAdminPlugin implements FeaturesPlugin {
	
	@InjectPlugin
	private AdminConfigRepository config;
	
	@InjectPlugin
	private VertxServer server;

	@InjectPlugin
	private SessionManager sessionManager;
	
	private Map<String,FeaturesProcessor> processors = new HashMap<String,FeaturesProcessor>();
	private final ObjectMapper jsonMapper = new ObjectMapper();

	
	@Override
	public FeaturesPlugin addFeatureProcessor(FeaturesProcessor processor) {
		processors.put(processor.getFeatureNamespace(), processor);
		return this;
	}
	
	@Override
	public Map<String, FeaturesProcessor> getFeatureProcessors() {
		return processors;
	}

	@Override
	public String getControllerName() {
		return "features";
	}

	@Override
	protected void process(Request request) {
		if(request.getParams().containsKey("getTypes")) {
			Map<String,Object> result = new HashMap<String,Object>();
			for(FeaturesProcessor processor : processors.values()) {
				result.put(processor.getFeatureNamespace(), processor.getProcessorAdminTemplateName());
			}
			request.getResponse().sendJsonMap(result);
		} else {
			request.getResponse().end();
		}
	}

	@Override
	protected void process(Request request, String action) {
		
        Session session = sessionManager.getVertxSession(request, server);
		//TODO encrypt git repository passwords
		String key = (String)session.get("encryptionkey");
		
		try {
			if(request.getMethod() == Method.POST) {
				Map<String,Object> body = request.getBodyAsMap();
				switch(action) {				
				case "apply":
					bootstrapFeatureVersion(request.getVirtualHost(), (String) body.get("repo"), (String) body.get("feature"), (String) body.get("version"), false);
					break;
				case "disable":
					disableFeature(request.getVirtualHost(), (String) body.get("repo"), (String) body.get("feature"));
					setFeatureVersion(request.getVirtualHost(), (String) body.get("repo"), (String) body.get("feature"), null);
					break;
				default:
					break;
				}
			}
			request.getResponse().end();
		} catch (IOException e) {
			request.getResponse().setStatusCode(500);
			request.getResponse().end(e.getMessage());
		}
	}
	
	@Override
	protected void process(Request request, String feature, String action) {
		request.getResponse().end();
	}
	
	@Override
	public void bootstrapFeatureVersion(VirtualHost vhost, String repo, String featurename, String version) throws IOException {
		bootstrapFeatureVersion(vhost, repo, featurename, version, true);
	}
	
	public void bootstrapFeatureVersion(VirtualHost vhost, String repo, String featurename, String version, boolean check) throws IOException {
		if(check) {
			String current = getFeatureVersion(vhost, repo, featurename);
			if(version.equals(current)) {
				return;
			}
		}
		applyFeature(vhost, repo, featurename, version);
		setFeatureVersion(vhost, repo, featurename, version);
	}

	/**
	 * Set the currently applied version in the database
	 * @param virtualHost
	 * @param repo
	 * @param featurename
	 * @param version
	 */
	private void setFeatureVersion(VirtualHost virtualHost, String repo, String featurename, String version) {
		Map<String,Object> repoConfig = config.readConfig(virtualHost, repo);
		if(!repoConfig.containsKey("current")) {
			repoConfig.put("current", new HashMap<String,String>());
		}
		@SuppressWarnings("unchecked")
		Map<String,String> current = (Map<String, String>) repoConfig.get("current");
		if(version != null) {
			current.put(featurename, version);
		} else {
			current.remove(featurename);
		}
		config.updateConfig(virtualHost, (String) repoConfig.get("configtype"), repo, repoConfig);
	}
	
	/**
	 * Return the current version for a feature
	 * @param vhost
	 * @param repo
	 * @param featurename
	 * @return
	 */
	private String getFeatureVersion(VirtualHost vhost, String repo, String featurename) {
		Map<String,Object> repoConfig = config.readConfig(vhost, repo);
		@SuppressWarnings("unchecked")
		Map<String,String> current = (Map<String, String>) repoConfig.get("current");
		if(current != null) {
			return current.get(featurename);
		}
		return null;
	}
	
	/**
	 * Disable the current version of a feature
	 * TODO: cleanup, almost the same as applyFeature
	 * @param vhost
	 * @param repo
	 * @param featurename
	 * @throws IOException
	 */
	private void disableFeature(VirtualHost vhost, String repo, String featurename) throws IOException {
		String currentVersion = getFeatureVersion(vhost, repo, featurename);
		if(currentVersion == null) {
			return;
		}
		File repository = new File("data/git/" + repo + "/" + featurename + "/" + currentVersion);
		File feature = new File(repository, "feature.json");
		Map<String,Object> featureMap = jsonMapper.readValue(feature, new TypeReference<Map<String,Object>>(){});
		for(Map.Entry<String, Object> featureType : featureMap.entrySet()) {
			FeaturesProcessor processor = processors.get(featureType.getKey());
			if(processor != null) {
				@SuppressWarnings("unchecked")
				List<Map<String,Object>> list = (List<Map<String, Object>>) featureType.getValue();
				for(Map<String,Object> item : list) {
					processor.removeFeature(vhost, repository, item);
				}
			}
		}
	}
	
	
	
	/**
	 * Apply the feature version
 	 * TODO: cleanup, almost the same as removeFeature
	 * @param vhost
	 * @param repo
	 * @param featurename
	 * @param version
	 * @throws IOException
	 */
	private void applyFeature(VirtualHost vhost, String repo, String featurename, String version) throws IOException {
		disableFeature(vhost, repo, featurename);
		File repository = new File("data/git/" + repo + "/" + featurename + "/" + version);
		File feature = new File(repository, "feature.json");
		Map<String,Object> featureMap = jsonMapper.readValue(feature, new TypeReference<Map<String,Object>>(){});
		for(Map.Entry<String, Object> featureType : featureMap.entrySet()) {
			FeaturesProcessor processor = processors.get(featureType.getKey());
			if(processor != null) {
				@SuppressWarnings("unchecked")
				List<Map<String,Object>> list = (List<Map<String, Object>>) featureType.getValue();
				for(Map<String,Object> item : list) {
					processor.handleFeature(vhost, repository, item);
				}
			}
		}
	}
}
