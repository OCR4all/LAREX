package de.uniwue.zpd.dachs.larex.backend.service.image;

import java.io.IOException;

public class ImagePresentationException extends IOException {
    private final int status;
    public ImagePresentationException(int status, String message) { super(message); this.status = status; }
    public int status() { return status; }
}
