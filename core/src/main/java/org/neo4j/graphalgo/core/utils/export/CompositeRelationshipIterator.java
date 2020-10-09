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
package org.neo4j.graphalgo.core.utils.export;

import com.carrotsearch.hppc.ObjectObjectHashMap;
import com.carrotsearch.hppc.ObjectObjectMap;
import com.carrotsearch.hppc.procedures.ObjectObjectProcedure;
import org.neo4j.graphalgo.core.huge.AdjacencyList;
import org.neo4j.graphalgo.core.huge.AdjacencyOffsets;
import org.neo4j.internal.batchimport.input.InputEntityVisitor;

import java.io.IOException;
import java.util.Map;

class CompositeRelationshipIterator {

    private final AdjacencyList adjacencyList;
    private final AdjacencyOffsets adjacencyOffsets;
    private final Map<String, AdjacencyList> propertyLists;
    private final Map<String, AdjacencyOffsets> propertyOffsets;

    private final String[] propertyKeys;
    private final AdjacencyList.DecompressingCursor cursorCache;
    private final ObjectObjectMap<String, AdjacencyOffsets> propertyOffsetsCache;
    private final ObjectObjectMap<String, AdjacencyList.Cursor> propertyCursorCache;

    CompositeRelationshipIterator(
        AdjacencyList adjacencyList,
        AdjacencyOffsets adjacencyOffsets,
        Map<String, AdjacencyList> propertyLists,
        Map<String, AdjacencyOffsets> propertyOffsets
    ) {
        this.adjacencyList = adjacencyList;
        this.adjacencyOffsets = adjacencyOffsets;
        this.propertyLists = propertyLists;
        this.propertyOffsets = propertyOffsets;

        // create data structures for internal use
        this.propertyKeys = propertyLists.keySet().toArray(new String[0]);
        this.cursorCache = adjacencyList.rawDecompressingCursor();
        this.propertyOffsetsCache = new ObjectObjectHashMap<>(propertyOffsets.size());
        this.propertyOffsets.forEach(propertyOffsetsCache::put);
        this.propertyCursorCache = new ObjectObjectHashMap<>(propertyLists.size());
        this.propertyLists.forEach((key, list) -> this.propertyCursorCache.put(key, list.rawCursor()));
    }

    CompositeRelationshipIterator concurrentCopy() {
        return new CompositeRelationshipIterator(adjacencyList, adjacencyOffsets, propertyLists, propertyOffsets);
    }

    int propertyCount() {
        return propertyKeys.length;
    }

    void forEachRelationship(long sourceId, String relType, InputEntityVisitor visitor) throws IOException {
        var offset = adjacencyOffsets.get(sourceId);

        if (offset == 0L) {
            return;
        }

        // init adjacency cursor
        var adjacencyCursor = AdjacencyList.decompressingCursor(cursorCache, offset);
        // init property cursors
        for (var propertyKey : propertyKeys) {
            propertyCursorCache.put(
                propertyKey,
                AdjacencyList.cursor(
                    propertyCursorCache.get(propertyKey),
                    propertyOffsetsCache.get(propertyKey).get(sourceId)
                )
            );
        }

        // in-step iteration of adjacency and property cursors
        while (adjacencyCursor.hasNextVLong()) {
            visitor.startId(sourceId);
            visitor.endId(adjacencyCursor.nextVLong());
            visitor.type(relType);

            propertyCursorCache.forEach((ObjectObjectProcedure<String, AdjacencyList.Cursor>) (propertyKey, propertyCursor) -> {
                visitor.property(
                    propertyKey,
                    Double.longBitsToDouble(propertyCursor.nextLong())
                );
            });

            visitor.endOfEntity();
        }
    }
}
