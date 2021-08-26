package org.testcontainers.controller;

import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.VolumesFrom;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public interface CreateContainerIntent {

    CreateContainerIntent withCmd(String... args);

    // TODO: Replace ExposedPort
    CreateContainerIntent withExposedPorts(List<ExposedPort> exposedPorts);

    CreateContainerIntent withExposedPorts(ExposedPort... exposedPorts);

    // TODO: Replace HostConfig
    CreateContainerIntent withHostConfig(HostConfig hostConfig);

    HostConfig getHostConfig();
    CreateContainerIntent withEnv(String[] envArray);

    // TODO: Replace Bind
    CreateContainerIntent withBinds(Bind[] bindsArray);

    // TODO: Replace VolumesFrom
    CreateContainerIntent withVolumesFrom(VolumesFrom[] volumesFromsArray);

    // TODO: Replace Link

    CreateContainerIntent withLinks(Link[] links);

    String getNetworkMode();

    CreateContainerIntent withNetworkMode(String networkMode);

    CreateContainerIntent withExtraHosts(String[] extraHosts);

    CreateContainerIntent withAliases(List<String> networkAliases);

    CreateContainerIntent withWorkingDir(String workingDirectory);

    CreateContainerIntent withPrivileged(boolean privilegedMode);

    CreateContainerIntent withHostName(String hostName);

    @NotNull
    Map<String, String> getLabels();

    CreateContainerIntent withLabels(Map<String, String> labels);

    CreateContainerIntent withEntrypoint(String entrypoint);

    CreateContainerIntent withName(String name);

    CreateContainerIntent withAttachStdin(boolean withStdIn);

    CreateContainerIntent withStdinOpen(boolean withStdinOpen);

    CreateContainerIntent withTty(boolean ttyEnabled);

    CreateContainerIntent withCapAdd(Capability capability);

    CreateContainerResult perform();
}