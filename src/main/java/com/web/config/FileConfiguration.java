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

@Component
@Scope("session")
public class FileConfiguration {

	private Map<String, String> configurations;

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
				String[] lineInput = line.split("#",2);
				if (lineInput.length > 0) {
					line = lineInput[0];
				}
				if(!line.equals("")) {
					// Split variable and content
					String[] lineContent = line.split(":",2);
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
	}

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

	public void setSetting(String setting, String value) {
		if (!isInitiated()) {
			System.err.println("Configuration file has not been read.");
			this.configurations = new HashMap<String, String>();
		}
		configurations.put(setting, value);
	}
	
	public Map<String, String> getConfigurationMap() {
		if (isInitiated()) {
			return new HashMap<String, String>(configurations);
		} else {
			System.err.println("Configuration file has not been read.");
			return new HashMap<String, String>();
		}
	}
	
	public boolean isInitiated() {
		return configurations != null;
	}
}
