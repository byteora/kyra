package org.byteora.kyra.orm.runtime.dialect;

import org.byteora.kyra.orm.runtime.SqlRequest;

public interface QueryRenderer {
    SqlRequest render(QueryModel queryModel, RenderContext context);
}
