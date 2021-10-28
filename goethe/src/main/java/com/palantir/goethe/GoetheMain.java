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
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Main class used internally to bootstrap the formatter with additional jvm args for compiler class access. */
@SuppressWarnings({"checkstyle:BanSystemErr", "checkstyle:BanSystemOut"})
final class GoetheMain {

    private GoetheMain() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Class name argument is required");
            System.exit(1);
        }
        String className = args[0];
        String input = new String(ByteStreams.toByteArray(System.in), StandardCharsets.UTF_8);
        try {
            System.out.print(new DirectFormatterFacade().formatSource(className, input));
        } catch (GoetheException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
