package org.testcontainers.docker;

import org.testcontainers.controller.ContainerController;
import org.testcontainers.ContainerProvider;

public class DockerContainerProvider implements ContainerProvider {

    @Override
    public ContainerController lazyController() {
        return new DockerContainerController(DockerClientFactory.lazyClient());
    }
}