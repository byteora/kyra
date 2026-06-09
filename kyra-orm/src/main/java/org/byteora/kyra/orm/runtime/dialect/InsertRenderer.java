package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.runtime.SqlRequest;

public interface InsertRenderer {
    SqlRequest render(InsertModel insertModel, RenderContext context);
}
