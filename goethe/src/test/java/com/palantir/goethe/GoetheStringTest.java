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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import javax.annotation.processing.Filer;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class GoetheStringTest {

    private static final String CLASS_NAME = "com.palantir.foo.Foo";

    @TempDir
    Path tempDir;

    @Test
    public void testDiagnosticException() {
        String source = unformattedClassWithCode("  type oops name = bar;");
        assertThatThrownBy(() -> Goethe.formatAsString(CLASS_NAME, source))
                .isInstanceOf(GoetheException.class)
                .hasMessageContaining("Failed to format 'com.palantir.foo.Foo'")
                .hasMessageContaining("';' expected")
                .hasMessageContaining(
                        "" // newline to align the output
                                + "  type oops name = bar;\n"
                                + "          ^");
    }

    @Test
    public void testFormatting() {
        String padding = "a".repeat(90);
        String source = unformattedClassWithCode("System.out.println(\"" + padding + "\");");
        String formatted = Goethe.formatAsString(CLASS_NAME, source);
        assertThat(formatted)
                .as("Expected the formatted output to differ from original")
                .isNotEqualTo(source)
                .as("Formatting does not match the expected output, the expectation may need to be updated")
                .isEqualTo("package com.palantir.foo;\n\n"
                        + "import java.lang.System;\n"
                        + "\n"
                        + "class Foo {\n"
                        + "    static {\n"
                        + "        System.out.println(\n"
                        + "                \"" + padding + "\");\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void testFormattingToFiler() throws IOException {
        String padding = "a".repeat(90);
        String source = unformattedClassWithCode("System.out.println(\"" + padding + "\");");
        StringWriter writer = new StringWriter();
        JavaFileObject javaFileObject = Mockito.mock(JavaFileObject.class);
        when(javaFileObject.openWriter()).thenReturn(writer);
        Filer filer = Mockito.mock(Filer.class);
        // If the originating elements aren't passed through to the Filer, this test will fail with:
        // 'Cannot invoke "javax.tools.JavaFileObject.openWriter()" because "filerSourceFile" is null'
        when(filer.createSourceFile(eq(CLASS_NAME))).thenReturn(javaFileObject);
        Goethe.formatAndEmit(CLASS_NAME, source, filer);
        assertThat(writer.toString())
                .as("Expected the formatted output to differ from original")
                .isNotEqualTo(source)
                .as("Expected identical output to 'formatAsString'")
                .isEqualTo(Goethe.formatAsString(CLASS_NAME, source));
    }

    @Test
    public void testFormattingToDirectory() {
        String padding = "a".repeat(90);
        String source = unformattedClassWithCode("System.out.println(\"" + padding + "\");");
        Path location = Goethe.formatAndEmit(CLASS_NAME, source, tempDir);
        assertThat(location.toString()).endsWith("com/palantir/foo/Foo.java");
        assertThat(location)
                .as("Expected contents on disk to be formatted")
                .hasContent(Goethe.formatAsString(CLASS_NAME, source));
    }

    private static String unformattedClassWithCode(String code) {
        StringBuilder builder = new StringBuilder()
                .append("package com.palantir.foo;")
                .append("import java.lang.System;")
                .append("class Foo {")
                .append("    static {")
                .append("\n")
                .append(code)
                .append("\n")
                .append("    }")
                .append("}");
        return builder.toString();
    }
}
