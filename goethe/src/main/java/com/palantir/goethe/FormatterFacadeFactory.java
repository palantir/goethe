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

import com.google.common.annotations.VisibleForTesting;
import java.lang.management.ManagementFactory;
import java.util.List;

@SuppressWarnings("checkstyle:BanSystemErr") // this repo doesn't use safe-logging
final class FormatterFacadeFactory {
    private FormatterFacadeFactory() {}

    static FormatterFacade create() {
        if (currentJvmHasExportArgs()) {
            return new DirectFormatterFacade();
        }
        System.err.println("[goethe] The current JVM does not have the right JVM arguments for direct formatting. "
                + "Falling back to equivalent but slower bootstrapping formatter. Required exports: "
                + BootstrappingFormatterFacade.REQUIRED_EXPORTS);
        return new BootstrappingFormatterFacade();
    }

    private static boolean currentJvmHasExportArgs() {
        return true;
    }
}
