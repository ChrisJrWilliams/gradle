/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.*;
import org.gradle.api.internal.artifacts.ResolverFactory;
import org.gradle.api.internal.artifacts.mvnsettings.CannotLocateLocalMavenRepositoryException;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.cached.CachedExternalResourceIndex;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultResolverFactory implements ResolverFactory {
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
    private final FileResolver fileResolver;
    private final Instantiator instantiator;
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder;
    private final CachedExternalResourceIndex<String> cachedExternalResourceIndex;

    public DefaultResolverFactory(LocalMavenRepositoryLocator localMavenRepositoryLocator, FileResolver fileResolver, Instantiator instantiator,
                                  RepositoryTransportFactory transportFactory,
                                  LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                                  CachedExternalResourceIndex<String> cachedExternalResourceIndex) {
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileResolver = fileResolver;
        this.instantiator = instantiator;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.cachedExternalResourceIndex = cachedExternalResourceIndex;
    }

    public ArtifactRepository createRepository(Object userDescription) {
        if (userDescription instanceof ArtifactRepository) {
            return (ArtifactRepository) userDescription;
        }

        if (userDescription instanceof String) {
            MavenArtifactRepository repository = createMavenRepository();
            repository.setUrl(userDescription);
            return repository;
        } else if (userDescription instanceof Map) {
            Map<String, ?> userDescriptionMap = (Map<String, ?>) userDescription;
            MavenArtifactRepository repository = createMavenRepository();
            ConfigureUtil.configureByMap(userDescriptionMap, repository);
            return repository;
        }

        DependencyResolver result;
        if (userDescription instanceof DependencyResolver) {
            result = (DependencyResolver) userDescription;
        } else {
            throw new InvalidUserDataException(String.format("Cannot create a DependencyResolver instance from %s", userDescription));
        }
        return new CustomResolverArtifactRepository(result, transportFactory);
    }

    public FlatDirectoryArtifactRepository createFlatDirRepository() {
        return instantiator.newInstance(DefaultFlatDirArtifactRepository.class, fileResolver, transportFactory);
    }

    public MavenArtifactRepository createMavenLocalRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository();
        try {
            final File localMavenRepository = localMavenRepositoryLocator.getLocalMavenRepository();
            mavenRepository.setUrl(localMavenRepository);
            return mavenRepository;
        } catch (CannotLocateLocalMavenRepositoryException ex) {

            throw new GradleException(ex.getMessage(), ex);
        }
    }

    public MavenArtifactRepository createMavenCentralRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository();
        mavenRepository.setUrl(RepositoryHandler.MAVEN_CENTRAL_URL);
        return mavenRepository;
    }

    public IvyArtifactRepository createIvyRepository() {
        return instantiator.newInstance(DefaultIvyArtifactRepository.class, fileResolver, createPasswordCredentials(), transportFactory,
                locallyAvailableResourceFinder, cachedExternalResourceIndex
        );
    }

    public MavenArtifactRepository createMavenRepository() {
        return instantiator.newInstance(DefaultMavenArtifactRepository.class, fileResolver, createPasswordCredentials(), transportFactory,
                locallyAvailableResourceFinder, cachedExternalResourceIndex
        );
    }

    private PasswordCredentials createPasswordCredentials() {
        return instantiator.newInstance(DefaultPasswordCredentials.class);
    }

}
