package rapier.aws.ssm.compiler.util;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;

public class InterfacesTest {
  @Test
  public void test() {
    System.out
        .println(Interfaces.generateInterfaceImplementation(SsmClient.class, (out, method) -> {
          out.println(
              "        throw new UnsupportedOperationException(\"" + method.getName() + "\");");
        }));
  }
}
