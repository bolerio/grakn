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
 */

package ai.grakn.graql.internal.query.predicate;

import ai.grakn.concept.ResourceType;
import ai.grakn.graql.Var;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.VarPatternBuilder;
import ai.grakn.graql.admin.ValuePredicateAdmin;
import ai.grakn.graql.admin.VarPatternAdmin;
import ai.grakn.util.Schema;
import ai.grakn.util.StringUtil;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__;
import org.apache.tinkerpop.gremlin.structure.Vertex;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static ai.grakn.concept.ResourceType.DataType.SUPPORTED_TYPES;
import static ai.grakn.util.ErrorMessage.INVALID_VALUE;

abstract class ComparatorPredicate implements ValuePredicateAdmin {

    private final Optional<Object> originalValue;
    private final Optional<Object> value;
    private final Optional<VarPatternAdmin> var;

    private static final String[] VALUE_PROPERTIES =
            SUPPORTED_TYPES.values().stream()
                    .map(ResourceType.DataType::getConceptProperty)
                    .distinct()
                    .map(Enum::name)
                    .toArray(String[]::new);

    /**
     * @param value the value that this predicate is testing against
     */
    ComparatorPredicate(Object value) {
        if (value instanceof VarPatternBuilder) {
            this.originalValue = Optional.empty();
            this.value = Optional.empty();
            this.var = Optional.of(((VarPatternBuilder) value).pattern().admin());
        } else {
            // Convert integers to longs for consistency
            if (value instanceof Integer) {
                value = ((Integer) value).longValue();
            }

            this.originalValue = Optional.of(value);

            // Convert values to how they are stored in the graph
            ResourceType.DataType dataType = ResourceType.DataType.SUPPORTED_TYPES.get(value.getClass().getName());

            if (dataType == null) {
                throw new IllegalArgumentException(INVALID_VALUE.getMessage(value.getClass()));
            }

            // We can trust the `SUPPORTED_TYPES` map to store things with the right type
            //noinspection unchecked
            value = dataType.getPersistenceValue(value);

            this.value = Optional.of(value);
            this.var = Optional.empty();
        }
    }

    /**
     * @param var the variable that this predicate is testing against
     */
    ComparatorPredicate(VarPattern var) {
        this.originalValue = Optional.empty();
        this.value = Optional.empty();
        this.var = Optional.of(var.admin());
    }

    protected abstract String getSymbol();

    abstract <V> P<V> gremlinPredicate(V value);

    public String toString() {
        // If there is no value, then there must be a var
        //noinspection OptionalGetWithoutIsPresent
        String argument = value.map(StringUtil::valueToString).orElseGet(() -> var.get().getPrintableName());

        return getSymbol() + " " + argument;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ComparatorPredicate that = (ComparatorPredicate) o;

        return value != null ? value.equals(that.value) : that.value == null;

    }

    @Override
    public boolean isCompatibleWith(ValuePredicateAdmin predicate) {
        if (!(predicate instanceof EqPredicate)) return false;
        EqPredicate p = (EqPredicate) predicate;
        Object v = value.orElse(null);
        Object pval = p.equalsValue().orElse(null);
        return v == null
                || pval == null
                || gremlinPredicate(v).test(pval);
    }

    @Override
    public int hashCode() {
        return value != null ? value.hashCode() : 0;
    }

    @Override
    public Optional<P<Object>> getPredicate() {
        return value.map(this::gremlinPredicate);
    }

    @Override
    public Optional<VarPatternAdmin> getInnerVar() {
        return var;
    }

    @Override
    public final void applyPredicate(GraphTraversal<Vertex, Vertex> traversal) {
        var.ifPresent(theVar -> {
            // Compare to another variable
            String thisVar = UUID.randomUUID().toString();
            Var otherVar = theVar.getVarName();
            String otherValue = UUID.randomUUID().toString();

            Traversal[] traversals = Stream.of(VALUE_PROPERTIES)
                    .map(prop -> __.values(prop).as(otherValue).select(thisVar).values(prop).where(gremlinPredicate(otherValue)))
                    .toArray(Traversal[]::new);

            traversal.as(thisVar).select(otherVar.getValue()).or(traversals).select(thisVar);
        });

        value.ifPresent(theValue -> {
            // Compare to a given value
            ResourceType.DataType<?> dataType = SUPPORTED_TYPES.get(originalValue.get().getClass().getTypeName());
            Schema.ConceptProperty property = dataType.getConceptProperty();
            traversal.has(property.name(), gremlinPredicate(theValue));
        });
    }

}
