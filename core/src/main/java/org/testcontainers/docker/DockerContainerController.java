package org.testcontainers.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.testcontainers.controller.intents.ConnectToNetworkIntent;
import org.testcontainers.controller.ContainerController;
import org.testcontainers.controller.intents.CopyArchiveFromContainerIntent;
import org.testcontainers.controller.intents.CreateContainerIntent;
import org.testcontainers.controller.intents.InspectContainerIntent;
import org.testcontainers.controller.intents.ListContainersIntent;
import org.testcontainers.controller.intents.LogContainerIntent;
import org.testcontainers.controller.intents.StartContainerIntent;
import org.testcontainers.controller.intents.TagImageIntent;
import org.testcontainers.controller.intents.WaitContainerIntent;
import org.testcontainers.docker.intents.ConnectToNetworkDockerIntent;
import org.testcontainers.docker.intents.CopyArchiveFromContainerDockerIntent;
import org.testcontainers.docker.intents.CreateContainerDockerIntent;
import org.testcontainers.docker.intents.InspectContainerDockerIntent;
import org.testcontainers.docker.intents.ListContainersDockerIntent;
import org.testcontainers.docker.intents.LogContainerDockerIntent;
import org.testcontainers.docker.intents.StartContainerDockerIntent;
import org.testcontainers.docker.intents.TagImageDockerIntent;
import org.testcontainers.docker.intents.WaitContainerDockerIntent;
import org.testcontainers.images.TimeLimitedLoggedPullImageResultCallback;

@Slf4j
public class DockerContainerController implements ContainerController {


    private final DockerClient dockerClient;

    public DockerContainerController(DockerClient dockerClient) {
        this.dockerClient = dockerClient;
    }

    @Override
    public void warmup() {
        // trigger LazyDockerClient's resolve so that we fail fast here and not in getDockerImageName()
        dockerClient.authConfig();
    }

    @Override
    public StartContainerIntent startContainerIntent(String containerId) {
        return new StartContainerDockerIntent(dockerClient.startContainerCmd(containerId));
    }

    @Override
    public InspectContainerIntent inspectContainerIntent(String containerId) {
        return new InspectContainerDockerIntent(dockerClient.inspectContainerCmd(containerId));
    }

    @Override
    public ListContainersIntent listContainersIntent() {
        return new ListContainersDockerIntent(dockerClient.listContainersCmd());
    }

    @Override
    public ConnectToNetworkIntent connectToNetworkIntent() {
        return new ConnectToNetworkDockerIntent(dockerClient.connectToNetworkCmd());
    }

    @Override
    public CreateContainerIntent createContainerIntent(String containerImageName) {
        return new CreateContainerDockerIntent(dockerClient.createContainerCmd(containerImageName));
    }

    @Override
    public CopyArchiveFromContainerIntent copyArchiveFromContainerIntent(String containerId, String newRecordingFileName) {
        return new CopyArchiveFromContainerDockerIntent(dockerClient.copyArchiveFromContainerCmd(containerId, newRecordingFileName));
    }

    @Override
    public WaitContainerIntent waitContainerIntent(String containerId) {
        return new WaitContainerDockerIntent(dockerClient.waitContainerCmd(containerId));
    }

    @Override
    @SneakyThrows
    public void checkAndPullImage(String imageName) {
        try {
            dockerClient.inspectImageCmd(imageName).exec();
        } catch (NotFoundException notFoundException) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(imageName);
            try {
                pullImageCmd.exec(new TimeLimitedLoggedPullImageResultCallback(log)).awaitCompletion();
            } catch (DockerClientException | InterruptedException e) {
                // Try to fallback to x86
                pullImageCmd
                    .withPlatform("linux/amd64")
                    .exec(new TimeLimitedLoggedPullImageResultCallback(log))
                    .awaitCompletion();
            }
        }
    }

    @Override
    public TagImageIntent tagImageIntent(String sourceImage, String repositoryWithImage, String tag) {
        return new TagImageDockerIntent(dockerClient.tagImageCmd(sourceImage, repositoryWithImage, tag));
    }

    @Override
    public LogContainerIntent logContainerIntent(String containerId) {
        return new LogContainerDockerIntent(dockerClient.logContainerCmd(containerId));
    }
}
