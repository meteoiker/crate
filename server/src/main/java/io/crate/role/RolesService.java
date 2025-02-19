/*
 * Licensed to Crate.io GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.role;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.metadata.Metadata;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.jetbrains.annotations.Nullable;

import io.crate.role.metadata.RolesMetadata;
import io.crate.role.metadata.UsersMetadata;
import io.crate.role.metadata.UsersPrivilegesMetadata;

public class RolesService implements Roles, ClusterStateListener {

    private volatile Set<Role> roles = Set.of(Role.CRATE_USER);

    @Inject
    public RolesService(ClusterService clusterService) {
        clusterService.addListener(this);
    }

    @Override
    public Collection<Role> roles() {
        return roles;
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        Metadata prevMetadata = event.previousState().metadata();
        Metadata newMetadata = event.state().metadata();

        UsersMetadata prevUsers = prevMetadata.custom(UsersMetadata.TYPE);
        UsersMetadata newUsers = newMetadata.custom(UsersMetadata.TYPE);
        RolesMetadata prevRoles = prevMetadata.custom(RolesMetadata.TYPE);
        RolesMetadata newRoles = newMetadata.custom(RolesMetadata.TYPE);

        UsersPrivilegesMetadata prevUsersPrivileges = prevMetadata.custom(UsersPrivilegesMetadata.TYPE);
        UsersPrivilegesMetadata newUsersPrivileges = newMetadata.custom(UsersPrivilegesMetadata.TYPE);

        if (prevUsers != newUsers || prevRoles != newRoles || prevUsersPrivileges != newUsersPrivileges) {
            roles = getRoles(newUsers, newRoles, newUsersPrivileges);
        }
    }


    static Set<Role> getRoles(@Nullable UsersMetadata usersMetadata,
                              @Nullable RolesMetadata rolesMetadata,
                              @Nullable UsersPrivilegesMetadata privilegesMetadata) {
        Map<String, Role> roles = new HashMap<>();
        roles.put(Role.CRATE_USER.name(), Role.CRATE_USER);
        if (usersMetadata != null) {
            for (Map.Entry<String, SecureHash> user: usersMetadata.users().entrySet()) {
                String userName = user.getKey();
                SecureHash password = user.getValue();
                Set<Privilege> privileges = null;
                if (privilegesMetadata != null) {
                    privileges = privilegesMetadata.getUserPrivileges(userName);
                    if (privileges == null) {
                        // create empty set
                        privilegesMetadata.createPrivileges(userName, Set.of());
                    }
                }
                roles.put(userName, Role.userOf(userName, privileges, password));
            }
        } else if (rolesMetadata != null) {
            for (Map.Entry<String, Role> role: rolesMetadata.roles().entrySet()) {
                String userName = role.getKey();
                SecureHash password = role.getValue().password();
                Set<Privilege> privileges = null;
                if (privilegesMetadata != null) {
                    privileges = privilegesMetadata.getUserPrivileges(userName);
                    if (privileges == null) {
                        // create empty set
                        privilegesMetadata.createPrivileges(userName, Set.of());
                    }
                }
                roles.put(userName, Role.of(userName, role.getValue().isUser(), privileges, password));
            }
        }
        return Collections.unmodifiableSet(new HashSet<>(roles.values()));
    }
}
