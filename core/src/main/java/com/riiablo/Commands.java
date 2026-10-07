package com.riiablo;

import android.support.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.Trie;
import org.apache.commons.lang3.math.NumberUtils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.net.Socket;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import com.artemis.annotations.Wire;

import com.riiablo.command.Action;
import com.riiablo.command.Command;
import com.riiablo.command.CommandManager;
import com.riiablo.command.OptionalParameter;
import com.riiablo.command.Parameter;
import com.riiablo.command.ParameterException;
import com.riiablo.console.Console;
import com.riiablo.cvar.Cvar;
import com.riiablo.engine.client.WeatherRenderSystem;
import com.riiablo.key.MappedKey;
import com.riiablo.logger.Level;
import com.riiablo.logger.Logger;
import com.riiablo.logger.LoggerRegistry;
import com.riiablo.map.RenderSystem;
import com.riiablo.map.EnvironmentCycle;
import com.riiablo.screen.SelectCharacterScreen3;
import com.riiablo.serializer.SerializeException;
import com.riiablo.serializer.StringSerializer;
import com.riiablo.suggester.CvarSuggester;
import com.riiablo.suggester.CvarValueSuggester;
import com.riiablo.suggester.KeySuggester;
import com.riiablo.suggester.KeyValueSuggester;
import com.riiablo.suggester.LoggerLevelSuggester;
import com.riiablo.suggester.LoggerSuggester;
import com.riiablo.validator.ValidationException;

public class Commands {
  public static Collection<Throwable> addTo(CommandManager commandManager) {
    return addTo(commandManager, Commands.class, new ArrayList<Throwable>());
  }

  private static Collection<Throwable> addTo(CommandManager commandManager, Class<?> clazz, Collection<Throwable> throwables) {
    for (Field field : ClassReflection.getFields(clazz)) {
      if (Command.class.isAssignableFrom(field.getType())) {
        try {
          commandManager.add((Command) field.get(null));
        } catch (Throwable t) {
          throwables.add(t);
        }
      }
    }

    for (Class<?> subclass : clazz.getClasses()) {
      addTo(commandManager, subclass, throwables);
    }

    return throwables;
  }

  private Commands() {}

