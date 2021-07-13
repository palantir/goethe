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

import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.palantir.javaformat.java.Formatter;
import com.palantir.javaformat.java.FormatterDiagnostic;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import com.squareup.javapoet.JavaFile;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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

    private static final Formatter JAVA_FORMATTER = Formatter.createFormatter(JavaFormatterOptions.builder()
            .style(JavaFormatterOptions.Style.PALANTIR)
            .build());

    /**
     * Format a {@link JavaFile javapoet java file} into a {@link String}.
     *
     * @param file Javapoet file to format
     * @return Formatted source code
     */
    public static String formatAsString(JavaFile file) {
        StringBuilder rawSource = new StringBuilder();
        try {
            file.writeTo(rawSource);
            return JAVA_FORMATTER.formatSource(rawSource.toString());
        } catch (FormatterException e) {
            throw new GoetheException(generateMessage(file, rawSource.toString(), e.diagnostics()), e);
        } catch (IOException e) {
            throw new GoetheException("Formatting failed", e);
        }
    }

    /**
     * Format a {@link JavaFile javapoet java file} and write the result to an {@link Filer annotation processing
     * filer}.
     *
     * @param file Javapoet file to format
     * @param filer Destination for the formatted file
     */
    public static void formatAndEmit(JavaFile file, Filer filer) {
        String formatted = formatAsString(file);

        JavaFileObject filerSourceFile = null;
        try {
            filerSourceFile =
                    filer.createSourceFile(className(file), file.typeSpec.originatingElements.toArray(new Element[0]));
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
    public static Path formatAndEmit(JavaFile file, Path baseDir) {
        String formatted = formatAsString(file);
        try {
            Path output = getFilePath(file, baseDir);
            Files.writeString(output, formatted);
            return output;
        } catch (IOException e) {
            throw new GoetheException("Failed to write formatted sources", e);
        }
    }

    /**
     * Attempt to provide as much actionable information as possible to understand why formatting is failing. This
     * is common when generated code is incorrect and cannot compile, so we mustn't make it difficult to understand
     * the problem.
     */
    private static String generateMessage(
            JavaFile file, String unformattedSource, List<FormatterDiagnostic> formatterDiagnostics) {
        try {
            List<String> lines = Splitter.on('\n').splitToList(unformattedSource);
            StringBuilder failureText = new StringBuilder();
            failureText
                    .append("Failed to format '")
                    .append(file.packageName)
                    .append('.')
                    .append(file.typeSpec.name)
                    .append("'\n");
            for (FormatterDiagnostic formatterDiagnostic : formatterDiagnostics) {
                failureText
                        .append(formatterDiagnostic.message())
                        .append("\n")
                        // Diagnostic values are one-indexed, while our list is zero-indexed.
                        .append(lines.get(formatterDiagnostic.line() - 1))
                        .append('\n')
                        // Offset by two to convert from one-indexed to zero indexed values, and account for the caret.
                        .append(Strings.repeat(" ", Math.max(0, formatterDiagnostic.column() - 2)))
                        .append("^\n\n");
            }
            return CharMatcher.is('\n').trimFrom(failureText.toString());
        } catch (RuntimeException e) {
            return "Failed to format:\n" + unformattedSource;
        }
    }

    private static String className(JavaFile file) {
        return file.packageName.isEmpty() ? file.typeSpec.name : file.packageName + "." + file.typeSpec.name;
    }

    /**
     * Returns the full path for the given Java file and Java base dir. In a nutshell, turns packages into directories,
     * e.g., {@code com.foo.bar.MyClass -> /<baseDir>/com/foo/bar/MyClass.java} and creates all directories.
     * Implementation taken from JavaPoet's {@link JavaFile#writeTo(File)}.
     */
    private static Path getFilePath(JavaFile file, Path baseDir) throws IOException {
        Preconditions.checkArgument(
                Files.notExists(baseDir) || Files.isDirectory(baseDir),
                "path %s exists but is not a directory.",
                baseDir);
        Path outputDirectory = baseDir;
        if (!file.packageName.isEmpty()) {
            for (String packageComponent : Splitter.on(".").split(file.packageName)) {
                outputDirectory = outputDirectory.resolve(packageComponent);
            }
            Files.createDirectories(outputDirectory);
        }

        return outputDirectory.resolve(file.typeSpec.name + ".java");
    }

    private Goethe() {}
}
