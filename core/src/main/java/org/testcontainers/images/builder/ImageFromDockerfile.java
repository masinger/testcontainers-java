package org.testcontainers.images.builder;

import lombok.Cleanup;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.testcontainers.ContainerControllerFactory;
import org.testcontainers.controller.ContainerController;
import org.testcontainers.controller.callbacks.BuildImageResultCallback;
import org.testcontainers.controller.intents.BuildImageIntent;
import org.testcontainers.controller.intents.BuildResultItem;
import org.testcontainers.docker.DockerClientFactory;
import org.testcontainers.images.ParsedDockerfile;
import org.testcontainers.images.builder.traits.BuildContextBuilderTrait;
import org.testcontainers.images.builder.traits.ClasspathTrait;
import org.testcontainers.images.builder.traits.DockerfileTrait;
import org.testcontainers.images.builder.traits.FilesTrait;
import org.testcontainers.images.builder.traits.StringsTrait;
import org.testcontainers.utility.DockerLoggerFactory;
import org.testcontainers.utility.LazyFuture;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Getter
public class ImageFromDockerfile extends LazyFuture<String> implements
        BuildContextBuilderTrait<ImageFromDockerfile>,
        ClasspathTrait<ImageFromDockerfile>,
        FilesTrait<ImageFromDockerfile>,
        StringsTrait<ImageFromDockerfile>,
        DockerfileTrait<ImageFromDockerfile> {

    private final String dockerImageName;

    private boolean deleteOnExit = true;

    private final Map<String, Transferable> transferables = new HashMap<>();
    private final Map<String, String> buildArgs = new HashMap<>();
    private Optional<String> dockerFilePath = Optional.empty();
    private Optional<Path> dockerfile = Optional.empty();
    private Optional<Boolean> disabledPush = Optional.empty();
    private Set<String> dependencyImageNames = Collections.emptySet();

    public ImageFromDockerfile() {
        this(ContainerControllerFactory.instance().controller().getRandomImageName());
    }

    public ImageFromDockerfile(String dockerImageName) {
        this(dockerImageName, true);
    }

    public ImageFromDockerfile(String dockerImageName, boolean deleteOnExit) {
        this.dockerImageName = dockerImageName;
        this.deleteOnExit = deleteOnExit;
    }

    @Override
    public ImageFromDockerfile withFileFromTransferable(String path, Transferable transferable) {
        Transferable oldValue = transferables.put(path, transferable);

        if (oldValue != null) {
            log.warn("overriding previous mapping for '{}'", path);
        }

        return this;
    }

    @Override
    protected final String resolve() {
        Logger logger = DockerLoggerFactory.getLogger(dockerImageName);

        ContainerController containerController = ContainerControllerFactory.instance().controller();

        try {
            if (deleteOnExit) {
                containerController.getResourceReaper().registerImageForCleanup(dockerImageName);
            }

            BuildImageResultCallback resultCallback = new BuildImageResultCallback() {
                @Override
                public void onNext(BuildResultItem item) {
                    super.onNext(item);
                    if (item.isErrorIndicated()) {
                        logger.error(item.getErrorDetail().getMessage());
                    } else {
                        logger.debug(StringUtils.chomp(item.getStream(), "\n"));
                    }
                }
            };

            // We have to use pipes to avoid high memory consumption since users might want to build really big images
            @Cleanup PipedInputStream in = new PipedInputStream();
            @Cleanup PipedOutputStream out = new PipedOutputStream(in);

            BuildImageIntent buildImageCmd = containerController.buildTemporaryImageIntent(in);
            configure(buildImageCmd);
            Map<String, String> labels = new HashMap<>();
            if (buildImageCmd.getLabels() != null) {
                labels.putAll(buildImageCmd.getLabels());
            }
            labels.putAll(DockerClientFactory.DEFAULT_LABELS);
            buildImageCmd.withLabels(labels);

            prePullDependencyImages(dependencyImageNames);

            BuildImageResultCallback exec = buildImageCmd.perform(resultCallback);

            long bytesToDockerDaemon = 0;

            // To build an image, we have to send the context to Docker in TAR archive format
            try (TarArchiveOutputStream tarArchive = new TarArchiveOutputStream(new GZIPOutputStream(out))) {
                tarArchive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

                for (Map.Entry<String, Transferable> entry : transferables.entrySet()) {
                    Transferable transferable = entry.getValue();
                    final String destination = entry.getKey();
                    transferable.transferTo(tarArchive, destination);
                    bytesToDockerDaemon += transferable.getSize();
                }
                tarArchive.finish();
            }

            log.info("Transferred {} to Docker daemon", FileUtils.byteCountToDisplaySize(bytesToDockerDaemon));
            if (bytesToDockerDaemon > FileUtils.ONE_MB * 50) // warn if >50MB sent to docker daemon
                log.warn("A large amount of data was sent to the Docker daemon ({}). Consider using a .dockerignore file for better performance.",
                        FileUtils.byteCountToDisplaySize(bytesToDockerDaemon));

            exec.awaitImageId();

            return dockerImageName;
        } catch(IOException e) {
            throw new RuntimeException("Can't close DockerClient", e);
        }
    }

    protected void configure(BuildImageIntent buildImageCmd) {
        buildImageCmd.withTag(this.getDockerImageName());
        this.dockerFilePath.ifPresent(buildImageCmd::withDockerfilePath);
        this.disabledPush.ifPresent(buildImageCmd::withDisabledPush);
        this.dockerfile.ifPresent(p -> {
            buildImageCmd.withDockerfile(p.toFile());
            dependencyImageNames = new ParsedDockerfile(p).getDependencyImageNames();

            if (dependencyImageNames.size() > 0) {
                // if we'll be pre-pulling images, disable the built-in pull as it is not necessary and will fail for
                // authenticated registries
                buildImageCmd.withPull(false);
            }
        });
        this.buildArgs.forEach(buildImageCmd::withBuildArg);
    }

    private void prePullDependencyImages(Set<String> imagesToPull) {
        final ContainerController containerController = ContainerControllerFactory.instance().controller();

        imagesToPull.forEach(imageName -> {
            try {
                log.info("Pre-emptively checking local images for '{}', referenced via a Dockerfile. If not available, it will be pulled.", imageName);
                containerController.checkAndPullImage(imageName);
            } catch (Exception e) {
                log.warn("Unable to pre-fetch an image ({}) depended upon by Dockerfile - image build will continue but may fail. Exception message was: {}", imageName, e.getMessage());
            }
        });
    }

    public ImageFromDockerfile withBuildArg(final String key, final String value) {
        this.buildArgs.put(key, value);
        return this;
    }

    public ImageFromDockerfile withBuildArgs(final Map<String, String> args) {
        this.buildArgs.putAll(args);
        return this;
    }

    public ImageFromDockerfile withDisabledPush(Boolean disabledPush) {
        this.disabledPush = Optional.ofNullable(disabledPush);
        return this;
    }

    /**
     * Sets the Dockerfile to be used for this image.
     *
     * @param relativePathFromBuildContextDirectory relative path to the Dockerfile, relative to the image build context directory
     * @deprecated It is recommended to use {@link #withDockerfile} instead because it honors .dockerignore files and
     * will therefore be more efficient. Additionally, using {@link #withDockerfile} supports Dockerfiles that depend
     * upon images from authenticated private registries.
     */
    @Deprecated
    public ImageFromDockerfile withDockerfilePath(String relativePathFromBuildContextDirectory) {
        this.dockerFilePath = Optional.of(relativePathFromBuildContextDirectory);
        return this;
    }

    /**
     * Sets the Dockerfile to be used for this image. Honors .dockerignore files for efficiency.
     * Additionally, supports Dockerfiles that depend upon images from authenticated private registries.
     *
     * @param dockerfile path to Dockerfile on the test host.
     */
    public ImageFromDockerfile withDockerfile(Path dockerfile) {
        this.dockerfile = Optional.of(dockerfile);
        return this;
    }

}
