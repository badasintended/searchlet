/*
 * Copyright 2024 deirn
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

package lol.bai.searchlet;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.NestingKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementScanner14;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.sun.source.doctree.HiddenTree;
import com.sun.source.util.DocTreeScanner;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

public class Searchlet implements Doclet {

    private Reporter reporter;

    private @Nullable Path outputDir = null;
    private @Nullable String javadocUrl = null;
    private boolean includeMembers = false;

    private final Set<Option> options = Set.of(
        new OptionBuilder("--directory", "-d")
            .desc("""
                The output directory""")
            .args("dir")
            .build((option, arguments) -> {
                outputDir = Path.of(arguments.get(0));
            }),
        new OptionBuilder("--javadoc-url", "-j")
            .desc("""
                The url to the root of the canonical Javadoc, can be relative path from
                the root of the Searchlet or an absolute path""")
            .args("url")
            .build((option, arguments) -> {
                javadocUrl = arguments.get(0).replaceAll("/$", "");
            }),
        new OptionBuilder("--include-members")
            .desc("""
                Whet""")
            .args("bool")
            .build((option, arguments) -> {
                includeMembers = Boolean.parseBoolean(arguments.get(0));
            }),

        // Gradle shit
        // https://github.com/gradle/gradle/issues/24595
        new OptionBuilder("-notimestamp").build((option, arguments) -> {
        })
    );

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "Searchlet";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return options;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        if (outputDir == null) {
            reporter.print(Diagnostic.Kind.ERROR, "Missing output directory");
            return false;
        }

        if (javadocUrl == null) {
            reporter.print(Diagnostic.Kind.ERROR, "Missing javadoc url");
            return false;
        }

        var mapper = new JsonMapper();

        var index = mapper.createArrayNode();
        var indexer = new Indexer(index, environment);
        for (var element : environment.getIncludedElements()) {
            indexer.visit(element);
        }

        var config = mapper.createObjectNode();
        config.put("javadocUrl", javadocUrl);

        try {
            Files.createDirectories(outputDir);
            mapper.writeValue(outputDir.resolve("index.json").toFile(), index);
            mapper.writeValue(outputDir.resolve("config.json").toFile(), config);
            Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/index.html")), outputDir.resolve("index.html"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    record NestedIndex(String name, String link) {

    }

    class Indexer extends ElementScanner14<Void, Void> {

        private final ArrayNode node;

        private final Elements elementUtils;
        private final DocTrees docUtils;
        private final Types typeUtils;

        private final Deque<NestedIndex> packages = new ArrayDeque<>();
        private final Deque<NestedIndex> nestedTypes = new ArrayDeque<>();
        private int id = 1;

        Indexer(ArrayNode node, DocletEnvironment environment) {
            this.node = node;
            this.elementUtils = environment.getElementUtils();
            this.docUtils = environment.getDocTrees();
            this.typeUtils = environment.getTypeUtils();
        }

        private boolean isExcluded(Element element) {
            var modifiers = element.getModifiers();
            if (!modifiers.contains(Modifier.PUBLIC) && !modifiers.contains(Modifier.PROTECTED)) {
                return true;
            }

            var commentTree = docUtils.getDocCommentTree(element);
            if (commentTree != null) {
                var hidden = new boolean[]{false};
                commentTree.accept(new DocTreeScanner<Void, Void>() {
                    @Override
                    public Void visitHidden(HiddenTree node, Void unused) {
                        hidden[0] = true;
                        return super.visitHidden(node, unused);
                    }
                }, null);

                return hidden[0];
            }

            return false;
        }

        private String replace(String name) {
            return name.replace('.', '/').replace('$', '.');
        }

        private Void index(Element element, String type, String key, String title, @Nullable String unifier, String link) {
            var index = node.addObject();
            index.put("id", id++);
            index.put("type", type);
            index.put("key", String.join(" ", String.join(" ", StringUtils.splitByCharacterTypeCamelCase(key)).split("[.$/]")));
            index.put("title", title);
            index.put("link", link);

            if (unifier != null) {
                index.put("unifier", unifier);
            }

            var body = elementUtils.getDocComment(element);
            if (body != null) index.put("body", body.trim().replaceAll(" +", " "));
            return null;
        }

        @Override
        public Void visitModule(ModuleElement e, Void unused) {
            var name = e.getQualifiedName().toString();
            return index(e, "module", name, name, null, "");
        }

        @Override
        public Void visitPackage(PackageElement e, Void unused) {
            var name = e.getQualifiedName().toString();

            packages.push(new NestedIndex(name, null));
            index(e, "package", name, name, null, replace(name) + "/package-summary.html");
            packages.pop();
            return null;
        }

        @Override
        public Void visitType(TypeElement e, Void unused) {
            if (isExcluded(e)) return null;
            if (e.getNestingKind() == NestingKind.ANONYMOUS) return null;

            var ppackage = packages.peek();

            var name = elementUtils.getBinaryName(e).toString();
            var key = ppackage != null ? name.replaceFirst("^" + ppackage.name, "") : name;
            var link = replace(name) + ".html";
            index(e, "type", key, name, null, link);

            nestedTypes.push(new NestedIndex(name, link));
            super.visitType(e, unused);
            nestedTypes.pop();
            return null;
        }

        @Override
        public Void visitExecutable(ExecutableElement e, Void unused) {
            if (!includeMembers) return null;
            if (isExcluded(e)) return null;

            var type = nestedTypes.peek();
            if (type == null) return null;

            var name = new StringBuilder(e.getSimpleName() + "(");
            var separator = "";
            for (VariableElement parameter : e.getParameters()) {
                name.append(separator);
                name.append(typeUtils.erasure(parameter.asType()).toString());
                separator = ",";
            }
            name.append(")");

            return index(e, "method", e.getSimpleName().toString(), type.name + "#" + name, type.name, type.link + "#" + name);
        }

        @Override
        public Void visitRecordComponent(RecordComponentElement e, Void unused) {
            return visitExecutable(e.getAccessor(), unused);
        }

        @Override
        public Void visitVariable(VariableElement e, Void unused) {
            if (!includeMembers) return null;
            if (isExcluded(e)) return null;

            var type = nestedTypes.peek();
            if (type == null) return null;

            var name = e.getSimpleName().toString();
            return index(e, "variable", name, type.name + "#" + name, null, type.link + "#" + name);
        }

        @Override
        public Void visitTypeParameter(TypeParameterElement e, Void unused) {
            return null;
        }

    }

}
