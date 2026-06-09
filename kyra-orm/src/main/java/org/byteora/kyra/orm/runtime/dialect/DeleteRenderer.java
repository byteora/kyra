package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.runtime.SqlRequest;

public interface DeleteRenderer {
    SqlRequest render(DeleteModel deleteModel, RenderContext context);
}
