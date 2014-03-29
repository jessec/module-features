package io.core9.plugin.features;

import io.core9.plugin.admin.AbstractAdminPlugin;
import io.core9.plugin.admin.plugins.AdminConfigRepository;
import io.core9.plugin.git.GitRepository;
import io.core9.plugin.git.GitRepositoryManager;
import io.core9.plugin.server.VirtualHost;
import io.core9.plugin.server.request.Request;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.xeoh.plugins.base.annotations.PluginImplementation;
import net.xeoh.plugins.base.annotations.injections.InjectPlugin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

@PluginImplementation
public class FeaturesRepositoryPluginImpl extends AbstractAdminPlugin implements FeaturesRepositoryPlugin {

	@InjectPlugin
	private AdminConfigRepository config;
	
	@InjectPlugin
	private GitRepositoryManager git;
	
	@InjectPlugin
	private FeaturesPlugin features;
	
	private final ObjectMapper jsonMapper = new ObjectMapper();

	@Override
	public String getControllerName() {
		return "featurerepository";
	}

	@Override
	protected void process(Request request) {
		request.getResponse().end();
	}

	@Override
	protected void process(Request request, String repoID) {
		try {
			switch(request.getMethod()) {
			case POST:
				if(request.getParams().containsKey("pull")) {
					pullRepository(request.getVirtualHost(), repoID);
				} else if(request.getParams().containsKey("push")) {
					pushRepository(request.getVirtualHost(), repoID);
				}
			default:
				request.getResponse().sendJsonArray(readRepositoryFeatures(repoID));
			}
		} catch (IOException e) {
			request.getResponse().setStatusCode(500);
			request.getResponse().end(e.getMessage());
		}
	}

	@Override
	protected void process(Request request, String repoID, String feature) {
		feature = feature.replace("%2F", "/");
		int versionPos = feature.indexOf("-v");
		if(versionPos != -1) {
			process(request, repoID, feature.substring(0, versionPos), feature.substring(versionPos + 2));
		} else {
			try {
				List<Map<String,Object>> features = readRepositoryFeatures(repoID);
				switch(request.getMethod()) {
				case DELETE:
					for(Iterator<Map<String,Object>> it = features.iterator(); it.hasNext();) {
						Map<String,Object> featuresMap = it.next();
						if(featuresMap.get("name").equals(feature)) {
							it.remove();
							new File("data/git/" + repoID + "/" + feature).delete();
						}
					}
					saveFeaturesFile(new File("data/git/" + repoID), features);
					break;
				case PUT:
					Map<String,Object> body = request.getBodyAsMap();
					boolean found = false;
					for(Map<String,Object> featureMap : features) {
						if(featureMap.get("name").equals(body.get("name"))) {
							found = true;
							featureMap.clear();
							featureMap.putAll(body);
						}
					}
					if(!found) {
						features.add(body);
					}
					saveFeaturesFile(new File("data/git/" + repoID), features);
					break;
				default:	
					break;
				}
				for(Map<String,Object> featureMap : features) {
					if(featureMap.get("name").equals(feature)) {
						request.getResponse().sendJsonMap(featureMap);
						break;
					}
				}
			} catch (IOException e) {
				request.getResponse().setStatusCode(500);
				request.getResponse().end(e.getMessage());
			}
		}
	}

	/**
	 * Process the feature version
	 * @param request
	 * @param repoID
	 * @param featurename
	 * @param version
	 */
	protected void process(Request request, String repoID, String featurename, String version) {
		try {
			switch(request.getMethod()) {
			case DELETE:
				request.getResponse().sendJsonMap(deleteFeatureVersion(repoID, featurename, version));
				break;
			case PUT:
				saveFeatureVersionFile(getRepositoryFolder(repoID, featurename, version), request.getBodyAsMap());
				updateFeatureVersionContents(request.getVirtualHost(), repoID, featurename, version);
				request.getResponse().sendJsonMap(readRepositoryFeatureVersion(repoID, featurename, version));
				break;
			case POST:
				updateFeatureVersionContents(request.getVirtualHost(), repoID, featurename, version);
				request.getResponse().sendJsonMap(readRepositoryFeatureVersion(repoID, featurename, version));
				break;
			default:
				request.getResponse().sendJsonMap(readRepositoryFeatureVersion(repoID, featurename, version));
				break;
			}
		} catch (IOException e) {
			request.getResponse().setStatusCode(500);
			request.getResponse().end(e.getMessage());
		}
	}

