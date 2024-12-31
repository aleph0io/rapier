package rapier.processor.aws.ssm;

public @interface AwsSsmStringParameter {
  public static final String DEFAULT_VALUE_NOT_SET = "__UNDEFINED__";

  public String value();

  public String defaultValue() default "__UNDEFINED__";
}
