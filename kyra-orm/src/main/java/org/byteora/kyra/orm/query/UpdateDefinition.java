package org.byteora.kyra.orm.query;

import java.util.List;

public record UpdateDefinition(
        List<UpdateAssignment> assignments,
        WhereDefinition where
) {
}
