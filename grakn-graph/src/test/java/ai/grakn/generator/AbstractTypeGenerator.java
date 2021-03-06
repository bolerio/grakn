/*
 * Grakn - A Distributed Semantic Database
 * Copyright (C) 2016  Grakn Labs Limited
 *
 * Grakn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Grakn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Grakn. If not, see <http://www.gnu.org/licenses/gpl.txt>.
 *
 */

package ai.grakn.generator;

import ai.grakn.concept.OntologyConcept;
import ai.grakn.concept.Type;
import ai.grakn.concept.Label;
import ai.grakn.util.Schema;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.pholser.junit.quickcheck.generator.GeneratorConfiguration;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Collection;
import java.util.Optional;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.stream.Collectors.toSet;

public abstract class AbstractTypeGenerator<T extends OntologyConcept> extends FromGraphGenerator<T> {

    private Optional<Boolean> meta = Optional.empty();
    private Optional<Boolean> includeAbstract = Optional.empty();

    AbstractTypeGenerator(Class<T> type) {
        super(type);
    }

    @Override
    protected final T generateFromGraph() {
        Collection<T> types;

        if (!includeNonMeta()) {
            types = Sets.newHashSet(otherMetaTypes());
            types.add(metaType());
        } else {
            types = (Collection<T>) metaType().subs();
        }

        types = types.stream().filter(this::filter).collect(toSet());

        if (!includeMeta()) {
            types.remove(metaType());
            types.removeAll(otherMetaTypes());
        }

        if(!includeAbstract()){
            types = types.stream().filter(type -> Schema.MetaSchema.isMetaLabel(type.getLabel()) || type instanceof Type && !((Type) type).isAbstract()).collect(toSet());
        }

        if (types.isEmpty() && includeNonMeta()) {
            Label label = genFromGraph(TypeLabels.class).mustBeUnused().generate(random, status);
            assert graph().getOntologyConcept(label) == null;
            return newType(label);
        } else {
            return random.choose(types);
        }
    }

    protected abstract T newType(Label label);

    protected abstract T metaType();

    protected Collection<T> otherMetaTypes() {
        return ImmutableSet.of();
    }

    protected boolean filter(T type) {
        return true;
    }

    private final boolean includeMeta() {
        return meta.orElse(true);
    }

    private final boolean includeNonMeta() {
        return !meta.orElse(false);
    }

    private final boolean includeAbstract(){
        return includeAbstract.orElse(true);
    }

    final AbstractTypeGenerator<T> excludeMeta() {
        meta = Optional.of(false);
        return this;
    }

    final AbstractTypeGenerator<T> excludeAbstract() {
        includeAbstract = Optional.of(false);
        return this;
    }

    public final void configure(Meta meta) {
        this.meta = Optional.of(meta.value());
    }

    public final void configure(Abstract includeAbstract) {
        this.includeAbstract = Optional.of(includeAbstract.value());
    }

    @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface Meta {
        boolean value() default true;
    }

    @Target({PARAMETER, FIELD, ANNOTATION_TYPE, TYPE_USE})
    @Retention(RUNTIME)
    @GeneratorConfiguration
    public @interface Abstract {
        boolean value() default true;
    }
}
