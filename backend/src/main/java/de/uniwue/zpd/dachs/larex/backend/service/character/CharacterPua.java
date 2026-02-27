package de.uniwue.zpd.dachs.larex.backend.service.character;

final class CharacterPua {

    private CharacterPua() {
    }

    static boolean isPua(int codePoint) {
        // Unicode Private Use Areas:
        // - BMP:     U+E000..U+F8FF
        // - Plane 15: U+F0000..U+FFFFD
        // - Plane 16: U+100000..U+10FFFD
        return (codePoint >= 0xE000 && codePoint <= 0xF8FF)
                || (codePoint >= 0xF0000 && codePoint <= 0xFFFFD)
                || (codePoint >= 0x100000 && codePoint <= 0x10FFFD);
    }
}