	/**
	 * Update the feature version contents (updates content that is referenced in a feature)
	 * TODO cleanup code
	 * @param vhost
	 * @param repoID
	 * @param featurename
	 * @param version
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	private void updateFeatureVersionContents(VirtualHost vhost, String repoID, String featurename, String version) throws IOException {
		File repoFolder = getRepositoryFolder(repoID, featurename, version);
		Map<String,Object> contents = readRepositoryFeatureVersion(repoID, featurename, version);
		for(Map.Entry<String, Object> item : contents.entrySet()) {
			FeaturesProcessor processor = features.getFeatureProcessors().get(item.getKey());
			if(processor != null) {
				List<Map<String,Object>> list = (List<Map<String, Object>>) item.getValue();
				for(Iterator<Map<String,Object>> it = list.iterator(); it.hasNext();) {
					Map<String,Object> featureMap = it.next();
					boolean exists = processor.updateFeatureContent(vhost, repoFolder, featureMap);
					if(!exists) {
						it.remove();
					};
				}
			}
		}
		saveFeatureVersionFile(repoFolder, contents);
	}
	
	/**
	 * Return the repository folder
	 * @param repoID
	 * @param featurename
	 * @param version
	 * @return
	 */
	private File getRepositoryFolder(String repoID, String featurename, String version) {
		File repoFolder = new File("data/git/" + repoID + "/" + featurename + "/" + version);
		if(!repoFolder.isDirectory()) {
			repoFolder.mkdirs();
		}
		return repoFolder;
	}
	
	/**
	 * Persist the repository index file (features.json)
	 * @param repoFolder
	 * @param features
	 * @throws IOException 
	 */
	private void saveFeaturesFile(File repoFolder, List<Map<String, Object>> features) throws IOException {
		File featuresFile = new File(repoFolder, "features.json");
		jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
		jsonMapper.writeValue(featuresFile, features);
	}

	/**
	 * Persist the feature version file (feature.json)
	 * @param versionFolder
	 * @param contents
	 * @throws IOException
	 */
	private void saveFeatureVersionFile(File versionFolder, Map<String, Object> contents) throws IOException {
		File feature = new File(versionFolder, "feature.json");
		jsonMapper.enable(SerializationFeature.INDENT_OUTPUT);
		jsonMapper.writeValue(feature, contents);
	}
	
	/**
	 * Delete the feature version
	 * @param repoID
	 * @param featurename
	 * @param version
	 * @return the updated feature
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	private Map<String,Object> deleteFeatureVersion(String repoID, String featurename, String version) throws IOException {
		getRepositoryFolder(repoID, featurename, version).delete();
		List<Map<String,Object>> features = readRepositoryFeatures(repoID);
		for(Map<String,Object> feature : features) {
			if(feature.get("name").equals(featurename)) {
				((List<String>) feature.get("versions")).remove(version);
				saveFeaturesFile(new File("data/git/" + repoID), features);
				return feature;
			}
		}
		return null;
	}

	/**
	 * Read the feature version file (feature.json) 
	 * @param repoID
	 * @param featurename
	 * @param version
	 * @return
	 * @throws IOException
	 */
	private Map<String, Object> readRepositoryFeatureVersion(String repoID, String featurename, String version) throws IOException {
		File file = new File("data/git/" + repoID + "/" + featurename + "/" + version + "/feature.json");
		if(!file.exists()) {
			file.getParentFile().mkdirs();
			jsonMapper.writeValue(file, new HashMap<String,Object>());
		}
		return jsonMapper.readValue(file, new TypeReference<Map<String,Object>>(){});
	}

	/**
	 * Read the repository index file (features.json)
	 * @param repoID
	 * @return
	 * @throws IOException
	 */
	private List<Map<String, Object>> readRepositoryFeatures(String repoID) throws IOException {
		File repo = new File("data/git/" + repoID + "/features.json");
		if(!repo.exists()) {
			FileOutputStream fop = new FileOutputStream(repo);
			OutputStreamWriter writer = new OutputStreamWriter(fop);
			writer.write("[]");
			writer.close();
			fop.close();
		}
		return jsonMapper.readValue(repo, new TypeReference<List<Map<String,Object>>>(){});
	}

	/**
	 * Update a repository (creates one if it doesn't exist)
	 * @param vhost
	 * @param repoID
	 */
	private void pullRepository(VirtualHost vhost, String repoID) {
		GitRepository repo = git.registerRepository(repoID);
		if(!repo.exists()) {
			Map<String,Object> repoConf = config.readConfig(vhost, repoID);
			repo.setLocalPath((String) repoConf.get("_id"));
			repo.setOrigin((String) repoConf.get("path"));
			repo.setUsername((String) repoConf.get("user"));
			repo.setPassword((String) repoConf.get("password"));
			git.init(repo);
		} else {
			git.pull(repo);
		}
	}

	/**
	 * Push tot the repository
	 * @param virtualHost
	 * @param repoID
	 */
	private void pushRepository(VirtualHost virtualHost, String repoID) {
		GitRepository repo = git.registerRepository(repoID);
		git.push(repo);
	}

}
