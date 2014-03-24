package io.core9.plugin.features;

import io.core9.plugin.server.VirtualHost;

import java.io.File;
import java.util.Map;


public interface FeaturesProcessor {
	
	/**
	 * Set the namespace for the processor
	 * @return
	 */
	String getFeatureNamespace();
	
	/**
	 * Set the template for the processor
	 * @return
	 */
	String getProcessorAdminTemplateName();
	
	/**
	 * Get the feature content, used for updating of contents
	 * @param vhost
	 * @param repository 
	 * @param feature (the feature)
	 * @return false if the feature should be removed
	 */
	boolean updateFeatureContent(VirtualHost vhost, File repository, Map<String,Object> feature);

	/**
	 * Handle the feature (parses its content into the system)
	 * @param repository
	 * @param item
	 */
	void handleFeature(VirtualHost vhost, File repository, Map<String, Object> feature);

	/**
	 * Remove a feature (remove it from the database)
	 * @param vhost
	 * @param repository
	 * @param item
	 */
	void removeFeature(VirtualHost vhost, File repository, Map<String, Object> item);

}
