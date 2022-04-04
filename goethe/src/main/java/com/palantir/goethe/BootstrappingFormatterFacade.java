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

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Semaphore;

/** A {@link FormatterFacade} implementation which spawns new java processes with {@link #EXPORTS} applied. */
final class BootstrappingFormatterFacade implements FormatterFacade {

    private static final int MAX_PROCESSES = Runtime.getRuntime().availableProcessors() * 2;
    private static final Semaphore MAX_PROCESS_SEMAPHORE = new Semaphore(MAX_PROCESSES);

    static final ImmutableList<String> REQUIRED_EXPORTS = ImmutableList.of(
            "jdk.compiler/com.sun.tools.javac.api",
            "jdk.compiler/com.sun.tools.javac.file",
            "jdk.compiler/com.sun.tools.javac.parser",
            "jdk.compiler/com.sun.tools.javac.tree",
            "jdk.compiler/com.sun.tools.javac.util");

    static final ImmutableList<String> EXPORTS = REQUIRED_EXPORTS.stream()
            .map(value -> String.format("--add-exports=%s=ALL-UNNAMED", value))
            .collect(ImmutableList.toImmutableList());

    @Override
    public String formatSource(String className, String unformattedSource) throws GoetheException {
        try {
            MAX_PROCESS_SEMAPHORE.acquire();
        } catch (InterruptedException e) {
            throw new GoetheException("Failed to acquire process semaphore", e);
        }

        try {
            Process process = new ProcessBuilder(ImmutableList.<String>builder()
                            .add(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath())
                            .addAll(EXPORTS)
                            .add( // Classpath
                                    "-cp",
                                    getClasspath(),
                                    // Main class
                                    GoetheMain.class.getName(),
                                    // Args
                                    className)
                            .build())
                    .start();
            try (OutputStream outputStream = process.getOutputStream()) {
                outputStream.write(unformattedSource.getBytes(StandardCharsets.UTF_8));
            }
            byte[] data;
            try (InputStream inputStream = process.getInputStream()) {
                data = ByteStreams.toByteArray(inputStream);
            }
            int exitStatus = process.waitFor();
            if (exitStatus != 0) {
                throw new GoetheException(String.format(
                        "Formatter exited non-zero (%d) formatting class %s:\n%s",
                        exitStatus, className, getErrorOutput(process)));
            }
            return new String(data, StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            throw new GoetheException("Failed to bootstrap jdk", e);
        } finally {
            MAX_PROCESS_SEMAPHORE.release();
        }
    }

    private static String getErrorOutput(Process process) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (InputStream inputStream = process.getErrorStream()) {
            ByteStreams.copy(inputStream, baos);
        } catch (IOException | RuntimeException e) {
            String diagnostic = "<failed to read process stream: " + e + ">";
            try {
                baos.write(diagnostic.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // should not happen
            }
        }
        return baos.toString(StandardCharsets.UTF_8);
    }

    private static String getClasspath() {
        return getPath(Goethe.class);
    }

    private static String getPath(Class<?> clazz) {
        try {
            return new File(clazz.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI())
                    .getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new GoetheException("Failed to locate the jar providing " + clazz, e);
        }
    }
}
