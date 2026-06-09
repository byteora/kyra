package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.runtime.SqlRequest;

public interface UpdateRenderer {
    SqlRequest render(UpdateModel updateModel, RenderContext context);
}
