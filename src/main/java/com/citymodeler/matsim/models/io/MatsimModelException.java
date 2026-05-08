package com.citymodeler.matsim.models.io;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MatsimModelException extends RuntimeException {
    private final Path filePath;
    private final int lineNumber;
    private final String elementName;

    public MatsimModelException(String message) {
        this(message, null, null, -1, null);
    }

    public MatsimModelException(String message, Throwable cause) {
        this(message, cause, null, -1, null);
    }

    public MatsimModelException(String message, Path filePath, int lineNumber, String elementName) {
        this(message, null, filePath, lineNumber, elementName);
    }

    public MatsimModelException(String message, Throwable cause, Path filePath, int lineNumber, String elementName) {
        super(formatMessage(message, filePath, lineNumber, elementName), cause);
        this.filePath = filePath;
        this.lineNumber = lineNumber;
        this.elementName = elementName;
    }

    public Path getFilePath() {
        return filePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public String getElementName() {
        return elementName;
    }

    private static String formatMessage(String message, Path filePath, int lineNumber, String elementName) {
        List<String> context = new ArrayList<>();
        if (filePath != null) {
            context.add("file=" + filePath);
        }
        if (lineNumber > 0) {
            context.add("line=" + lineNumber);
        }
        if (elementName != null && !elementName.isBlank()) {
            context.add("element=" + elementName);
        }
        if (context.isEmpty()) {
            return message;
        }
        return message + " (" + String.join(", ", context) + ")";
    }
}
