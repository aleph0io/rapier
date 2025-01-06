package rapier.processor.cli;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import dagger.Provides;
import rapier.processor.cli.thirdparty.com.sigpwned.just.args.JustArgs;

public class ModuleExperiment {
  private final String positional0;

  private final String positional1;

  private final String positional2;

  private final List<String> positional3;

  /**
   * -x, --xray
   */
  private final String option1234567;

  /**
   * -y, --yoyo, -Y, --no-yoyo
   */
  private final List<Boolean> flag2345678;



  public ModuleExperiment(String[] args) {
    this(asList(args));
  }

  public ModuleExperiment(List<String> args) {
    final Map<Character, String> shortOptions = new HashMap<>();
    shortOptions.put('x', "1234567");

    final Map<String, String> longOptions = new HashMap<>();
    longOptions.put("xray", "1234567");

    final Map<Character, String> shortPositiveFlags = new HashMap<>();
    shortPositiveFlags.put('y', "2345678");

    final Map<String, String> longPositiveFlags = new HashMap<>();
    longPositiveFlags.put("yoyo", "2345678");

    final Map<Character, String> shortNegativeFlags = new HashMap<>();
    shortNegativeFlags.put('Y', "2345678");

    final Map<String, String> longNegativeFlags = new HashMap<>();
    longNegativeFlags.put("no-yoyo", "2345678");

    JustArgs.ParsedArgs parsed;
    try {
      parsed = JustArgs.parseArgs(args, shortOptions, longOptions, shortPositiveFlags,
          longPositiveFlags, shortNegativeFlags, longNegativeFlags);
    } catch (JustArgs.IllegalSyntaxException e) {
      printHelp();
      System.exit(1);
      throw new AssertionError("exited");
    } catch (IllegalArgumentException e) {
      System.err.println(e.getMessage());
      System.exit(1);
      throw new AssertionError("exited");
    }

    final String positional0 = parsed.getArgs().size() >= 1 ? parsed.getArgs().get(0) : null;
    if (positional0 != null) {
      this.positional0 = positional0;
    } else {
      printHelp();
      throw new IllegalArgumentException("Missing required positional argument: positional 0");
    }

    final String postional1 = parsed.getArgs().size() >= 2 ? parsed.getArgs().get(1) : "default";
    this.positional1 = postional1;

    final String positional2 = parsed.getArgs().size() >= 3 ? parsed.getArgs().get(2) : null;
    this.positional2 = positional2;

    final List<String> positional3 =
        parsed.getArgs().size() > 3 ? parsed.getArgs().subList(3, parsed.getArgs().size())
            : emptyList();
    this.positional3 = positional3;

    final List<String> option1234567 = parsed.getOptions().getOrDefault("1234567", emptyList());
    if (option1234567.size() >= 1) {
      this.option1234567 = option1234567.get(option1234567.size() - 1);
    } else {
      printHelp();
      throw new IllegalArgumentException("Missing required option: option 1234567");
    }

    final List<Boolean> flag2345678 = parsed.getFlags().getOrDefault("2345678", emptyList());
    this.flag2345678 = flag2345678;
  }

  @Provides
  @PositionalCliParameter(0)
  public String providePositional0() {
    return positional0;
  }

  @Provides
  @PositionalCliParameter(1)
  public String providePositional1() {
    return positional1;
  }

  @Provides
  @Nullable
  @PositionalCliParameter(2)
  public String providePositional2() {
    return positional2;
  }

  @Provides
  @PositionalCliParameter(3)
  public List<String> providePositionalVarargs() {
    return positional3;
  }

  @Provides
  @OptionCliParameter(shortName = "x", longName = "xray")
  public String getOption1234567() {
    return option1234567;
  }

  @Provides
  @OptionCliParameter(shortName = "y", longName = "yoyo")
  public List<Boolean> getFlag2345678() {
    return flag2345678;
  }

  private void printHelp() {
    System.out.println("Usage: ModuleExperiment [options]");
    System.out.println("Options:");
    System.out.println("  -h, --help");
    System.out.println("  -v, --version");
    System.out.println("  -c, --config");
    System.out.println("  -f, --file");
    System.out.println("  -d, --directory");
  }
}
