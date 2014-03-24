package io.core9.plugin.features;

import io.core9.core.boot.CoreBootStrategy;
import io.core9.plugin.admin.plugins.AdminConfigRepository;
import io.core9.plugin.git.GitRepository;
import io.core9.plugin.git.GitRepositoryManager;
import io.core9.plugin.server.VirtualHost;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.xeoh.plugins.base.Plugin;
import net.xeoh.plugins.base.annotations.PluginImplementation;
import net.xeoh.plugins.base.annotations.injections.InjectPlugin;

import org.apache.commons.lang3.ClassUtils;

@PluginImplementation
public class FeaturesBootstrapImpl extends CoreBootStrategy implements FeaturesBootstrap {
	
	private VirtualHost[] vhosts;
	
	@InjectPlugin
	private AdminConfigRepository config;
	
	@InjectPlugin
	private GitRepositoryManager git;
	
	@InjectPlugin
	private FeaturesPlugin features;
	
	@Override
	public void process(VirtualHost[] vhosts) {
		this.vhosts = vhosts;
	}

	/**
	 * Create and initialize an internal repository
	 * @param vhost
	 */
	private void handleInternalRepository(VirtualHost vhost) {
		List<Map<String, Object>> tmpList = config.getConfigList(vhost, "featuresrepo_int");
		if(tmpList.size() > 0) {
			initializeRepository(tmpList.get(0));
		}
	}
	
	/**
	 * Create and initialize an external repository
	 * @param vhost
	 * @param providers 
	 */
	private void handleExternalRepositories(VirtualHost vhost, List<FeaturesProvider> providers) {
		List<Map<String, Object>> tmpList = config.getConfigList(vhost, "featuresrepo_ext");
		Map<FeaturesProvider, String> providersToRepositories = new HashMap<FeaturesProvider, String>();
		for(FeaturesProvider provider : providers) {
			boolean available = false;
			for(Map<String,Object> repoConf : tmpList) {
				available = repoConf.get("path").equals(provider.getRepositoryPath());
				if(available) {
					providersToRepositories.put(provider, (String) repoConf.get("_id"));
					break;
				}
			}
			if(!available) {
				Map<String,Object> newConf = new HashMap<String,Object>();
				newConf.put("path", provider.getRepositoryPath());
				newConf.put("user", null);
				newConf.put("password", null);
				Map<String,Object> repoConf = config.createConfig(vhost, "featuresrepo_ext", newConf);
				tmpList.add(repoConf);
				providersToRepositories.put(provider, (String) repoConf.get("_id"));
			}
		}
		for(Map<String,Object> repoConf : tmpList) {
			initializeRepository(repoConf);
		}
		handleFeatureProviders(vhost, providersToRepositories);
	}
	
	/**
	 * Handle the feature providers
	 * @param providersToRepositories
	 */
	private void handleFeatureProviders(VirtualHost vhost, Map<FeaturesProvider, String> providersToRepositories) {
		for(Map.Entry<FeaturesProvider, String> entry: providersToRepositories.entrySet()) {
			try {
				features.bootstrapFeatureVersion(vhost, entry.getValue(), entry.getKey().getFeatureName(), entry.getKey().getFeatureVersion());
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Initialize a repository
	 * @param repoConf
	 * @return
	 */
	private GitRepository initializeRepository(Map<String,Object> repoConf) {
		GitRepository repo = git.registerRepository((String) repoConf.get("_id"));
		repo.setLocalPath((String) repoConf.get("_id"));
		repo.setOrigin((String) repoConf.get("path"));
		repo.setUsername((String) repoConf.get("user"));
		repo.setPassword((String) repoConf.get("password"));
		git.init(repo);
		return repo;
	}

	@Override
	public Integer getPriority() {
		return 1560;
	}

	@Override
	public void processPlugins() {
		List<FeaturesProvider> providers = new ArrayList<FeaturesProvider>();
		for(Plugin plugin: this.registry.getPlugins()) {
			List<Class<?>> interfaces = ClassUtils.getAllInterfaces(plugin.getClass());
			if (interfaces.contains(FeaturesProvider.class)) {
				providers.add((FeaturesProvider) plugin);
			}
			if (interfaces.contains(FeaturesProcessor.class)) {
				features.addFeatureProcessor((FeaturesProcessor) plugin);
			}
		}
		for(VirtualHost vhost : vhosts) {
			handleInternalRepository(vhost);
			handleExternalRepositories(vhost, providers);
		}
	}
	
}
