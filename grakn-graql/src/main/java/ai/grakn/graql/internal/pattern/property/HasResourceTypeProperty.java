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

package ai.grakn.graql.internal.pattern.property;

import ai.grakn.concept.Concept;
import ai.grakn.concept.Label;
import ai.grakn.concept.RelationType;
import ai.grakn.concept.ResourceType;
import ai.grakn.concept.Role;
import ai.grakn.concept.Type;
import ai.grakn.exception.GraqlQueryException;
import ai.grakn.graql.Graql;
import ai.grakn.graql.Var;
import ai.grakn.graql.VarPattern;
import ai.grakn.graql.admin.Atomic;
import ai.grakn.graql.admin.ReasonerQuery;
import ai.grakn.graql.admin.VarPatternAdmin;
import ai.grakn.graql.internal.gremlin.EquivalentFragmentSet;
import ai.grakn.graql.internal.query.InsertQueryExecutor;
import ai.grakn.graql.internal.reasoner.atom.binary.TypeAtom;
import ai.grakn.util.Schema;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static ai.grakn.graql.Graql.var;
import static ai.grakn.util.Schema.ImplicitType.KEY;
import static ai.grakn.util.Schema.ImplicitType.KEY_OWNER;
import static ai.grakn.util.Schema.ImplicitType.KEY_VALUE;

/**
 * Represents the {@code has} and {@code key} properties on a {@link Type}.
 *
 * This property can be queried or inserted. Whether this is a key is indicated by the
 * {@link HasResourceTypeProperty#required} field.
 *
 * This property is defined as an implicit ontological structure between a {@link Type} and a {@link ResourceType},
 * including one implicit {@link RelationType} and two implicit {@link Role}s. The labels of these types are derived
 * from the label of the {@link ResourceType}.
 *
 * Like {@link HasResourceProperty}, if this is not a key and is used in a match query it will not use the implicit
 * structure - instead, it will match if there is any kind of relation type connecting the two types.
 *
 * @author Felix Chapman
 */
public class HasResourceTypeProperty extends AbstractVarProperty implements NamedProperty {

    private final VarPatternAdmin resourceType;

    private final VarPatternAdmin ownerRole;
    private final VarPatternAdmin valueRole;
    private final VarPatternAdmin relationOwner;
    private final VarPatternAdmin relationValue;

    private final boolean required;

    public HasResourceTypeProperty(VarPatternAdmin resourceType, boolean required) {
        this.resourceType = resourceType;
        this.required = required;

        Label resourceLabel = resourceType.getTypeLabel().orElseThrow(GraqlQueryException::noLabelSpecifiedForHas);

        VarPattern role = Graql.label(Schema.MetaSchema.ROLE.getLabel());

        VarPattern ownerRole = var().sub(role);
        VarPattern valueRole = var().sub(role);
        VarPattern relationType = var().sub(Graql.label(Schema.MetaSchema.RELATION.getLabel()));

        // If a key, limit only to the implicit key type
        if(required){
            ownerRole = ownerRole.label(KEY_OWNER.getLabel(resourceLabel));
            valueRole = valueRole.label(KEY_VALUE.getLabel(resourceLabel));
            relationType = relationType.label(KEY.getLabel(resourceLabel));
        }

        this.ownerRole = ownerRole.admin();
        this.valueRole = valueRole.admin();
        this.relationOwner = relationType.relates(this.ownerRole).admin();
        this.relationValue = relationType.admin().getVarName().relates(this.valueRole).admin();

    }

    public VarPatternAdmin getResourceType() {
        return resourceType;
    }

    @Override
    public String getName() {
        return required ? "key" : "has";
    }

    @Override
    public String getProperty() {
        return resourceType.getPrintableName();
    }

    @Override
    public Collection<EquivalentFragmentSet> match(Var start) {
        Collection<EquivalentFragmentSet> traversals = new HashSet<>();

        traversals.addAll(new PlaysProperty(ownerRole, required).match(start));
        //TODO: Get this to use real constraints no just the required flag
        traversals.addAll(new PlaysProperty(valueRole, false).match(resourceType.getVarName()));
        traversals.addAll(new NeqProperty(ownerRole).match(valueRole.getVarName()));

        return traversals;
    }

    @Override
    public Stream<VarPatternAdmin> getTypes() {
        return Stream.of(resourceType);
    }

    @Override
    public Stream<VarPatternAdmin> getInnerVars() {
        return Stream.of(resourceType);
    }

    @Override
    public Stream<VarPatternAdmin> getImplicitInnerVars() {
        return Stream.of(resourceType, ownerRole, valueRole, relationOwner, relationValue);
    }

    @Override
    public void insert(InsertQueryExecutor insertQueryExecutor, Concept concept) throws GraqlQueryException {
        Type entityTypeConcept = concept.asType();
        ResourceType resourceTypeConcept = insertQueryExecutor.getConcept(resourceType).asResourceType();

        if (required) {
            entityTypeConcept.key(resourceTypeConcept);
        } else {
            entityTypeConcept.resource(resourceTypeConcept);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        HasResourceTypeProperty that = (HasResourceTypeProperty) o;

        return resourceType.equals(that.resourceType);

    }

    @Override
    public int hashCode() {
        return resourceType.hashCode();
    }

    @Override
    public Atomic mapToAtom(VarPatternAdmin var, Set<VarPatternAdmin> vars, ReasonerQuery parent) {
        //TODO NB: HasResourceType is a special case and it doesn't allow variables as resource types
        Var varName = var.getVarName().asUserDefined();
        Label label = this.getResourceType().getTypeLabel().orElse(null);
        //isa part
        VarPatternAdmin resVar = varName.has(Graql.label(label)).admin();
        return new TypeAtom(resVar, parent);
    }
}
