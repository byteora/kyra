package org.byteora.kyra.orm.query;

import java.util.List;
import java.util.function.Consumer;

/**
 * Restricted entity query facade with the entity table and result type preconfigured.
 */
public final class EntityQuery<T> {
    private final QueryWrapper<T> query;

    EntityQuery(QueryWrapper<T> query) {
        this.query = query;
    }

    public EntityQuery<T> where(Consumer<PredicateBuilder> consumer) {
        query.where(consumer);
        return this;
    }

    public EntityQuery<T> where(Condition condition) {
        query.where(condition);
        return this;
    }

    public EntityQuery<T> where(Condition... conditions) {
        query.where(conditions);
        return this;
    }

    public EntityQuery<T> orderBy(Consumer<OrderBuilder> consumer) {
        query.orderBy(consumer);
        return this;
    }

    public EntityQuery<T> orderBy(Order... orders) {
        query.orderBy(orders);
        return this;
    }

    public EntityQuery<T> limit(int limit) {
        query.limit(limit);
        return this;
    }

    public EntityQuery<T> limit(int offset, int limit) {
        query.limit(offset, limit);
        return this;
    }

    public T one() {
        return query.one();
    }

    public List<T> list() {
        return query.list();
    }

    public Page<T> page(int current, int size) {
        return query.page(current, size);
    }

    public Page<T> page(Paging paging) {
        return query.page(paging);
    }

    public long count() {
        return query.count();
    }

    public boolean exists() {
        return query.exists();
    }
}
