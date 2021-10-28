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

import com.google.common.io.ByteStreams;
import com.palantir.javaformat.java.Main;
import com.squareup.javapoet.JavaFile;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

final class BootstrappingFormatterFacade implements FormatterFacade {

    @Override
    public String formatSource(JavaFile file) throws GoetheException {
        try {
            Process process = new ProcessBuilder(
                            new File(System.getProperty("java.home"), "bin/java").getAbsolutePath(),
                            // Exports
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
                            "--add-exports",
                            "jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
                            // Classpath
                            "-cp",
                            System.getProperty("java.class.path"),
                            // Main class
                            Main.class.getName(),
                            // Args
                            "--palantir",
                            "--skip-reflowing-long-strings",
                            "-")
                    .start();
            StringBuilder rawSource = new StringBuilder();
            file.writeTo(rawSource);
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(rawSource.toString().getBytes(StandardCharsets.UTF_8));
            }
            byte[] data;
            try (InputStream inputStream = process.getInputStream()) {
                data = ByteStreams.toByteArray(inputStream);
            }
            int exitStatus = process.waitFor();
            if (exitStatus != 0) {
                throw new GoetheException("Bootstrapped formatter exited non-zero: " + exitStatus + " with output:\n"
                        + getErrorOutput(process));
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            throw new GoetheException("Failed to bootstrap jdk", e);
        }
    }

    private static String getErrorOutput(Process process) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream inputStream = process.getErrorStream()) {
            ByteStreams.copy(inputStream, baos);
        } catch (IOException | RuntimeException ignored) {
            // continue
        }
        return baos.toString(StandardCharsets.UTF_8);
    }
}
