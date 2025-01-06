package rapier.processor.cli;

import rapier.processor.cli.model.BindingMetadata;

public interface PositionalParameterMetadataService {
  public BindingMetadata getPositionalParameterMetadata(int position);
}
