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
	
	private static final String PRIVATE_CONFIG_TYPE = "featuresrepo_private";
	private static final String PUBLIC_CONFIG_TYPE = "featuresrepo_public";
	
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
	 * Create and initialize a private repository
	 * @param vhost
	 */
	private void handlePrivateRepositories(VirtualHost vhost) {
		for(Map<String,Object> repo: config.getConfigList(vhost, PRIVATE_CONFIG_TYPE)) {
			initializeRepository(repo);
		}
	}
	
	/**
	 * Create and initialize a public repository
	 * @param vhost
	 * @param providers 
	 */
	private void handlePublicRepositories(VirtualHost vhost, List<FeaturesProvider> providers) {
		List<Map<String, Object>> repoList = config.getConfigList(vhost, PUBLIC_CONFIG_TYPE);
		Map<FeaturesProvider, String> providersToRepositories = new HashMap<FeaturesProvider, String>();
		for(FeaturesProvider provider : providers) {
			boolean available = false;
			for(Map<String,Object> repoConf : repoList) {
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
				Map<String,Object> repoConf = config.createConfig(vhost, PUBLIC_CONFIG_TYPE, newConf);
				repoList.add(repoConf);
				providersToRepositories.put(provider, (String) repoConf.get("_id"));
			}
		}
		for(Map<String,Object> repoConf : repoList) {
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
			handlePrivateRepositories(vhost);
			handlePublicRepositories(vhost, providers);
		}
	}
	
}
