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
package ai.grakn.test.migration.export;

import ai.grakn.GraknGraph;
import ai.grakn.concept.Concept;
import ai.grakn.concept.Entity;
import ai.grakn.concept.Thing;
import ai.grakn.concept.Relation;
import ai.grakn.concept.RelationType;
import ai.grakn.concept.Resource;
import ai.grakn.concept.Role;
import ai.grakn.concept.Rule;
import ai.grakn.concept.Type;
import ai.grakn.graql.Graql;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class GraphWriterTestUtil {

    public static void insert(GraknGraph graph, String query) {
        graph.graql().parse("insert " + query).execute();
    }

    public static void assertDataEqual(GraknGraph one, GraknGraph two){
        one.admin().getMetaConcept().subs().stream().
                filter(Concept::isType).
                map(Concept::asType).
                flatMap(t -> t.instances().stream()).
                forEach(i -> assertInstanceCopied(i, two));
    }

    public static void assertInstanceCopied(Thing thing, GraknGraph two){
        if(thing.isEntity()){
            assertEntityCopied(thing.asEntity(), two);
        } else if(thing.isRelation()){
            assertRelationCopied(thing.asRelation(), two);
        } else if(thing.isRule()){
            assertRuleCopied(thing.asRule(), two);
        } else if(thing.isResource()){
            assertResourceCopied(thing.asResource(), two);
        }
    }

    /**
     * Assert that there are the same number of entities in each graph with the same resources
     */
    public static void assertEntityCopied(Entity entity1, GraknGraph two){
        Collection<Entity> entitiesFromGraph1 = entity1.resources().stream().map(Resource::ownerInstances).flatMap(Collection::stream).map(Concept::asEntity).collect(toSet());
        Collection<Entity> entitiesFromGraph2 = getInstancesByResources(two, entity1).stream().map(Concept::asEntity).collect(toSet());

        assertEquals(entitiesFromGraph1.size(), entitiesFromGraph2.size());
    }

    /**
     * Get all instances with the same resources
     */
    public static Collection<Thing> getInstancesByResources(GraknGraph graph, Thing thing){
        return thing.resources().stream()
                .map(r -> getResourceFromGraph(graph, r))
                .map(Resource::ownerInstances)
                .flatMap(Collection::stream)
                .collect(toSet());
    }

    /**
     * Get an entity that is uniquely defined by its resources
     */
    public static Thing getInstanceUniqueByResourcesFromGraph(GraknGraph graph, Thing thing){
        return getInstancesByResources(graph, thing)
               .iterator().next();
    }

    public static <V> Resource<V> getResourceFromGraph(GraknGraph graph, Resource<V> resource){
        return graph.getResourceType(resource.type().getLabel().getValue()).getResource(resource.getValue());
    }

    public static void assertRelationCopied(Relation relation1, GraknGraph two){
        if(relation1.rolePlayers().stream().anyMatch(Concept::isResource)){
            return;
        }

        RelationType relationType = two.getRelationType(relation1.type().getLabel().getValue());
        Map<Role, Set<Thing>> rolemap = relation1.allRolePlayers().entrySet().stream().collect(toMap(
                e -> two.getRole(e.getKey().getLabel().getValue()),
                e -> e.getValue().stream().
                        map(instance -> getInstanceUniqueByResourcesFromGraph(two, instance)).
                        collect(Collectors.toSet())
        ));

        boolean relationFound = false;
        for (Relation relation : relationType.instances()) {
            if(relation.allRolePlayers().equals(rolemap)){
                relationFound = true;
            }
        }

        assertTrue("The copied relation [" + relation1 + "] was not found.", relationFound);
    }

    public static void assertResourceCopied(Resource resource1, GraknGraph two){
        assertEquals(true, two.getResourcesByValue(resource1.getValue()).stream()
                .map(Thing::type)
                .map(Type::getLabel)
                .anyMatch(t -> resource1.type().getLabel().equals(t)));
    }

    public static void assertRuleCopied(Rule rule1, GraknGraph two){
        Rule rule2 = getInstanceUniqueByResourcesFromGraph(two, rule1).asRule();

        assertEquals(Graql.and(rule1.getLHS()), rule2.getLHS());
        assertEquals(Graql.and(rule1.getRHS()), rule2.getRHS());
    }

    public static void assertOntologiesEqual(GraknGraph one, GraknGraph two){
        boolean ontologyCorrect = one.admin().getMetaConcept().subs().stream().filter(Concept::isType)
                .allMatch(t -> typesEqual(t.asType(), two.getOntologyConcept(t.asType().getLabel())));
        assertEquals(true, ontologyCorrect);
    }

    public static boolean typesEqual(Type one, Type two){
        return one.getLabel().equals(two.getLabel())
                && one.isAbstract().equals(two.isAbstract())
                && (one.sup() == null || one.sup().getLabel().equals(two.sup().getLabel()))
                && (!one.isResourceType() || Objects.equals(one.asResourceType().getDataType(), two.asResourceType().getDataType()));
    }
}
