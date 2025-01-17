/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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
package org.wildfly.security.authz.jacc;

import static java.lang.System.getSecurityManager;
import static java.security.AccessController.doPrivileged;
import static org.wildfly.common.Assert.checkNotNullParam;
import static org.wildfly.security.authz.jacc.ElytronMessages.log;
import static org.wildfly.security.authz.jacc.ElytronPolicyConfiguration.State.OPEN;

import java.security.PrivilegedAction;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.security.jacc.PolicyConfiguration;
import jakarta.security.jacc.PolicyConfigurationFactory;
import jakarta.security.jacc.PolicyContext;
import jakarta.security.jacc.PolicyContextException;

/**
 * <p>A {@link jakarta.security.jacc.PolicyConfigurationFactory} implementation.
 *
 * <p>Accordingly with the JACC specification, a {@link PolicyConfigurationFactory} is a singleton, instantiate once during
 * the application server startup. Thus, there is only one instance of this class for a given JRE of an application server.
 *
 * <p>The static method {@link #getCurrentPolicyConfiguration()} is necessary in order to keep compatibility with TCK, given that
 * it will wrap both factory and policy provider into its own implementations and still should be possible to obtain the policy configuration
 * created by this factory by the {@link JaccDelegatingPolicy}. This behavior is exactly the same as currently being used by RI implementation from
 * GF and PicketBox.
 *
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 * @see org.wildfly.security.authz.jacc.JaccDelegatingPolicy
 */
public class ElytronPolicyConfigurationFactory extends PolicyConfigurationFactory {

    public static final PrivilegedAction<String> GET_CONTEXT_ID = () -> PolicyContext.getContextID();

    /**
     * Holds all {@link jakarta.security.jacc.PolicyConfiguration} created in runtime.
     */
    private static final Map<String, ElytronPolicyConfiguration> configurationRegistry = new ConcurrentHashMap<>();

    /**
     * <p>Returns the {@link jakarta.security.jacc.PolicyConfiguration} associated with the current policy context identifier.
     *
     * <p>This method only returns {@link jakarta.security.jacc.PolicyConfiguration} transitioned to the <i>in service</i> state.
     * If the configuration associated with the current policy context identifier is in a different state, a exception is thrown.
     *
     * @return this method always returns a configuration instance transitioned with the <i>in service</i> state and associated
     *         with the current policy context identifier.
     * @throws PolicyContextException if the configuration is in a different state than <i>in service</i>, no policy context identifier
     * was set or if no configuration is found for the given identifier.
     */
    static <P extends PolicyConfiguration> P getCurrentPolicyConfiguration() throws PolicyContextException {
        String contextID;

        if (getSecurityManager() != null) {
            contextID = doPrivileged(GET_CONTEXT_ID);
        } else {
            contextID = PolicyContext.getContextID();
        }

        if (contextID == null) {
            throw log.authzContextIdentifierNotSet();
        }

        try {
            P policyConfiguration = (P) configurationRegistry.get(contextID);

            if (policyConfiguration == null) {
                throw log.authzInvalidPolicyContextIdentifier(contextID);
            }

            if (!policyConfiguration.inService()) {
                throw log.authzPolicyConfigurationNotInService(contextID);
            }

            return policyConfiguration;
        } catch (Exception e) {
            throw log.authzUnableToObtainPolicyConfiguration(contextID, e);
        }
    }

    private PolicyConfiguration getPolicyConfiguration(String contextID, boolean create, boolean remove) throws PolicyContextException {
        checkNotNullParam("contextID", contextID);

        synchronized (configurationRegistry) {
            ElytronPolicyConfiguration policyConfiguration = configurationRegistry.get(contextID);

            if (policyConfiguration == null) {
                return create ? createPolicyConfiguration(contextID) : null;
            }

            if (remove) {
                policyConfiguration.delete();
            }

            policyConfiguration.transitionTo(OPEN);

            return policyConfiguration;
        }
    }

    @Override
    public PolicyConfiguration getPolicyConfiguration(String contextID, boolean remove) throws PolicyContextException {
        return getPolicyConfiguration(contextID, true, remove);
    }

    @Override
    public PolicyConfiguration getPolicyConfiguration(String contextID) {
        try {
            return getPolicyConfiguration(contextID, false, false);
        } catch (PolicyContextException e) {
            // This is unexpected as the exception would only be thrown if removal fails.
            log.trace("Unexpected exception caught", e);
            return null;
        }
    }

    @Override
    public PolicyConfiguration getPolicyConfiguration() {
        try {
            return getCurrentPolicyConfiguration();
        } catch (PolicyContextException e) {
            log.trace("Unable to obtain current policy configuration.", e);
            return null;
        }
    }

    @Override
    public boolean inService(String contextID) throws PolicyContextException {
        checkNotNullParam("contextID", contextID);

        synchronized (configurationRegistry) {
            PolicyConfiguration policyConfiguration = configurationRegistry.get(contextID);

            if (policyConfiguration == null) {
                return false;
            }

            return policyConfiguration.inService();
        }
    }

    private ElytronPolicyConfiguration createPolicyConfiguration(String contextID) {
        return configurationRegistry.computeIfAbsent(contextID, ElytronPolicyConfiguration::new);
    }

}
