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
import com.google.common.base.Splitter;
import com.palantir.javaformat.java.Formatter;
import com.palantir.javaformat.java.FormatterDiagnostic;
import com.palantir.javaformat.java.FormatterException;
import com.palantir.javaformat.java.JavaFormatterOptions;
import java.util.List;

final class DirectFormatterFacade implements FormatterFacade {

    private final Formatter formatter = Formatter.createFormatter(JavaFormatterOptions.builder()
            .style(JavaFormatterOptions.Style.PALANTIR)
            .build());

    @Override
    public String formatSource(String className, String unformattedSource) throws GoetheException {
        try {
            return formatter.formatSource(unformattedSource);
        } catch (FormatterException e) {
            throw new GoetheException(generateMessage(className, unformattedSource, e.diagnostics()), e);
        }
    }

    /**
     * Attempt to provide as much actionable information as possible to understand why formatting is failing. This
     * is common when generated code is incorrect and cannot compile, so we mustn't make it difficult to understand
     * the problem.
     */
    private static String generateMessage(
            String className, String unformattedSource, List<FormatterDiagnostic> formatterDiagnostics) {
        try {
            List<String> lines = Splitter.on('\n').splitToList(unformattedSource);
            StringBuilder failureText = new StringBuilder();
            failureText.append("Failed to format '").append(className).append("'\n");
            for (FormatterDiagnostic formatterDiagnostic : formatterDiagnostics) {
                failureText
                        .append(formatterDiagnostic.message())
                        .append("\n")
                        // Diagnostic values are one-indexed, while our list is zero-indexed.
                        .append(lines.get(formatterDiagnostic.line() - 1))
                        .append('\n')
                        // Offset by two to convert from one-indexed to zero indexed values, and account for the caret.
                        .append(" ".repeat(Math.max(0, formatterDiagnostic.column() - 2)))
                        .append("^\n\n");
            }
            return CharMatcher.is('\n').trimFrom(failureText.toString());
        } catch (RuntimeException e) {
            return "Failed to format:\n" + unformattedSource;
        }
    }
}
