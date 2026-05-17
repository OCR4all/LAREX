package de.uniwue.zpd.dachs.larex.backend.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "larex.versioning")
public class VersioningProperties {

    @Min(1)
    private int maxVersionsPerXml = 25;

    public int getMaxVersionsPerXml() {
        return maxVersionsPerXml;
    }

    public void setMaxVersionsPerXml(int maxVersionsPerXml) {
        this.maxVersionsPerXml = maxVersionsPerXml;
    }
}
