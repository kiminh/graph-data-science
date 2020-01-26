/*
 * Copyright (c) 2017-2020 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.graphalgo.degree;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.neo4j.graphalgo.AlgoTestBase;
import org.neo4j.graphalgo.QueryRunner;
import org.neo4j.graphalgo.TestDatabaseCreator;
import org.neo4j.graphalgo.TestSupport.AllGraphTypesTest;
import org.neo4j.graphalgo.api.Graph;
import org.neo4j.graphalgo.api.GraphFactory;
import org.neo4j.graphalgo.core.Aggregation;
import org.neo4j.graphalgo.core.GraphLoader;
import org.neo4j.graphalgo.core.loading.CypherGraphFactory;
import org.neo4j.graphalgo.core.utils.Pools;
import org.neo4j.graphalgo.core.utils.paged.AllocationTracker;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.Label;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.neo4j.graphalgo.QueryRunner.runInTransaction;

final class DegreeCentralityTest extends AlgoTestBase {

    private static final String DB_CYPHER =
            "CREATE" +
            "  (_:Label0 {name: '_'})" +
            ", (a:Label1 {name: 'a'})" +
            ", (b:Label1 {name: 'b'})" +
            ", (c:Label1 {name: 'c'})" +
            ", (d:Label1 {name: 'd'})" +
            ", (e:Label1 {name: 'e'})" +
            ", (f:Label1 {name: 'f'})" +
            ", (g:Label1 {name: 'g'})" +
            ", (h:Label1 {name: 'h'})" +
            ", (i:Label1 {name: 'i'})" +
            ", (j:Label1 {name: 'j'})" +
            ", (k:Label2 {name: 'k'})" +
            ", (l:Label2 {name: 'l'})" +
            ", (m:Label2 {name: 'm'})" +
            ", (n:Label2 {name: 'n'})" +
            ", (o:Label2 {name: 'o'})" +
            ", (p:Label2 {name: 'p'})" +
            ", (q:Label2 {name: 'q'})" +
            ", (r:Label2 {name: 'r'})" +
            ", (s:Label2 {name: 's'})" +
            ", (t:Label2 {name: 't'})" +

            ", (b)-[:TYPE1 {weight: 2.0}]->(c)" +
            ", (c)-[:TYPE1 {weight: 2.0}]->(b)" +

            ", (d)-[:TYPE1 {weight: 2.0}]->(a)" +
            ", (d)-[:TYPE1 {weight: 2.0}]->(b)" +

            ", (e)-[:TYPE1 {weight: 2.0}]->(b)" +
            ", (e)-[:TYPE1 {weight: 2.0}]->(d)" +
            ", (e)-[:TYPE1 {weight: 2.0}]->(f)" +

            ", (f)-[:TYPE1 {weight: 2.0}]->(b)" +
            ", (f)-[:TYPE1 {weight: 2.0}]->(e)" +

            ", (a)-[:TYPE3 {weight: -2.0}]->(b)" +

            ", (b)-[:TYPE3 {weight: 2.0}]->(c)" +
            ", (c)-[:TYPE3 {weight: 2.0}]->(b)" +

            ", (d)-[:TYPE3 {weight: 2.0}]->(a)" +
            ", (d)-[:TYPE3 {weight: 2.0}]->(b)" +

            ", (e)-[:TYPE3 {weight: 2.0}]->(b)" +
            ", (e)-[:TYPE3 {weight: 2.0}]->(d)" +
            ", (e)-[:TYPE3 {weight: 2.0}]->(f)" +

            ", (f)-[:TYPE3 {weight: 2.0}]->(b)" +
            ", (f)-[:TYPE3 {weight: 2.0}]->(e)" +

            ", (g)-[:TYPE2]->(b)" +
            ", (g)-[:TYPE2]->(e)" +
            ", (h)-[:TYPE2]->(b)" +
            ", (h)-[:TYPE2]->(e)" +
            ", (i)-[:TYPE2]->(b)" +
            ", (i)-[:TYPE2]->(e)" +
            ", (j)-[:TYPE2]->(e)" +
            ", (k)-[:TYPE2]->(e)";

    @BeforeEach
    void setupGraphDb() {
        db = TestDatabaseCreator.createTestDatabase();
        runQuery(DB_CYPHER);
    }

    @AfterEach
    void shutdownGraphDb() {
        db.shutdown();
    }

    @AllGraphTypesTest
    void outgoingCentrality(Class<? extends GraphFactory> graphFactory) {
        final Label label = Label.label("Label1");
        final Map<Long, Double> expected = new HashMap<>();

        runInTransaction(db, () -> {
                expected.put(db.findNode(label, "name", "a").getId(), 0.0);
                expected.put(db.findNode(label, "name", "b").getId(), 1.0);
                expected.put(db.findNode(label, "name", "c").getId(), 1.0);
                expected.put(db.findNode(label, "name", "d").getId(), 2.0);
                expected.put(db.findNode(label, "name", "e").getId(), 3.0);
                expected.put(db.findNode(label, "name", "f").getId(), 2.0);
                expected.put(db.findNode(label, "name", "g").getId(), 0.0);
                expected.put(db.findNode(label, "name", "h").getId(), 0.0);
                expected.put(db.findNode(label, "name", "i").getId(), 0.0);
                expected.put(db.findNode(label, "name", "j").getId(), 0.0);
            }
        );

        final Graph graph;
        if (graphFactory.isAssignableFrom(CypherGraphFactory.class)) {
            graph = QueryRunner.runInTransaction(
                db,
                () -> new GraphLoader(db)
                    .withLabel("MATCH (n:Label1) RETURN id(n) AS id")
                    .withRelationshipType(
                        "MATCH (n:Label1)-[:TYPE1]->(m:Label1) RETURN id(n) AS source, id(m) AS target")
                    .load(graphFactory)
            );
        } else {
            graph = new GraphLoader(db)
                    .withLabel(label)
                    .withRelationshipType("TYPE1")
                    .withDirection(Direction.OUTGOING)
                    .load(graphFactory);
        }

        DegreeCentrality degreeCentrality = new DegreeCentrality(
                graph,
                Pools.DEFAULT,
                4,
                Direction.OUTGOING,
                false,
                AllocationTracker.EMPTY);
        degreeCentrality.compute();

        IntStream.range(0, expected.size()).forEach(i -> {
            final long nodeId = graph.toOriginalNodeId(i);
            assertEquals(
                    expected.get(nodeId),
                    degreeCentrality.result().score(i),
                    1e-2,
                    "Node#" + nodeId
            );
        });
    }

    @AllGraphTypesTest
    void incomingCentrality(Class<? extends GraphFactory> graphFactory) {
        final Label label = Label.label("Label1");
        final Map<Long, Double> expected = new HashMap<>();

        runInTransaction(db, () -> {
                expected.put(db.findNode(label, "name", "a").getId(), 1.0);
                expected.put(db.findNode(label, "name", "b").getId(), 4.0);
                expected.put(db.findNode(label, "name", "c").getId(), 1.0);
                expected.put(db.findNode(label, "name", "d").getId(), 1.0);
                expected.put(db.findNode(label, "name", "e").getId(), 1.0);
                expected.put(db.findNode(label, "name", "f").getId(), 1.0);
                expected.put(db.findNode(label, "name", "g").getId(), 0.0);
                expected.put(db.findNode(label, "name", "h").getId(), 0.0);
                expected.put(db.findNode(label, "name", "i").getId(), 0.0);
                expected.put(db.findNode(label, "name", "j").getId(), 0.0);
            }
        );

        final Direction direction;
        final Graph graph;

        if (graphFactory.isAssignableFrom(CypherGraphFactory.class)) {
            // For Cypher we always treat the graph as outgoing, and let the user
            // handle the direction in the Cypher query
            direction = Direction.OUTGOING;
            graph = QueryRunner.runInTransaction(
                db,
                () -> new GraphLoader(db)
                    .withLabel("MATCH (n:Label1) RETURN id(n) AS id")
                    .withRelationshipType(
                        "MATCH (n:Label1)<-[:TYPE1]-(m:Label1) RETURN id(n) AS source, id(m) AS target")
                    .withDirection(direction)
                    .load(graphFactory)
            );
        } else {
            direction = Direction.INCOMING;
            graph = new GraphLoader(db)
                    .withLabel(label)
                    .withRelationshipType("TYPE1")
                    .withDirection(direction)
                    .load(graphFactory);
        }

        DegreeCentrality degreeCentrality = new DegreeCentrality(graph, Pools.DEFAULT, 4, direction, false, AllocationTracker.EMPTY);
        degreeCentrality.compute();

        IntStream.range(0, expected.size()).forEach(i -> {
            final long nodeId = graph.toOriginalNodeId(i);
            assertEquals(
                    expected.get(nodeId),
                    degreeCentrality.result().score(i),
                    1e-2,
                    "Node#" + nodeId
            );
        });
    }

    @AllGraphTypesTest
    void totalCentrality(Class<? extends GraphFactory> graphFactory) {
        Label label = Label.label("Label1");
        Map<Long, Double> expected = new HashMap<>();

        // if there are 2 relationships between a pair of nodes these get squashed into a single relationship
        // when we use an undirected graph
        runInTransaction(db, () -> {
            expected.put(db.findNode(label, "name", "a").getId(), 1.0);
            expected.put(db.findNode(label, "name", "b").getId(), 4.0);
            expected.put(db.findNode(label, "name", "c").getId(), 1.0);
            expected.put(db.findNode(label, "name", "d").getId(), 3.0);
            expected.put(db.findNode(label, "name", "e").getId(), 3.0);
            expected.put(db.findNode(label, "name", "f").getId(), 2.0);
            expected.put(db.findNode(label, "name", "g").getId(), 0.0);
            expected.put(db.findNode(label, "name", "h").getId(), 0.0);
            expected.put(db.findNode(label, "name", "i").getId(), 0.0);
            expected.put(db.findNode(label, "name", "j").getId(), 0.0);
        });

        final Graph graph;
        if (graphFactory.isAssignableFrom(CypherGraphFactory.class)) {
            graph = QueryRunner.runInTransaction(
                db,
                () -> new GraphLoader(db)
                    .withLabel("MATCH (n:Label1) RETURN id(n) AS id")
                    .withRelationshipType("MATCH (n:Label1)-[:TYPE1]-(m:Label1) RETURN id(n) AS source, id(m) AS target")
                    .withDefaultAggregation(Aggregation.SINGLE)
                    .load(graphFactory)
            );
        } else {
            graph = new GraphLoader(db)
                    .withLabel(label)
                    .withRelationshipType("TYPE1")
                    .withDirection(Direction.OUTGOING)
                    .undirected()
                    .load(graphFactory);
        }

        DegreeCentrality degreeCentrality = new DegreeCentrality(
                graph,
                Pools.DEFAULT,
                4,
                Direction.OUTGOING,
                false,
                AllocationTracker.EMPTY);
        degreeCentrality.compute();

        IntStream.range(0, expected.size()).forEach(i -> {
            long nodeId = graph.toOriginalNodeId(i);
            assertEquals(
                    expected.get(nodeId),
                    degreeCentrality.result().score(i),
                    1e-2,
                    "Node#" + nodeId + "[" + i + "]"
            );
        });
    }
}
