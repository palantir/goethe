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

final class FormatterFacadeFactory {
    private FormatterFacadeFactory() {}

    static FormatterFacade create() {
        if (Runtime.version().feature() < 16 || currentJvmHasExportArgs()) {
            return new DirectFormatterFacade();
        }
        return new BootstrappingFormatterFacade();
    }

    private static boolean currentJvmHasExportArgs() {
        List<String> arguments =
                List.copyOf(ManagementFactory.getRuntimeMXBean().getInputArguments());
        return BootstrappingFormatterFacade.REQUIRED_EXPORTS.stream()
                .allMatch(required -> hasExport(arguments, required));
    }

    @VisibleForTesting
    static boolean hasExport(List<String> arguments, String moduleAndPackage) {
        String singleArgAddExport = "--add-exports=" + moduleAndPackage + "=ALL-UNNAMED";
        String multiArgAddExport = moduleAndPackage + "=ALL-UNNAMED";
        for (int i = 0; i < arguments.size(); i++) {
            String argument = arguments.get(i);
            if (singleArgAddExport.equals(argument)) {
                return true;
            }
            if (multiArgAddExport.equals(argument)) {
                if (i > 0 && "--add-exports".equals(arguments.get(i - 1))) {
                    return true;
                }
            }
        }
        return false;
    }
}
