package de.uniwue.web.config;

import java.io.*;
import java.util.*;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Handler to load and provide settings written in the LAREX configuration file.
 * The larex.properties file is either at "src/main/webapp/WEB-INF/larex.properties" or if
 * the "LAREX_CONFIG" system variable is defined under "$LAREX_CONFIG".
 */
@Component
@Scope("session")
public class LarexConfiguration {

	private Map<String, String> configurations;

	/**
	 * Read the configuration file into the session
	 *
	 * @param configuration File direction pointing to the configuration file
	 */
	@SuppressWarnings("resource")
	public void read(File configuration) {
		configurations = new HashMap<>();
		try {
			FileReader input = new FileReader(configuration);
			Properties prop = new Properties();
			prop.load(input);
			prop.forEach((key, value) -> configurations.put(key.toString(), value.toString()));
		}catch (IOException ex) {
			ex.printStackTrace();
		}

		// defaults
		if(getSetting("modes") == null || getSetting("modes").equals("")) {
			setSetting("modes", "segment lines text");
		}
	}

	/**
	 * Get a setting defined in the configuration file. Throw error if non has been
	 * read yet.
	 *
	 * @param setting Name of the setting to return
	 * @return
	 */
	public String getSetting(String setting) {
		if (!isInitiated()) {
			System.err.println("Configuration file has not been read.");
			return "";
		}
		return configurations.getOrDefault(setting, "");
	}

	/**
	 * Get a list setting defined in the configuration file. Throw error if non has
	 * been read yet.
	 *
	 * @param setting Name of the setting to return
	 * @return
	 */
	public List<String> getListSetting(String setting) {
		if (!isInitiated()) {
			System.err.println("Configuration file has not been read.");
			return new ArrayList<>();
		}
		if (configurations.containsKey(setting)) {
			return Arrays.asList(configurations.get(setting).split(" "));
		} else {
			return new ArrayList<>();
		}
	}

	/**
	 * Set a value of a setting for this session. Throw error if no settings file
	 * has been read yet.
	 *
	 * @param setting Name of the setting to set
	 * @param value   Value of the setting to set
	 */
	public void setSetting(String setting, String value) {
		if (!isInitiated()) {
			System.err.println("Configuration file has not been read.");
			this.configurations = new HashMap<String, String>();
		}
		configurations.put(setting, value);
	}

	/**
	 * Check if a settings file has been read for this session yet.
	 *
	 * @return
	 */
	public boolean isInitiated() {
		return configurations != null;
	}
}
