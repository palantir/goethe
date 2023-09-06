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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class GoetheBootstrapTest {

    @TempDir
    Path tempDir;

    static Stream<FormatterFacade> formatterFacades() {
        return Stream.of(new DirectFormatterFacade(), new BootstrappingFormatterFacade());
    }

    private static String format(FormatterFacade facade, JavaFile javaFile) {
        return facade.formatSource(javaFile.packageName + '.' + javaFile.typeSpec.name, javaFile.toString());
    }

    @ParameterizedTest
    @MethodSource("formatterFacades")
    public void testDiagnosticException(FormatterFacade formatter) {
        JavaFile javaFile = JavaFile.builder(
                        "com.palantir.foo",
                        TypeSpec.classBuilder("Foo")
                                .addStaticBlock(CodeBlock.builder()
                                        .addStatement("type oops name = bar")
                                        .build())
                                .build())
                .build();

        assertThatThrownBy(() -> format(formatter, javaFile))
                .isInstanceOf(GoetheException.class)
                .hasMessageContaining("Failed to format 'com.palantir.foo.Foo'")
                .hasMessageContaining("';' expected")
                .hasMessageContaining(
                        "" // newline to align the output
                                + "    type oops name = bar;\n"
                                + "            ^");
    }

    @ParameterizedTest
    @MethodSource("formatterFacades")
    public void testFormatting(FormatterFacade formatter) {
        String longWord = "a".repeat(90);
        JavaFile javaFile = JavaFile.builder(
                        "com.palantir.foo",
                        TypeSpec.classBuilder("Foo")
                                .addStaticBlock(CodeBlock.builder()
                                        .addStatement("$T.out.println($S)", System.class, longWord)
                                        .build())
                                .build())
                .build();
        String raw = javaFile.toString();
        String formatted = format(formatter, javaFile);
        assertThat(formatted)
                .as("Expected the formatted output to differ from original")
                .isNotEqualTo(raw)
                .as("Formatting does not match the expected output, the expectation may need to be updated")
                .isEqualTo("package com.palantir.foo;\n\n"
                        + "import java.lang.System;\n"
                        + "\n"
                        + "class Foo {\n"
                        + "    static {\n"
                        + "        System.out.println(\n"
                        + "                \"" + longWord + "\");\n"
                        + "    }\n"
                        + "}\n");
    }

    @ParameterizedTest
    @MethodSource("formatterFacades")
    public void testFormatJavadoc(FormatterFacade formatter) {
        String longWord = "a".repeat(90);
        JavaFile javaFile = JavaFile.builder(
                        "com.palantir.foo",
                        TypeSpec.classBuilder("Foo")
                                .addJavadoc("$1L $1L", longWord)
                                .build())
                .build();

        assertThat(format(formatter, javaFile))
                .as("Formatting does not match the expected output, the expectation may need to be updated")
                .isEqualTo("package com.palantir.foo;\n\n"
                        + "/**\n"
                        + " * " + longWord + "\n"
                        + " * " + longWord + "\n"
                        + " */\n"
                        + "class Foo {}\n");
    }
}
