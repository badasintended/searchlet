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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.Doclet.Option.Kind;

public class OptionBuilder {

    private final List<String> names = new ArrayList<>();

    private int argCount = 0;
    private String parameters;

    private String desc = "";
    private Kind kind = Kind.STANDARD;

    public OptionBuilder(String... names) {
        Collections.addAll(this.names, names);
    }

    public OptionBuilder args(String... args) {
        this.argCount = args.length;
        this.parameters = Arrays.stream(args).collect(Collectors.joining(">:<", "<", ">"));
        return this;
    }

    public OptionBuilder desc(String desc) {
        this.desc = desc;
        return this;
    }

    public OptionBuilder kind(Kind kind) {
        this.kind = kind;
        return this;
    }

    public Doclet.Option build(AlwaysTrueProcessor processor) {
        return build((option, arguments) -> {
            processor.process(option, arguments);
            return true;
        });
    }

    public Doclet.Option build(Processor processor) {
        return new Doclet.Option() {
            @Override
            public int getArgumentCount() {
                return argCount;
            }

            @Override
            public String getDescription() {
                return desc;
            }

            @Override
            public Kind getKind() {
                return kind;
            }

            @Override
            public List<String> getNames() {
                return names;
            }

            @Override
            public String getParameters() {
                return parameters;
            }

            @Override
            public boolean process(String option, List<String> arguments) {
                return processor.process(option, arguments);
            }
        };
    }

    @FunctionalInterface
    public interface Processor {

        boolean process(String option, List<String> arguments);

    }

    public interface AlwaysTrueProcessor {

        void process(String option, List<String> arguments);

    }

}
