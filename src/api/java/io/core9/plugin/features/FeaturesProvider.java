package io.core9.plugin.features;

public interface FeaturesProvider {

	/**
	 * Return the repository path for the feature
	 * @return
	 */
	String getRepositoryPath();
	
	/**
	 * Return the feature name to be handled
	 * @return
	 */
	String getFeatureName();
	
	/**
	 * Return the feature version to be handled
	 * @return
	 */
	String getFeatureVersion();
}
