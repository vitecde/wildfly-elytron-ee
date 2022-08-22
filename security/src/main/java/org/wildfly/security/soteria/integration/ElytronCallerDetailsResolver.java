/*
 * Copyright 2022 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.security.soteria.integration;

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.glassfish.soteria.authorization.spi.CallerDetailsResolver;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ElytronCallerDetailsResolver implements CallerDetailsResolver {

    @Override
    public Principal getCallerPrincipal() {
        return currentSecurityIdentity().getPrincipal();
    }

    @Override
    public <T extends Principal> Set<T> getPrincipalsByType(Class<T> pType) {
        Set<T> principals = new HashSet<>();
        Principal principal = getCallerPrincipal();
        if (principal.getClass().isAssignableFrom(pType)) {
            principals.add(pType.cast(principal));
        }
        return Collections.unmodifiableSet(principals);
    }

    @Override
    public boolean isCallerInRole(String role) {
        return currentSecurityIdentity().getRoles().contains(role);
    }

    @Override
    public Set<String> getAllDeclaredCallerRoles() {
        Set<String> allRoles = new HashSet<>();
        Iterator<String> it = currentSecurityIdentity().getRoles().iterator();
        while(it.hasNext()) {
            allRoles.add(it.next());
        }

        return Collections.unmodifiableSet(allRoles);
    }

    private static SecurityIdentity currentSecurityIdentity() {
        return SecurityDomain.getCurrent().getCurrentSecurityIdentity();
    }

}
