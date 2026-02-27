package de.uniwue.zpd.dachs.larex.backend.dto;

public class FileExportDto {

    public record Metadata(
            String fileName,
            String contentType,
            boolean attachment
    ) {
    }
}
