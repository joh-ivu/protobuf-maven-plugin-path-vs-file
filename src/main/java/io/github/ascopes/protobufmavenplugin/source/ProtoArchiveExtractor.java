/*
 * Copyright (C) 2023 - 2024, Ashley Scopes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.ascopes.protobufmavenplugin.source;

import io.github.ascopes.protobufmavenplugin.system.Digests;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component that extracts proto sources from archives into a location on the root file system.
 *
 * @author Ashley Scopes
 */
@Named
public final class ProtoArchiveExtractor {

  private static final Logger log = LoggerFactory.getLogger(ProtoArchiveExtractor.class);

  private final FileSystemProvider jarFileSystemProvider;
  private final Path extractionBaseDir;

  @Inject
  public ProtoArchiveExtractor(MavenProject mavenProject) {
    jarFileSystemProvider = FileSystemProvider.installedProviders()
        .stream()
        .filter(provider -> provider.getScheme().equalsIgnoreCase("jar"))
        .findFirst()
        .orElseThrow();

    extractionBaseDir = Path.of(mavenProject.getBuild().getDirectory())
        .resolve("protobuf-maven-plugin")
        .resolve("extracted");
  }

  public Optional<ProtoFileListing> extractProtoFiles(Path zipPath) throws IOException {
    try (var vfs = jarFileSystemProvider.newFileSystem(zipPath, Map.of())) {
      var vfsRoot = vfs.getRootDirectories().iterator().next();
      var sourceFiles = findProtoFilesInArchive(vfsRoot);

      if (sourceFiles.isEmpty()) {
        return Optional.empty();
      }

      var extractionRoot = extractionBaseDir.resolve(generateUniqueName(zipPath));
      Files.createDirectories(extractionRoot);

      var targetFiles = new ArrayList<Path>();

      for (var sourceFile : sourceFiles) {
        var targetFile = changeRelativePath(extractionRoot, vfsRoot, sourceFile);
        log.trace("Copying {} to {}", sourceFile.toUri(), targetFile);

        // We have to do this on each iteration to ensure the directory hierarchy exists.
        Files.createDirectories(targetFile.getParent());
        Files.copy(sourceFile, targetFile);
        targetFiles.add(targetFile);
      }

      var listing = ImmutableProtoFileListing
          .builder()
          .originalRoot(zipPath)
          .protoFilesRoot(extractionRoot)
          .protoFiles(targetFiles)
          .build();

      return Optional.of(listing);
    }
  }

  private Collection<Path> findProtoFilesInArchive(Path archiveRootPath) throws IOException {
    try (var stream = Files.walk(archiveRootPath)) {
      return stream
          .filter(ProtoFilePredicates::isProtoFile)
          .peek(protoFile -> log.trace(
              "Found proto file {} in archive {}",
              protoFile.toUri(),
              archiveRootPath
          ))
          .collect(Collectors.toUnmodifiableList());
    }
  }

  private String generateUniqueName(Path archive) {
    return Digests.sha1(archive.getFileName().toString());
  }

  private Path changeRelativePath(Path newRoot, Path existingRoot, Path existingPath) {
    var path = newRoot;

    for (var part : existingRoot.relativize(existingPath)) {
      path = path.resolve(part.toString());
    }

    return path;
  }
}
