package rapier.cli;

@SuppressWarnings("serial")
public class CliSyntaxException extends IllegalArgumentException {
  public CliSyntaxException(String message) {
    super(message);
  }
}
