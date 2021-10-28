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

import java.lang.management.ManagementFactory;

final class FormatterFacadeFactory {
    private FormatterFacadeFactory() {}

    static FormatterFacade create() {
        if (Runtime.version().feature() < 16 || currentJvmHasExportArgs()) {
            return new DirectFormatterFacade();
        }
        return new BootstrappingFormatterFacade();
    }

    private static boolean currentJvmHasExportArgs() {
        return ManagementFactory.getRuntimeMXBean()
                .getInputArguments()
                .containsAll(BootstrappingFormatterFacade.EXPORTS);
    }
}
