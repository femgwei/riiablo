package com.riiablo.architecture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ClientUiBoundaryTest {
  private static final Pattern COMMENTS_AND_STRINGS = Pattern.compile(
      "/\\*.*?\\*/|//[^\\n]*|\"(?:\\\\.|[^\"\\\\])*+\"", Pattern.DOTALL);

  @Test void migratedGameplayConsumersCannotRegainScreensOrWidgets() throws IOException {
    Path source = repository().resolve("core/src/main/java/com/riiablo/engine/client");
    Pattern ui = Pattern.compile("com\\.riiablo\\.(?:screen|widget)\\.|clientContext\\s*\\.\\s*game\\s*\\(");
    for (String file : Arrays.asList("KeyboardMovementSystem.java", "CursorMovementSystem.java",
        "ClientItemManager.java", "ClientNetworkReceiver.java", "ClientEntityFactory.java",
        "NativeShrinePresentationSystem.java")) {
      assertFalse(ui.matcher(code(source.resolve(file))).find(), file + " must use a presentation port");
    }
  }

  @Test void dedicatedServerRecipesCannotInstallClientUiSystems() throws IOException {
    Path source = repository().resolve("server/d2gs/src/main/java/com/riiablo/server/d2gs");
    Pattern ui = Pattern.compile("com\\.riiablo\\.(?:engine\\.client|presentation|screen|widget)\\.");
    for (String file : Arrays.asList("D2GSSimulationAssembly.java", "MonsterAiArena.java")) {
      assertFalse(ui.matcher(code(source.resolve(file))).find(), file + " must remain headless");
    }
  }

  @Test void worldUiHasOnePresentationOwnerAndCannotReturnToEngine() throws IOException {
    Path source = repository().resolve("core/src/main/java/com/riiablo");
    for (String type : Arrays.asList("ClientNpc", "MenuManager", "DialogManager",
        "Act1QuestDialogController", "QuestDialogController", "Act1QuestIndicatorSystem",
        "LabelManager", "MonsterLabelManager", "MonsterNameResolver", "ZoneEntryDisplayer")) {
      assertTrue(Files.isRegularFile(source.resolve("presentation/" + type + ".java")), type);
      assertFalse(Files.exists(source.resolve("engine/client/" + type + ".java")),
          type + " must not regain an engine compatibility copy");
    }
    assertTrue(Files.isRegularFile(source.resolve("presentation/component/MenuWrapper.java")));
    assertFalse(Files.exists(source.resolve("engine/shared/component/MenuWrapper.java")));
    Pattern menu = Pattern.compile("com\\.riiablo\\.(?:widget\\.NpcMenu|presentation\\.component\\.MenuWrapper)");
    for (String directory : Arrays.asList("engine/server", "engine/shared")) {
      try (java.util.stream.Stream<Path> paths = Files.walk(source.resolve(directory))) {
        for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".java"))::iterator) {
          assertFalse(menu.matcher(code(path)).find(), path + " cannot own a client menu");
        }
      }
    }
  }

  @Test void npcPresentationUsesItsOwningSessionInsteadOfLiveGameGlobals() throws IOException {
    Path file = repository().resolve("core/src/main/java/com/riiablo/presentation/ClientNpc.java");
    assertFalse(Pattern.compile("Riiablo\\s*\\.\\s*(?:engine|game|charData|audio|files|string)\\b")
        .matcher(code(file)).find(), "NPC presentation must use injected world/context");
  }

  private static String code(Path file) throws IOException {
    return COMMENTS_AND_STRINGS.matcher(new String(Files.readAllBytes(file), StandardCharsets.UTF_8))
        .replaceAll(" ");
  }

  private static Path repository() {
    Path working = Paths.get("").toAbsolutePath().normalize();
    return Files.isDirectory(working.resolve("core/src")) ? working : working.getParent();
  }
}
