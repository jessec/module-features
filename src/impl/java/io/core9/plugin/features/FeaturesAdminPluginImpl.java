package io.core9.plugin.features;

import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
public class FeaturesAdminPluginImpl implements FeaturesAdminPlugin {

	@Override
	public String getRepositoryPath() {
		return "https://github.com/core9/feature-admin-dashboard.git";
	}

	@Override
	public String getFeatureName() {
		return "core9/features";
	}

	@Override
	public String getFeatureVersion() {
		return "1.0.0";
	}

}
