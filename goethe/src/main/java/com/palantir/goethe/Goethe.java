/*
 * (c) Copyright 2021 Palantir Technologies Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.palantir.goethe;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

/**
 * {@link Goethe} utility class provides clean formatting for generated sources.
 * <pre>{@code
 * // General purpose code-generation
 * String formatted = Goethe.formatAsString(poetFile);
 * // Annotation processing
 * Goethe.formatAndEmit(poetFile, annotationProcessorFiler);
 * }</pre>
 */
public final class Goethe {

    private static final FormatterFacade JAVA_FORMATTER = FormatterFacadeFactory.create();

    /**
     * Format a {@link com.palantir.javapoet.JavaFile javapoet java file} into a {@link String}.
     *
     * @param file Javapoet file to format
     * @return Formatted source code
     */
    public static String formatAsString(com.palantir.javapoet.JavaFile file) {
        StringBuilder rawSource = new StringBuilder();
        try {
            file.writeTo(rawSource);
            return JAVA_FORMATTER.formatSource(
                    file.packageName() + '.' + file.typeSpec().name(), rawSource.toString());
        } catch (IOException e) {
            throw new GoetheException("Formatting failed", e);
        }
    }

    /**
     * Format a {@link com.squareup.javapoet.JavaFile javapoet java file} into a {@link String}.
     *
     * @param file Javapoet file to format
     * @return Formatted source code
     */
    public static String formatAsString(com.squareup.javapoet.JavaFile file) {
        StringBuilder rawSource = new StringBuilder();
        try {
            file.writeTo(rawSource);
            return JAVA_FORMATTER.formatSource(file.packageName + '.' + file.typeSpec.name, rawSource.toString());
        } catch (IOException e) {
            throw new GoetheException("Formatting failed", e);
        }
    }

    /**
     * Format a {@link com.palantir.javapoet.JavaFile javapoet java file} and write the result to an {@link Filer annotation processing
     * filer}.
     *
     * @param file Javapoet file to format
     * @param filer Destination for the formatted file
     */
    public static void formatAndEmit(com.palantir.javapoet.JavaFile file, Filer filer) {
        String formatted = formatAsString(file);

        JavaFileObject filerSourceFile = null;
        try {
            String className = file.packageName().isEmpty()
                    ? file.typeSpec().name()
                    : file.packageName() + "." + file.typeSpec().name();
            filerSourceFile = filer.createSourceFile(
                    className, file.typeSpec().originatingElements().toArray(new Element[0]));
            try (Writer writer = filerSourceFile.openWriter()) {
                writer.write(formatted);
            }
        } catch (IOException e) {
            if (filerSourceFile != null) {
                try {
                    filerSourceFile.delete();
                } catch (Exception deletionFailure) {
                    e.addSuppressed(deletionFailure);
                }
            }
            throw new GoetheException("Failed to write formatted code to the filer", e);
        }
    }

    /**
     * Format a {@link com.squareup.javapoet.JavaFile javapoet java file} and write the result to an {@link Filer annotation processing
     * filer}.
     *
     * @param file Javapoet file to format
     * @param filer Destination for the formatted file
     */
    public static void formatAndEmit(com.squareup.javapoet.JavaFile file, Filer filer) {
        String formatted = formatAsString(file);

        JavaFileObject filerSourceFile = null;
        try {
            String className =
                    file.packageName.isEmpty() ? file.typeSpec.name : file.packageName + "." + file.typeSpec.name;
            filerSourceFile =
                    filer.createSourceFile(className, file.typeSpec.originatingElements.toArray(new Element[0]));
            try (Writer writer = filerSourceFile.openWriter()) {
                writer.write(formatted);
            }
        } catch (IOException e) {
            if (filerSourceFile != null) {
                try {
                    filerSourceFile.delete();
                } catch (Exception deletionFailure) {
                    e.addSuppressed(deletionFailure);
                }
            }
            throw new GoetheException("Failed to write formatted code to the filer", e);
        }
    }

    /**
     * Formats the given Java file and emits it to the appropriate directory under {@code baseDir}.
     *
     * @param file Javapoet file to format
     * @param baseDir Source set root where the formatted file will be written
     * @return the new file location
     */
    public static Path formatAndEmit(com.palantir.javapoet.JavaFile file, Path baseDir) {
        String formatted = formatAsString(file);
        try {
            Path output =
                    getFilePath(baseDir, file.packageName(), file.typeSpec().name());
            Files.writeString(output, formatted);
            return output;
        } catch (IOException e) {
            throw new GoetheException("Failed to write formatted sources", e);
        }
    }

    /**
     * Formats the given Java file and emits it to the appropriate directory under {@code baseDir}.
     *
     * @param file Javapoet file to format
     * @param baseDir Source set root where the formatted file will be written
     * @return the new file location
     */
    public static Path formatAndEmit(com.squareup.javapoet.JavaFile file, Path baseDir) {
        String formatted = formatAsString(file);
        try {
            Path output = getFilePath(baseDir, file.packageName, file.typeSpec.name);
            Files.writeString(output, formatted);
            return output;
        } catch (IOException e) {
            throw new GoetheException("Failed to write formatted sources", e);
        }
    }

    /**
     * Returns the full path for the given Java file and Java base dir. In a nutshell, turns packages into directories,
     * e.g., {@code com.foo.bar.MyClass -> /<baseDir>/com/foo/bar/MyClass.java} and creates all directories.
     */
    private static Path getFilePath(Path baseDir, String packageName, String typeName) throws IOException {
        Preconditions.checkArgument(
                Files.notExists(baseDir) || Files.isDirectory(baseDir),
                "path %s exists but is not a directory.",
                baseDir);
        Path outputDirectory = baseDir;
        if (!packageName.isEmpty()) {
            for (String packageComponent : Splitter.on(".").split(packageName)) {
                outputDirectory = outputDirectory.resolve(packageComponent);
            }
            Files.createDirectories(outputDirectory);
        }

        return outputDirectory.resolve(typeName + ".java");
    }

    private Goethe() {}
}
