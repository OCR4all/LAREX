package com.web.config;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Handler to load and provide settings written in the LAREX configuration file.
 * The larex.config file is either at "src/main/webapp/WEB-INF/larex.config" or if 
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
		BufferedReader in;
		configurations = new HashMap<String, String>();
		try {
			in = new BufferedReader(new FileReader(configuration));

			String line;
			int lineNumber = 0;
			while ((line = in.readLine()) != null) {
				lineNumber++;
				// Filter out comments
				String[] lineInput = line.split("#", 2);
				if (lineInput.length > 0) {
					line = lineInput[0];
				}
				if (!line.equals("")) {
					// Split variable and content
					String[] lineContent = line.split(":", 2);
					if (lineContent.length == 2) {
						configurations.put(lineContent[0], lineContent[1]);
					} else {
						throw new IOException("Could not read configurationfile. Error in line " + lineNumber);
					}
				}
			}
			in.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
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
		if (configurations.containsKey(setting)) {
			return configurations.get(setting);
		} else {
			return "";
		}
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
