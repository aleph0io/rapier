package rapier.processor.cli.util;

public final class CliProcessing {
  private CliProcessing() {}

  public static boolean isRequired(boolean nullable, String defaultValue) {
    return !nullable && defaultValue == null;
  }
}
