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

package ai.grakn.graph.internal;

import ai.grakn.util.CommonUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *     Ensures each concept undergoes the correct type of validation.
 * </p>
 *
 * <p>
 *      Handles calling the relevant validation defined in {@link ValidateGlobalRules} depending on the
 *      type of the concept.
 * </p>
 *
 * @author fppt
 *
 */
class Validator {
    private final AbstractGraknGraph<?> graknGraph;
    private final List<String> errorsFound = new ArrayList<>();

    public Validator(AbstractGraknGraph graknGraph){
        this.graknGraph = graknGraph;
    }

    /**
     *
     * @return Any errors found during validation
     */
    public List<String> getErrorsFound(){
        return errorsFound;
    }

    /**
     *
     * @return True if the data and schema conforms to our concept.
     */
    public boolean validate(){
        CommonUtil.withImplicitConceptsVisible(graknGraph, () -> {
            //Validate Entity Types
            //Not Needed
            //Validate Entities
            graknGraph.txCache().getModifiedEntities().forEach(this::validateInstance);

            //Validate RoleTypes
            graknGraph.txCache().getModifiedRoles().forEach(this::validateRoleType);
            //Validate Role Players
            graknGraph.txCache().getModifiedCastings().forEach(this::validateCasting);

            //Validate Relation Types
            graknGraph.txCache().getModifiedRelationTypes().forEach(this::validateRelationType);
            //Validate Relations
            graknGraph.txCache().getModifiedRelations().forEach(relation -> validateRelation(graknGraph, relation));

            //Validate Rule Types
            //Not Needed
            //Validate Rules
            graknGraph.txCache().getModifiedRules().forEach(rule -> validateRule(graknGraph, rule));

            //Validate Resource Types
            //Not Needed
            //Validate Resource
            graknGraph.txCache().getModifiedResources().forEach(this::validateInstance);

        });

        return errorsFound.size() == 0;
    }

    /**
     * Validation rules exclusive to rules
     * @param graph the graph to query against
     * @param rule the rule which needs to be validated
     */
    private void validateRule(AbstractGraknGraph<?> graph, RuleImpl rule){
        errorsFound.addAll(ValidateGlobalRules.validateRuleOntologyElementsExist(graph, rule));
    }

    /**
     * Validation rules exclusive to relations
     * @param relation The relation to validate
     */
    private void validateRelation(AbstractGraknGraph<?> graph, RelationImpl relation){
        validateInstance(relation);
        ValidateGlobalRules.validateRelationshipStructure(relation).ifPresent(errorsFound::add);
        ValidateGlobalRules.validateRelationIsUnique(graph, relation).ifPresent(errorsFound::add);
    }

    /**
     * Validation rules exclusive to role players
     * @param casting The Role player to validate
     */
    private void validateCasting(Casting casting){
        ValidateGlobalRules.validatePlaysStructure(casting).ifPresent(errorsFound::add);
    }

    /**
     * Validation rules exclusive to role types
     * @param roleType The roleType to validate
     */
    private void validateRoleType(RoleImpl roleType){
        ValidateGlobalRules.validateHasSingleIncomingRelatesEdge(roleType).ifPresent(errorsFound::add);
    }

    /**
     * Validation rules exclusive to relation types
     * @param relationType The relationTypes to validate
     */
    private void validateRelationType(RelationTypeImpl relationType){
        ValidateGlobalRules.validateHasMinimumRoles(relationType).ifPresent(errorsFound::add);
        errorsFound.addAll(ValidateGlobalRules.validateRelationTypesToRolesSchema(relationType));
    }

    /**
     * Validation rules exclusive to instances
     * @param instance The instance to validate
     */
    private void validateInstance(ThingImpl instance) {
        ValidateGlobalRules.validateInstancePlaysAllRequiredRoles(instance).ifPresent(errorsFound::add);
    }
}
