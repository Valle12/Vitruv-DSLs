package tools.vitruv.setup;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.emftext.language.java.resource.JavaSourceOrClassFileResource;
import org.emftext.language.java.resource.java.IJavaOptions;

public class LayoutFreeJavaResource extends JavaSourceOrClassFileResource {
  public LayoutFreeJavaResource(URI uri) {
    super(uri);
  }

  @Override
  public void load(Map<?, ?> options) throws IOException {
    Map<Object, Object> withLayoutDisabled = new HashMap<>(options);
    withLayoutDisabled.put(IJavaOptions.DISABLE_LAYOUT_INFORMATION_RECORDING, Boolean.TRUE);
    super.load(withLayoutDisabled);
  }
}
