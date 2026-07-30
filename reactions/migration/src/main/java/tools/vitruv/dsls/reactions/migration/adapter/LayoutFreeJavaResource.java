package tools.vitruv.dsls.reactions.migration.adapter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.emf.common.util.URI;
import org.emftext.language.java.resource.JavaSourceOrClassFileResource;
import org.emftext.language.java.resource.java.IJavaOptions;

final class LayoutFreeJavaResource extends JavaSourceOrClassFileResource {
  LayoutFreeJavaResource(URI uri) {
    super(uri);
  }

  @Override
  public void load(Map<?, ?> options) throws IOException {
    Map<Object, Object> withLayoutDisabled = new HashMap<>(options);
    withLayoutDisabled.put(IJavaOptions.DISABLE_LAYOUT_INFORMATION_RECORDING, Boolean.TRUE);
    super.load(withLayoutDisabled);
  }

  @Override
  public void delete(Map<?, ?> options) throws IOException {
    if (getURI() != null && !getURI().isFile() && !getURI().isPlatform()) {
      unload();
      if (getResourceSet() != null) {
        getResourceSet().getResources().remove(this);
      }
      return;
    }

    super.delete(options);
  }
}