  public static final Command help = Command.builder()
      .alias("help").alias("?")
      .description("Displays this message")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Riiablo.console.out.println("<> indicates required, [] indicates optional");
          for (Command cmd : Riiablo.commands.getCommands()) {
            Riiablo.console.out.println(cmd + " : " + cmd.getDescription());
          }
        }
      })
      .build();


  public static final Command clear = Command.builder()
      .alias("clear").alias("cls")
      .description("Clears the console output")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Riiablo.console.clear();
        }
      })
      .build();

  public static final Command exit = Command.builder()
      .alias("exit")
      .description("Closes the game")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Gdx.app.exit();
        }
      })
      .build();

  public static final Command home = Command.builder()
      .alias("home")
      .description("Prints the current home directory")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Riiablo.console.out.println(Riiablo.home);
        }
      })
      .build();

  public static final Command saves = Command.builder()
      .alias("saves")
      .description("Prints the current saves directory")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Riiablo.console.out.println(Riiablo.saves);
        }
      })
      .build();

  public static final Command cvars = Command.builder()
      .alias("cvars")
      .description("Prints the descriptions of all cvars")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Collection<Cvar> cvars = Riiablo.cvars.getCvars();
          for (Cvar cvar : cvars) {
            Riiablo.console.out.format("%s \"%s\"; %s (Default: \"%s\")%n",
                cvar.getAlias(), cvar.get(), cvar.getDescription(), cvar.getDefault());
          }
        }
      })
      .build();

  public static final Command get = Command.builder()
      .alias("get")
      .description("Prints the value of the specified cvar")
      .params(Parameter.of(Cvar.class).suggester(CvarSuggester.INSTANCE))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          String alias = instance.getArg(0);
          Cvar cvar = Riiablo.cvars.get(alias);
          if (cvar == null) {
            throw new ParameterException("Failed to find cvar by alias: %s. For a list of cvars type \"%s\"", alias, cvars.getAlias());
          }

          Riiablo.console.out.format("%s = %s%n", cvar.getAlias(), cvar.get());
        }
      })
      .build();

  public static final Command set = Command.builder()
      .alias("set")
      .description("Sets the value of the specified cvar")
      .params(
          Parameter.of(Cvar.class).suggester(CvarSuggester.INSTANCE),
          Parameter.of(String.class).suggester(CvarValueSuggester.INSTANCE))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          String alias = instance.getArg(0);
          String value = instance.getArg(1);
          Cvar cvar = Riiablo.cvars.get(alias);
          if (cvar == null) {
            throw new ParameterException("Failed to find cvar by alias: " + alias);
          }

          StringSerializer serializer = Riiablo.cvars.getSerializer(cvar);
          try {
            cvar.set(value, serializer);
          } catch (SerializeException e) {
            throw new ParameterException("Invalid value specified: \"%s\". Expected type: %s", value, cvar.getType().getName());
          } catch (ValidationException e) {
            throw new ParameterException("Invalid value specified: \"%s\". %s", value, e.getMessage());
          }
        }
      })
      .build();

  public static final Command bind = Command.builder()
      .alias("bind")
      .description("Binds a specified key")
      .params(
          Parameter.of(MappedKey.class).suggester(KeySuggester.INSTANCE),
          OptionalParameter.of(String.class).suggester(KeyValueSuggester.INSTANCE))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          String alias = instance.getArg(0);
          MappedKey key = Riiablo.keys.get(alias);
          if (key == null) {
            throw new ParameterException("Failed to find key by alias: " + alias);
          }

          if (instance.numArgs() == 1) {
            int[] assignments = key.getAssignments();
            Array<String> keynames = new Array<>(assignments.length);
            for (int assignment : assignments) {
              if (assignment != MappedKey.NOT_MAPPED) keynames.add(Input.Keys.toString(assignment));
            }

            Riiablo.console.out.println(key + " = " + keynames);
            return;
          }

          String value = instance.getArg(1);
          int keycode = KeyValueSuggester.INSTANCE.get(value);
          if (keycode == -1) {
            throw new ParameterException("Failed to find key by value: " + value);
          }

          Set<MappedKey> existingBinds = Riiablo.keys.get(keycode);
          for (MappedKey existingBind : existingBinds) {
            existingBind.unassignKey(keycode);
          }

          boolean assigned = key.assignFirst(keycode);
          if (!assigned) {
            throw new ParameterException("Unable to bind \"%s\", too many assignments", alias);
          }
        }
      })
      .build();

  public static final Command unbind = Command.builder()
      .alias("unbind")
      .description("Unbinds a specified key")
      .params(Parameter.of(MappedKey.class).suggester(KeySuggester.INSTANCE))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          String alias = instance.getArg(0);
          MappedKey key = Riiablo.keys.get(alias);
          if (key == null) {
            throw new ParameterException("Failed to find key by alias: " + alias);
          }

          key.unassign();
        }
      })
      .build();

  public static final Command assets = Command.builder()
      .alias("assets")
      .description("Prints a list of all loaded assets")
      .action(new Action() {
        @Override
        public void onExecuted(@NonNull Command.Instance instance) {
          Array<String> assets = Riiablo.assets.getAssetNames();
          for (String fileName : assets) {
            Riiablo.console.out.println(fileName);
          }
        }
      })
      .build();

  public static final Command glversion = Command.builder()
      .alias("gl.version")
      .description("Prints devices OpenGL version")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Riiablo.console.out.println(Gdx.gl.glGetString(GL20.GL_VERSION));
        }
      })
      .build();

  public static final Command renderZoom = Command.builder()
      .alias("render.zoom")
      .description("Sets an exact render profiling zoom: 1, 2, or 5")
      .params(Parameter.of(String.class))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          float zoom = NumberUtils.toFloat(instance.getArg(0), Float.NaN);
          if (zoom != 1f && zoom != 2f && zoom != 5f) {
            throw new ParameterException("Render profiling zoom must be 1, 2, or 5");
          }

          RenderSystem renderer = renderSystem();
          renderer.zoom(zoom, true);
          Riiablo.console.out.format("Render profiling zoom set to %.0fx%n", zoom);
        }
      })
      .build();

  public static final Command renderStats = Command.builder()
      .alias("render.stats")
      .description("Prints 1x/2x/5x render cache metrics; optional argument: reset")
      .params(OptionalParameter.of(String.class))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          RenderSystem renderer = renderSystem();
          if (instance.numArgs() == 1) {
            String action = instance.getArg(0);
            if (!"reset".equalsIgnoreCase(action)) {
              throw new ParameterException("Expected reset or no argument");
            }
            renderer.resetCacheMetrics();
            Riiablo.console.out.println("Render cache metrics reset");
            return;
          }

          Riiablo.console.out.println(renderer.cacheMetricsReport());
        }
      })
      .build();

  public static final Command environmentTime = Command.builder()
      .alias("env.time")
      .description("Prints or sets local environment time: day, dusk, night, dawn")
      .params(OptionalParameter.of(String.class))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          EnvironmentCycle environment = environmentCycle();
          if (instance.numArgs() == 1) {
            String value = instance.getArg(0);
            int period;
            if ("day".equalsIgnoreCase(value)) {
              period = EnvironmentCycle.PERIOD_DAY;
            } else if ("dusk".equalsIgnoreCase(value)) {
              period = EnvironmentCycle.PERIOD_DUSK;
            } else if ("night".equalsIgnoreCase(value)) {
              period = EnvironmentCycle.PERIOD_NIGHT;
            } else if ("dawn".equalsIgnoreCase(value)) {
              period = EnvironmentCycle.PERIOD_DAWN;
            } else {
              throw new ParameterException("Expected day, dusk, night, or dawn");
            }
            environment.setPeriodOfDay(period);
          }
          Riiablo.console.out.format("Environment: cycle=%d period=%s ticks=%d eclipse=%s%n",
              environment.cycleIndex(), EnvironmentCycle.periodName(environment.periodOfDay()),
              environment.ticks(), environment.eclipse());
        }
      })
      .build();

  public static final Command weather = Command.builder()
      .alias("weather")
      .description("Prints or overrides client weather: auto, off, rain, snow")
      .params(OptionalParameter.of(String.class))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          WeatherRenderSystem weather = weatherSystem();
          if (instance.numArgs() == 1) {
            String value = instance.getArg(0);
            try {
              weather.setControlMode(
                  WeatherRenderSystem.ControlMode.valueOf(value.toUpperCase(java.util.Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
              throw new ParameterException("Expected auto, off, rain, or snow");
            }
          }
          Riiablo.console.out.format("Weather: %s%n",
              weather.getControlMode().name().toLowerCase(java.util.Locale.ROOT));
        }
      })
      .build();

  public static final Command connect = Command.builder()
      .alias("connect")
      .description("Connects to specified server")
      .params(
          Parameter.of(String.class).suggester(new Console.SuggestionProvider() {
            @Override
            public int suggest(Console console, CharSequence buffer, String[] args, int arg) {
              console.in.append("127.0.0.1");
              return 1;
            }
          }),
          OptionalParameter.of(String.class))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          int port = instance.numArgs() == 1 ? 6114 : NumberUtils.toInt(instance.getArg(1), 6114);
          Socket socket = null;
          try {
            socket = Gdx.net.newClientSocket(Net.Protocol.TCP, instance.getArg(0), port, null);
            final Socket socketRef = socket;
            Gdx.app.postRunnable(new Runnable() {
              @Override
              public void run() {
                Riiablo.client.clearAndSet(new SelectCharacterScreen3(socketRef));
              }
            });
          } catch (Throwable t) {
            Gdx.app.error("Command", t.getMessage(), t);
            if (socket != null) socket.dispose();
          }
        }
      })
      .build();

  public static final Command loggers = Command.builder()
      .alias("loggers")
      .description("Prints the log level of all instanced loggers")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Trie<String, Logger> loggers = Riiablo.logs.getLoggers();
          for (Map.Entry<String, Logger> logger : loggers.entrySet()) {
            Riiablo.console.out.format("%s \"%s\"%n",
                Riiablo.logs.getDebugName(logger.getKey()),
                logger.getValue().level());
          }
        }
      })
      .build();

  public static final Command levels = Command.builder()
      .alias("levels").alias("contexts")
      .description("Prints the log level of all logger contexts")
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Trie<String, Level> loggers = Riiablo.logs.getContexts();
          for (Map.Entry<String, Level> logger : loggers.entrySet()) {
            Riiablo.console.out.format("%s \"%s\"%n",
                Riiablo.logs.getDebugName(logger.getKey()),
                logger.getValue());
          }
        }
      })
      .build();

  public static final Command getlevel = Command.builder()
      .alias("getlevel")
      .description("Prints the log level of the specified logger context")
      .params(Parameter.of(String.class).suggester(LoggerSuggester.INSTANCE))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          String alias = instance.getArg(0);
          if (alias.equalsIgnoreCase("root")) {
            alias = LoggerRegistry.ROOT;
          }

          Level level = Riiablo.logs.getLevel(alias);
          Riiablo.console.out.format("%s = %s%n", Riiablo.logs.getDebugName(alias), level);
        }
      })
      .build();

  public static final Command setlevel = Command.builder()
      .alias("setlevel")
      .description("Sets the log level of the specified logger context")
      .params(
          Parameter.of(String.class).suggester(LoggerSuggester.INSTANCE),
          Parameter.of(String.class).suggester(LoggerLevelSuggester.INSTANCE))
      .action(new Action() {
        @Override
        public void onExecuted(Command.Instance instance) {
          Level level = Level.valueOf(instance.getArg(1).toUpperCase(), null);
          if (level == null) {
            Riiablo.console.out.println("Unknown log level: " + instance.getArg(1));
            return;
          }

          String alias = instance.getArg(0);
          if (alias.equalsIgnoreCase("root")) {
            alias = LoggerRegistry.ROOT;
          }

          Riiablo.logs.setLevel(alias, level);
        }
      })
      .build();

  private static RenderSystem renderSystem() {
    if (Riiablo.engine == null) {
      throw new ParameterException("Render commands are only available in game");
    }
    RenderSystem renderer = Riiablo.engine.getSystem(RenderSystem.class);
    if (renderer == null) {
      throw new ParameterException("RenderSystem is not available");
    }
    return renderer;
  }

  private static EnvironmentCycle environmentCycle() {
    if (Riiablo.engine == null) {
      throw new ParameterException("Environment commands are only available in game");
    }
    EnvironmentAccess access = new EnvironmentAccess();
    Riiablo.engine.inject(access);
    if (access.environment == null) {
      throw new ParameterException("EnvironmentCycle is not available");
    }
    return access.environment;
  }

  private static WeatherRenderSystem weatherSystem() {
    if (Riiablo.engine == null) {
      throw new ParameterException("Weather command is only available in game");
    }
    WeatherRenderSystem weather = Riiablo.engine.getSystem(WeatherRenderSystem.class);
    if (weather == null) {
      throw new ParameterException("WeatherRenderSystem is not available");
    }
    return weather;
  }

  private static final class EnvironmentAccess {
    @Wire(name = "environment")
    EnvironmentCycle environment;
  }
}
