package com.riiablo;

import android.support.annotation.NonNull;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl.LwjglApplication;
import com.badlogic.gdx.backends.lwjgl.LwjglApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;

import com.riiablo.cvar.Cvar;
import com.riiablo.cvar.CvarStateAdapter;
import com.riiablo.logger.Level;
import com.riiablo.logger.LogManager;
import com.riiablo.logger.Logger;
import com.riiablo.util.InstallationFinder;

public class DesktopLauncher {
  private static final Logger log = LogManager.getLogger(DesktopLauncher.class);

  public static void main(String[] args) {
    Options options = new Options()
        .addOption(Option
            .builder("h")
            .longOpt("help")
            .desc("prints this message")
            .build())
        .addOption(Option
            .builder("v")
            .longOpt("viewport")
            .desc("viewport size (default 854x480)")
            .hasArg()
            .argName("size")
            .build())
        .addOption(Option
            .builder("w")
            .longOpt("windowed")
            .desc("forces windowed mode")
            .build())
        .addOption(Option
            .builder("f")
            .longOpt("fps")
            .desc("force enables fps counter")
            .build())
        .addOption(Option
            .builder("l")
            .longOpt("log-level")
            .desc("log verbosity for debugging purposes")
            .hasArg()
            .argName("level")
            .build())
        .addOption(Option
            .builder("g") // for graphics
            .longOpt("allow-software-mode")
            .desc("allows software OpenGL rendering if hardware acceleration is not available")
            .build())
        .addOption(Option
            .builder("d")
            .longOpt("d2")
            .desc("directory containing D2 MPQ files")
            .hasArg()
            .argName("path")
            .build())
        .addOption(Option
            .builder("s")
            .longOpt("saves")
            .desc("directory containing D2 character save files (*.d2s)")
            .hasArg()
            .argName("path")
            .build())
        ;

    CommandLine cmd = null;
    try {
      CommandLineParser parser = new DefaultParser();
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      log.error(e.getMessage(), e);
      System.err.println(e.getMessage());
      System.out.println("For usage, use -help option");
    } finally {
      if (cmd != null) {
        if (cmd.hasOption("help")) {
          log.debug("--help");
          HelpFormatter formatter = new HelpFormatter();
          formatter.printHelp("riiablo", options);
          System.exit(0);
          return;
        }
      }
    }

    // TODO: fix requiring bootstrapping this logger here
    //       loggers don't load contexts until client init (at the end of #main())
    LogManager.setLevel(DesktopLauncher.class.getName(), Level.DEBUG);

    final Level logLevel;
    if (cmd != null && cmd.hasOption("log-level")) {
      String optionValue = cmd.getOptionValue("log-level");
      log.debug("--log-level={}", optionValue);
      logLevel = Level.valueOf(optionValue, Level.WARN);
    } else {
      logLevel = Level.WARN;
    }
    log.debug("logLevel: {}", logLevel);
    LogManager.setLevel(DesktopLauncher.class.getName(), logLevel);
    
    // 屏蔽非地图相关的DEBUG日志，只保留地图拼接相关的日志
    // 设置战斗、动画、序列等系统的日志级别为WARN，屏蔽DEBUG日志
    LogManager.setLevel("com.riiablo.engine.server.Actioneer", Level.WARN);
    LogManager.setLevel("com.riiablo.engine.server.combat.DamageCalculator", Level.WARN);
    LogManager.setLevel("com.riiablo.engine.server.AnimStepper", Level.WARN);
    LogManager.setLevel("com.riiablo.engine.server.SequenceHandler", Level.WARN);
    LogManager.setLevel("com.riiablo.engine.client.DamageHandler", Level.WARN);
    LogManager.setLevel("com.riiablo.engine.client.DeathHandler", Level.WARN);
    LogManager.setLevel("com.riiablo.engine.client.CorpseManager", Level.WARN);
    
    // 保留地图相关的DEBUG日志
    LogManager.setLevel("com.riiablo.map.Act1MapBuilderD2MOD", Level.DEBUG);
    LogManager.setLevel("com.riiablo.map.Map", Level.DEBUG);
    LogManager.setLevel("com.riiablo.map.MapManager", Level.DEBUG);

    final InstallationFinder finder = InstallationFinder.getInstance();

    final FileHandle d2Home;
    if (cmd != null && cmd.hasOption("d2")) {
      String optionValue = cmd.getOptionValue("d2");
      log.debug("--d2={}", optionValue);
      d2Home = new FileHandle(optionValue);
      if (!InstallationFinder.isD2Home(d2Home)) {
        throw new GdxRuntimeException("'d2' does not refer to a valid D2 installation: " + d2Home);
      }
    } else {
      log.trace("Locating D2 installations...");
      Array<FileHandle> homeDirs = finder.getHomeDirs();
      log.trace("D2 installations: {}", homeDirs);
      if (homeDirs.size > 0) {
        d2Home = homeDirs.first();
      } else {
        d2Home = new FileHandle(SystemUtils.USER_HOME).child("riiablo");
        d2Home.mkdirs();
      }
    }
    log.debug("d2Home: {}", d2Home);

    final FileHandle d2Saves;
    if (cmd != null && cmd.hasOption("saves")) {
      String optionValue = cmd.getOptionValue("saves");
      log.debug("--saves={}", optionValue);
      d2Saves = new FileHandle(optionValue);
      if (!InstallationFinder.containsSaves(d2Saves)) {
        log.warn("'saves' does not contain any save files: " + d2Saves);
      }
    } else {
      log.trace("Locating D2 saves...");
      Array<FileHandle> saveDirs = finder.getSaveDirs(d2Home);
      log.trace("D2 saves: {}", saveDirs);
      d2Saves = saveDirs.first();
    }
    log.debug("d2Saves: {}", d2Saves);

    final LwjglApplicationConfiguration config = new LwjglApplicationConfiguration();
    config.title = "Riiablo";
    config.addIcon("ic_launcher_128.png", Files.FileType.Internal);
    config.addIcon("ic_launcher_32.png",  Files.FileType.Internal);
    config.addIcon("ic_launcher_16.png",  Files.FileType.Internal);
    config.resizable = false;
    config.allowSoftwareMode = cmd != null && cmd.hasOption("allow-software-mode");

    int width = 854, height = 480;
    if (cmd != null && cmd.hasOption("viewport")) {
      String optionValue = cmd.getOptionValue("viewport");
      log.debug("--viewport={}", optionValue);
      String[] optionValues = StringUtils.split(optionValue, 'x');
      if (optionValues.length != 2) {
        System.err.println("'viewport' should be formatted like 854x480");
        System.exit(0);
        return;
      }

      width = NumberUtils.toInt(optionValues[0], width);
      height = NumberUtils.toInt(optionValues[1], height);
    }
    log.debug("viewport: {}x{}", width, height);

    config.width = width;
    config.height = height;
    config.forceExit = SystemUtils.IS_OS_MAC_OSX; /** see {@link LwjglApplicationConfiguration#forceExit */
    final Client client = new Client(d2Home, d2Saves, height);
    boolean windowedForced = false;
    if (cmd != null) {
      windowedForced = cmd.hasOption("windowed");
      client.setWindowedForced(windowedForced);
      client.setDrawFPSForced(cmd.hasOption("fps"));
    }
    
    // 设置窗口模式（参考 OpenDiablo2）
    // 如果强制窗口化（命令行参数 -windowed），则设置为窗口模式
    // 否则根据 Cvar 的值决定
    // 注意：此时 Cvar 可能还没有被加载（在 Client.create() 中加载），所以使用默认值
    // 如果 Cvar 在加载时值发生变化，监听器会在 LwjglApplication 创建后处理
    boolean windowedCvar = Cvars.Client.Windowed.get(); // 获取 Cvar 值（可能是默认值或已加载的值）
    config.fullscreen = !windowedForced && !windowedCvar;
    
    // 记录窗口模式（仅PC平台有效，移动平台无效）
    // 使用 warn 级别确保日志能输出（默认日志级别是 WARN，warn 级别会输出）
    String windowMode = config.fullscreen ? "fullscreen" : "windowed";
    if (windowedForced) {
      log.warn("Window mode: {} (forced by -windowed command line option)", windowMode);
    } else {
      log.warn("Window mode: {} (determined by Cvar Client.Windowed={})", windowMode, Cvars.Client.Windowed.get());
    }

    new LwjglApplication(client, config);
    
    // 等待 Client.create() 完成，确保 Cvar 已加载
    // 然后根据实际加载的 Cvar 值设置窗口模式（如果与初始设置不一致）
    Gdx.app.postRunnable(new Runnable() {
      @Override
      public void run() {
        // 此时 Cvar 应该已经加载，检查是否需要调整窗口模式
        if (!client.isWindowedForced() && Gdx.graphics != null) {
          boolean windowedCvar = Cvars.Client.Windowed.get();
          boolean shouldBeWindowed = windowedCvar;
          boolean isCurrentlyFullscreen = Gdx.graphics.isFullscreen();
          
          if (shouldBeWindowed && isCurrentlyFullscreen) {
            // Cvar 要求窗口模式，但当前是全屏，切换到窗口模式
            Gdx.graphics.setWindowedMode((int) (Riiablo.DESKTOP_VIEWPORT_HEIGHT * 16f / 9f), Riiablo.DESKTOP_VIEWPORT_HEIGHT);
            log.warn("Window mode adjusted to windowed (Cvar Client.Windowed={})", windowedCvar);
          } else if (!shouldBeWindowed && !isCurrentlyFullscreen) {
            // Cvar 要求全屏模式，但当前是窗口模式，切换到全屏模式
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            log.warn("Window mode adjusted to fullscreen (Cvar Client.Windowed={})", windowedCvar);
          }
        }
      }
    });
    
    if (cmd != null) {
      final int gdxLogLevel;
      switch (logLevel) {
        case DEBUG:
          gdxLogLevel = Application.LOG_DEBUG;
          break;
        case INFO:
        case WARN:
          gdxLogLevel = Application.LOG_INFO;
          break;
        case ERROR:
        case FATAL:
          gdxLogLevel = Application.LOG_ERROR;
          break;
        case OFF:
        case TRACE:
        default:
          gdxLogLevel = Application.LOG_NONE;
      }

      Gdx.app.setLogLevel(gdxLogLevel);
    }

    Cvars.Client.Windowed.addStateListener(new CvarStateAdapter<Boolean>() {
      @Override
      public void onChanged(@NonNull Cvar<Boolean> cvar, Boolean from, Boolean to) {
        if (!client.isWindowedForced()) {
          // 根据 Cvar 值设置窗口模式
          if (to) {
            // Cvar 为 true，设置为窗口模式
            if (Gdx.graphics.isFullscreen()) {
              int windowWidth = (int) (Riiablo.DESKTOP_VIEWPORT_HEIGHT * 16f / 9f);
              int windowHeight = Riiablo.DESKTOP_VIEWPORT_HEIGHT;
              Gdx.graphics.setWindowedMode(windowWidth, windowHeight);
            }
          } else {
            // Cvar 为 false，设置为全屏模式
            if (!Gdx.graphics.isFullscreen()) {
              Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
            }
          }
        }
      }
    });

    Cvars.Client.Display.BackgroundFPSLimit.addStateListener(new CvarStateAdapter<Short>() {
      @Override
      public void onChanged(Cvar<Short> cvar, Short from, Short to) {
        config.backgroundFPS = to;
      }
    });

    Cvars.Client.Display.ForegroundFPSLimit.addStateListener(new CvarStateAdapter<Short>() {
      @Override
      public void onChanged(Cvar<Short> cvar, Short from, Short to) {
        config.foregroundFPS = to;
      }
    });
  }
}
