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

import com.google.common.base.Strings;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.TypeSpec;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Path;
import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

class GoetheTest {

    @TempDir
    Path tempDir;

    @Test
    public void testDiagnosticException() {
        JavaFile javaFile = JavaFile.builder(
                        "com.palantir.foo",
                        TypeSpec.classBuilder("Foo")
                                .addStaticBlock(CodeBlock.builder()
                                        .addStatement("type oops name = bar")
                                        .build())
                                .build())
                .build();
        assertThatThrownBy(() -> Goethe.formatAsString(javaFile))
                .isInstanceOf(GoetheException.class)
                .hasMessageContaining("Failed to format 'com.palantir.foo.Foo'")
                .hasMessageContaining("';' expected")
                .hasMessageContaining(
                        "" // newline to align the output
                                + "    type oops name = bar;\n"
                                + "            ^");
    }

    @Test
    public void testFormatting() {
        String padding = Strings.repeat("a", 90);
        JavaFile javaFile = JavaFile.builder(
                        "com.palantir.foo",
                        TypeSpec.classBuilder("Foo")
                                .addStaticBlock(CodeBlock.builder()
                                        .addStatement("$T.out.println($S)", System.class, padding)
                                        .build())
                                .build())
                .build();
        String raw = javaFile.toString();
        String formatted = Goethe.formatAsString(javaFile);
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
                        + "                \"" + padding + "\");\n"
                        + "    }\n"
                        + "}\n");
    }

    @Test
    public void testFormattingToFiler() throws IOException {
        String padding = Strings.repeat("a", 90);
        Element originatingElement = Mockito.mock(Element.class);
        JavaFile javaFile = JavaFile.builder(
                        "com.palantir.foo",
                        TypeSpec.classBuilder("Foo")
                                .addStaticBlock(CodeBlock.builder()
                                        .addStatement("$T.out.println($S)", System.class, padding)
                                        .build())
                                .addOriginatingElement(originatingElement)
                                .build())
                .build();
        StringWriter writer = new StringWriter();
        JavaFileObject javaFileObject = Mockito.mock(JavaFileObject.class);
        when(javaFileObject.openWriter()).thenReturn(writer);
        Filer filer = Mockito.mock(Filer.class);
        // If the originating elements aren't passed through to the Filer, this test will fail with:
        // 'Cannot invoke "javax.tools.JavaFileObject.openWriter()" because "filerSourceFile" is null'
        when(filer.createSourceFile(eq("com.palantir.foo.Foo"), eq(originatingElement)))
                .thenReturn(javaFileObject);
        Goethe.formatAndEmit(javaFile, filer);
        assertThat(writer.toString())
                .as("Expected the formatted output to differ from original")
                .isNotEqualTo(javaFile.toString())
                .as("Expected identical output to 'formatAsString'")
                .isEqualTo(Goethe.formatAsString(javaFile));
    }

    @Test
    public void testFormattingToDirectory() {
        JavaFile javaFile = JavaFile.builder(
                        "com.palantir.foo",
                        TypeSpec.classBuilder("Foo")
                                .addStaticBlock(CodeBlock.builder()
                                        .addStatement("$T.out.println($S)", System.class, Strings.repeat("a", 90))
                                        .build())
                                .build())
                .build();
        Path location = Goethe.formatAndEmit(javaFile, tempDir);
        assertThat(location.toString()).endsWith("com/palantir/foo/Foo.java");
        assertThat(location)
                .as("Expected contents on disk to be formatted")
                .hasContent(Goethe.formatAsString(javaFile));
    }
}
