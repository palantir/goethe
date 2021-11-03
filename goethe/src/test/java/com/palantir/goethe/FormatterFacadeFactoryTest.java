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

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.Test;

class FormatterFacadeFactoryTest {

    @Test
    public void testHasExport_noArgs() {
        assertThat(FormatterFacadeFactory.hasExport(ImmutableList.of(), "jdk.compiler/com.sun.tools.javac.file"))
                .isFalse();
    }

    @Test
    public void testHasExport_otherArgs() {
        assertThat(FormatterFacadeFactory.hasExport(
                        ImmutableList.of("a", "b"), "jdk.compiler/com.sun.tools.javac.file"))
                .isFalse();
    }

    @Test
    public void testHasExport_singleExportArg() {
        assertThat(FormatterFacadeFactory.hasExport(
                        ImmutableList.of("--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"),
                        "jdk.compiler/com.sun.tools.javac.file"))
                .isTrue();
    }

    @Test
    public void testHasExport_multiExportArgs() {
        assertThat(FormatterFacadeFactory.hasExport(
                        ImmutableList.of("--add-exports", "jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED"),
                        "jdk.compiler/com.sun.tools.javac.file"))
                .isTrue();
    }

    @Test
    public void testHasExport_multiExportArgs_wrongOrder() {
        assertThat(FormatterFacadeFactory.hasExport(
                        ImmutableList.of("jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED", "--add-exports"),
                        "jdk.compiler/com.sun.tools.javac.file"))
                .isFalse();
    }
}
