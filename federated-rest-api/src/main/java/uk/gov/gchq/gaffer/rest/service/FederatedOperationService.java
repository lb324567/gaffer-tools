/*
 * Copyright 2016-2017 Crown Copyright
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

package uk.gov.gchq.gaffer.rest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.glassfish.jersey.server.ChunkedOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.gov.gchq.gaffer.operation.impl.add.AddElements;
import uk.gov.gchq.gaffer.operation.impl.generate.GenerateElements;
import uk.gov.gchq.gaffer.operation.impl.generate.GenerateObjects;
import uk.gov.gchq.gaffer.operation.impl.get.GetAdjacentEntitySeeds;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllEdges;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetAllEntities;
import uk.gov.gchq.gaffer.operation.impl.get.GetEdges;
import uk.gov.gchq.gaffer.operation.impl.get.GetElements;
import uk.gov.gchq.gaffer.operation.impl.get.GetEntities;
import uk.gov.gchq.gaffer.rest.FederatedExecutor;
import uk.gov.gchq.gaffer.rest.dto.Operation;
import uk.gov.gchq.gaffer.rest.dto.OperationChain;
import uk.gov.gchq.gaffer.store.Context;
import java.io.Closeable;
import java.io.IOException;

import static uk.gov.gchq.gaffer.jsonserialisation.JSONSerialiser.createDefaultMapper;

public class FederatedOperationService implements IFederatedOperationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(FederatedOperationService.class);
    public final ObjectMapper mapper = createDefaultMapper();

    final FederatedExecutor executor = new FederatedExecutor(true);

    protected Context fetchContext() {
        return new Context();
    }

    @Override
    public Object execute(final OperationChain operationChain, final boolean skipErrors, final boolean runIndividually) {
        return executor.executeOperationChain(operationChain, fetchContext(), skipErrors, runIndividually);
    }

    @Override
    public ChunkedOutput<String> executeChunked(final OperationChain opChain, final boolean skipErrors, final boolean runIndividually) {
        // Create chunked output instance
        final ChunkedOutput<String> output = new ChunkedOutput<>(String.class, "\r\n");

        // write chunks to the chunked output object
        new Thread() {
            public void run() {
                try {
                    final Object result = execute(opChain, skipErrors, runIndividually);
                    chunkResult(result, output);
                } finally {
                    IOUtils.closeQuietly(output);
                }
            }
        }.start();

        return output;
    }

    @Override
    public Object generateObjects(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GenerateObjects.class, fetchContext(), skipErrors);
    }

    @Override
    public Object generateElements(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GenerateElements.class, fetchContext(), skipErrors);
    }

    @Override
    public Object getAdjacentEntitySeeds(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GetAdjacentEntitySeeds.class, fetchContext(), skipErrors);
    }

    @Override
    public Object getAllElements(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GetAllElements.class, fetchContext(), skipErrors);
    }

    @Override
    public Object getAllEntities(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GetAllEntities.class, fetchContext(), skipErrors);
    }

    @Override
    public Object getAllEdges(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GetAllEdges.class, fetchContext(), skipErrors);
    }

    @Override
    public Object getElements(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GetElements.class, fetchContext(), skipErrors);
    }

    @Override
    public Object getEntities(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GetEntities.class, fetchContext(), skipErrors);
    }

    @Override
    public Object getEdges(final Operation operation, final boolean skipErrors) {
        return executor.executeOperation(operation, GetEdges.class, fetchContext(), skipErrors);
    }

    @Override
    public void addElements(final Operation operation, final boolean skipErrors) {
        executor.executeOperation(operation, AddElements.class, fetchContext(), skipErrors);
    }

    protected void chunkResult(final Object result, final ChunkedOutput<String> output) {
        if (result instanceof Iterable) {
            final Iterable itr = (Iterable) result;
            try {
                for (final Object item : itr) {
                    output.write(mapper.writeValueAsString(item));
                }
            } catch (final IOException ioe) {
                LOGGER.warn("IOException (chunks)", ioe);
            } finally {
                if (itr instanceof Closeable) {
                    IOUtils.closeQuietly(((Closeable) itr));
                }
            }
        } else {
            try {
                output.write(mapper.writeValueAsString(result));
            } catch (IOException ioe) {
                LOGGER.warn("IOException (chunks)", ioe);
            }
        }
    }
}
