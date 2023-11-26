/*
 * Copyright (C) 2023, Ashley Scopes.
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
package io.github.ascopes.protobufmavenplugin.resolve.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * File system walker that discovers protobuf sources in given source directories.
 *
 * @author Ashley Scopes
 */
public final class ProtoSourceResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProtoSourceResolver.class);

  private ProtoSourceResolver() {
    // Static-only class
  }

  /**
   * Discover any {@code *.proto} sources in the given source directory, recursively.
   *
   * @param sourceDirs the source directories to walk recursively. Must be a collection to prevent
   *                   confusion with a single {@code Path} which would be a valid {@code Iterable}
   *                   of {@code Path}s.
   * @return the list of discovered protobuf sources.
   * @throws IOException if an issue occurs walking the file system.
   */
  public static List<Path> resolve(Collection<Path> sourceDirs) throws IOException {
    var protoSources = new ArrayList<Path>();

    for (var sourceDir : sourceDirs) {
      if (!Files.exists(sourceDir)) {
        LOGGER.info("Source directory {} does not exist", sourceDir);
        continue;
      }

      LOGGER.info("Discovering protobuf sources in {}", sourceDir);

      try (var stream = Files.walk(sourceDir)) {
        stream
            .filter(Files::isRegularFile)
            .filter(protoFileMatcher())
            .peek(fileDiscoveryLogger())
            .forEach(protoSources::add);
      }
    }

    LOGGER.info("Discovered a total of {} protobuf source(s) to compile", protoSources.size());
    return Collections.unmodifiableList(protoSources);
  }

  private static Predicate<Path> protoFileMatcher() {
    return file -> {
      var fileName = file.getFileName().toString();
      var periodIndex = fileName.lastIndexOf('.');

      if (periodIndex == -1) {
        // No file extension, so not a proto file.
        return false;
      }

      // Enforce lowercase '.proto' file extension only. Even on Windows, users can ensure they
      // follow standard lowercase naming conventions.
      return fileName.substring(periodIndex).equals(".proto");
    };
  }

  private static Consumer<Path> fileDiscoveryLogger() {
    return file -> LOGGER.debug("Discovered protobuf source file at {}", file);
  }
}