package org.byteora.kyra.orm;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.byteora.kyra.orm.runtime.*;
import org.byteora.kyra.core.TypeRef;
import org.junit.jupiter.api.Test;

import org.byteora.kyra.orm.query.Column;
import org.byteora.kyra.orm.query.Conditions;
import org.byteora.kyra.orm.query.DSLContext;
import org.byteora.kyra.orm.query.Table;
import org.byteora.kyra.orm.query.Functions;
import org.byteora.kyra.orm.query.NamedSqlExpression;
import org.byteora.kyra.orm.query.Page;
import org.byteora.kyra.orm.query.Paging;
import org.byteora.kyra.orm.query.QueryWrapper;

class QueryWrapperTest {
    private final DefaultSqlGenerator sqlGenerator = new DefaultSqlGenerator();
    private final DSLContext dsl = new DSLContext(new RecordingSqlExecutor());

    @Test
    void predicateBuilderShouldRenderConditionalPredicates() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .where(where -> where
                        .eq(true, users.ID, 1L)
                        .like(false, users.NAME, "%skip%")
                        .isNotNull(users.NAME)
                        .in(users.ID, List.of(1L, 2L, 3L))
                        .between(users.AGE, 18, 30))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT * FROM users WHERE id = ? AND name IS NOT NULL AND id IN (?, ?, ?) AND age BETWEEN ? AND ?",
                request.sql());
        assertArrayEquals(new Object[]{1L, 1L, 2L, 3L, 18, 30}, request.args());
    }

    @Test
    void whereShouldSupportDirectConditionShortcut() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .where(users.ID.eq(1L))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT * FROM users WHERE id = ?",
                request.sql());
        assertArrayEquals(new Object[]{1L}, request.args());
    }

    @Test
    void whereShouldSupportMultipleConditionsAsAnd() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .where(
                        users.ID.eq(1L),
                        users.AGE.ge(18),
                        users.NAME.isNotNull()
                )
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT * FROM users WHERE (id = ? AND age >= ? AND name IS NOT NULL)",
                request.sql());
        assertArrayEquals(new Object[]{1L, 18}, request.args());
    }

    @Test
    void inAndNotInShouldHandleEmptyCollections() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .where(where -> where
                        .in(users.ID, List.of())
                        .notIn(users.AGE, List.of()))
                .toDefinition(), DbType.MYSQL);

        assertEquals("SELECT * FROM users WHERE 1 = 0 AND 1 = 1", request.sql());
        assertArrayEquals(new Object[0], request.args());
    }

    @Test
    void selectShouldSupportAggregateFunctions() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(
                        Functions.count().as("total"),
                        Functions.avg(users.AGE).as("avg_age"),
                        Functions.max(users.AGE).as("max_age"),
                        Functions.min(users.AGE).as("min_age"))
                .from(users)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS total, AVG(age) AS avg_age, MAX(age) AS max_age, MIN(age) AS min_age FROM users",
                request.sql());
        assertArrayEquals(new Object[0], request.args());
    }

    @Test
    void ifFunctionShouldRenderPerDbType() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest mysqlRequest = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group"))
                .from(users)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(age >= ?, ?, ?) AS age_group FROM users",
                mysqlRequest.sql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, mysqlRequest.args());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group"))
                .from(users)
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT CASE WHEN age >= ? THEN ? ELSE ? END AS age_group FROM users",
                postgresqlRequest.sql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, postgresqlRequest.args());
    }

    @Test
    void caseWhenShouldRenderMultipleBranches() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(Functions.caseWhen(Conditions.ge(users.AGE, 60), "senior")
                        .when(Conditions.ge(users.AGE, 18), "adult")
                        .orElse("minor")
                        .as("age_group"))
                .from(users)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT CASE WHEN age >= ? THEN ? WHEN age >= ? THEN ? ELSE ? END AS age_group FROM users",
                request.sql());
        assertArrayEquals(new Object[]{60, "senior", 18, "adult", "minor"}, request.args());
    }

    @Test
    void caseWhenShouldSupportAndOrConditionsAndNoElse() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(Functions.caseWhen(
                                Conditions.and(Conditions.ge(users.AGE, 18), Conditions.lt(users.AGE, 65)), "working")
                        .when(Conditions.or(Conditions.lt(users.AGE, 18), Conditions.ge(users.AGE, 65)), "exempt")
                        .end()
                        .as("tax_status"))
                .from(users)
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT CASE WHEN (age >= ? AND age < ?) THEN ? WHEN (age < ? OR age >= ?) THEN ? END AS tax_status FROM users",
                request.sql());
        assertArrayEquals(new Object[]{18, 65, "working", 18, 65, "exempt"}, request.args());
    }

    @Test
    void ifElseShouldSupportAndOrCombinedConditions() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(Functions.ifElse(
                        Conditions.and(Conditions.ge(users.AGE, 18), Conditions.lt(users.AGE, 65)),
                        1,
                        0).as("working_flag"))
                .from(users)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF((age >= ? AND age < ?), ?, ?) AS working_flag FROM users",
                request.sql());
        assertArrayEquals(new Object[]{18, 65, 1, 0}, request.args());
    }

    @Test
    void conditionsShouldPropagateDbTypeToNestedExpressions() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(Functions.ifElse(
                        Conditions.eq(
                                Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor"),
                                "adult"),
                        1,
                        0).as("adult_flag"))
                .from(users)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(IF(age >= ?, ?, ?) = ?, ?, ?) AS adult_flag FROM users",
                request.sql());
        assertArrayEquals(new Object[]{18, "adult", "minor", "adult", 1, 0}, request.args());
    }

    @Test
    void groupByAndHavingShouldSupportExpressions() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(
                        Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group"),
                        Functions.count().as("total"))
                .from(users)
                .groupBy(Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor"))
                .having(having -> having.ge(Functions.count(), 2))
                .orderBy(order -> order.desc(Functions.count()))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(age >= ?, ?, ?) AS age_group, COUNT(*) AS total FROM users GROUP BY IF(age >= ?, ?, ?) HAVING COUNT(*) >= ? ORDER BY COUNT(*) DESC",
                request.sql());
        assertArrayEquals(new Object[]{18, "adult", "minor", 18, "adult", "minor", 2}, request.args());
    }

    @Test
    void orderByShouldSupportAliasReference() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest mysqlRequest = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(
                        Functions.count().as("total"),
                        Functions.max(users.AGE).as("max_age"))
                .from(users)
                .orderBy(order -> order.descAlias("total").ascAlias("max_age"))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS total, MAX(age) AS max_age FROM users ORDER BY total DESC, max_age ASC",
                mysqlRequest.sql());
        assertArrayEquals(new Object[0], mysqlRequest.args());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(
                        Functions.count().as("total"),
                        Functions.max(users.AGE).as("max_age"))
                .from(users)
                .orderBy(order -> order.descAlias("total").ascAlias("max_age"))
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT COUNT(*) AS total, MAX(age) AS max_age FROM users ORDER BY total DESC, max_age ASC",
                postgresqlRequest.sql());
        assertArrayEquals(new Object[0], postgresqlRequest.args());
    }

    @Test
    void orderByShouldAcceptNamedExpressionDirectly() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(total)
                .from(users)
                .orderBy(order -> order.desc(total))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS total FROM users ORDER BY total DESC",
                request.sql());
        assertArrayEquals(new Object[0], request.args());
    }

    @Test
    void groupByShouldSupportAliasReference() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression ageGroup = Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group");

        SqlRequest mysqlRequest = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(ageGroup, Functions.count().as("total"))
                .from(users)
                .groupBy(ageGroup)
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(age >= ?, ?, ?) AS age_group, COUNT(*) AS total FROM users GROUP BY age_group",
                mysqlRequest.sql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, mysqlRequest.args());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(ageGroup, Functions.count().as("total"))
                .from(users)
                .groupByAlias("age_group")
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT CASE WHEN age >= ? THEN ? ELSE ? END AS age_group, COUNT(*) AS total FROM users GROUP BY age_group",
                postgresqlRequest.sql());
        assertArrayEquals(new Object[]{18, "adult", "minor"}, postgresqlRequest.args());
    }

    @Test
    void whereShouldRenderBeforeGroupBy() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression ageGroup = Functions.ifElse(Conditions.ge(users.AGE, 18), "adult", "minor").as("age_group");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(ageGroup, Functions.count().as("total"))
                .from(users)
                .where(users.ID.ge(10L), users.ID.lt(20L))
                .groupByAlias("age_group")
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT IF(age >= ?, ?, ?) AS age_group, COUNT(*) AS total FROM users "
                        + "WHERE (id >= ? AND id < ?) GROUP BY age_group",
                request.sql());
        assertArrayEquals(new Object[]{18, "adult", "minor", 10L, 20L}, request.args());
    }

    @Test
    void havingShouldSupportAliasReference() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");

        SqlRequest mysqlRequest = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(total)
                .from(users)
                .having(having -> having.geAlias("total", 2))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS total FROM users HAVING total >= ?",
                mysqlRequest.sql());
        assertArrayEquals(new Object[]{2}, mysqlRequest.args());

        SqlRequest postgresqlRequest = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(total)
                .from(users)
                .having(having -> having.geAlias("total", 2))
                .toDefinition(), DbType.POSTGRESQL);

        assertEquals(
                "SELECT COUNT(*) AS total FROM users HAVING total >= ?",
                postgresqlRequest.sql());
        assertArrayEquals(new Object[]{2}, postgresqlRequest.args());
    }

    @Test
    void havingShouldSupportDirectConditionShortcut() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(total)
                .from(users)
                .having(total.ge(2))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS total FROM users HAVING total >= ?",
                request.sql());
        assertArrayEquals(new Object[]{2}, request.args());
    }

    @Test
    void havingShouldSupportMultipleConditionsAsAnd() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");
        NamedSqlExpression maxAge = Functions.max(users.AGE).as("max_age");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(total, maxAge)
                .from(users)
                .having(total.ge(2), maxAge.ge(18))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS total, MAX(age) AS max_age FROM users HAVING (total >= ? AND max_age >= ?)",
                request.sql());
        assertArrayEquals(new Object[]{2, 18}, request.args());
    }

    @Test
    void namedExpressionShouldSupportDirectComparisonAndOrdering() {
        TestUserTable users = TestUserTable.USERS;
        NamedSqlExpression total = Functions.count().as("total");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(total)
                .from(users)
                .having(having -> having.condition(total.ge(2)))
                .orderBy(order -> order.asc(total))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT COUNT(*) AS total FROM users HAVING total >= ? ORDER BY total ASC",
                request.sql());
        assertArrayEquals(new Object[]{2}, request.args());
    }

    @Test
    void countRewriteShouldUseDialectCountRewriter() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.rewriteCount(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .where(where -> where.ge(users.AGE, 18))
                .orderBy(order -> order.asc(users.ID))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "select count(*) from (SELECT * FROM users WHERE age >= ?) _count",
                request.sql());
        assertArrayEquals(new Object[]{18}, request.args());
    }

    @Test
    void orderByVarargsShouldRenderMultipleOrders() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .where(users.AGE.ge(18))
                .orderBy(users.ID.asc(), users.NAME.desc())
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT * FROM users WHERE age >= ? ORDER BY id ASC, name DESC",
                request.sql());
        assertArrayEquals(new Object[]{18}, request.args());
    }

    @Test
    void existsShouldRenderLimitedSelectOne() {
        RecordingSqlExecutor sqlSession = new RecordingSqlExecutor();
        TestUserTable users = TestUserTable.USERS;

        boolean exists = new DSLContext(sqlSession).query(TestUser.class)
                .selectAll()
                .from(users)
                .where(users.ID.eq(1L))
                .exists();

        assertEquals(true, exists);
        assertEquals("SELECT 1 FROM users WHERE id = ? LIMIT ?", sqlSession.lastSql);
        assertArrayEquals(new Object[]{1L, 1}, sqlSession.lastArgs);
    }

    @Test
    void predicateBuilderShouldSupportOrAndNestedGroups() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .where(where -> where
                        .eq(users.ID, 1L)
                        .or(or -> or
                                .eq(users.NAME, "Bob")
                                .and(and -> and
                                        .ge(users.AGE, 18)
                                        .lt(users.AGE, 30))))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT * FROM users WHERE id = ? OR (name = ? AND (age >= ? AND age < ?))",
                request.sql());
        assertArrayEquals(new Object[]{1L, "Bob", 18, 30}, request.args());
    }

    @Test
    void joinOnShouldSupportPredicateBuilder() {
        TestUserTable users = TestUserTable.USERS;
        TestUserTable alias = new TestUserTable("users", "u2");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .leftJoin(alias)
                .on(on -> on
                        .condition(users.ID.eq(alias.ID))
                        .or(or -> or
                                .eq(alias.NAME, "Bob")
                                .eq(alias.AGE, 30)))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.* FROM users LEFT JOIN users u2 ON users.id = u2.id OR (u2.name = ? AND u2.age = ?)",
                request.sql());
        assertArrayEquals(new Object[]{"Bob", 30}, request.args());
    }

    @Test
    void joinShouldSupportShortcutOnClause() {
        TestUserTable users = TestUserTable.USERS;
        TestUserTable alias = new TestUserTable("users", "u2");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .leftJoin(alias, on -> on.eq(users.ID, alias.ID))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.* FROM users LEFT JOIN users u2 ON users.id = u2.id",
                request.sql());
        assertArrayEquals(new Object[0], request.args());
    }

    @Test
    void tableAliasShouldBeConvenientForSelfJoin() {
        TestUserTable users = TestUserTable.USERS;
        TestUserTable manager = users.alias("manager");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .select(users.NAME, manager.NAME)
                .from(users)
                .leftJoin(manager, on -> on.eq(users.ID, manager.ID))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.name, manager.name FROM users LEFT JOIN users manager ON users.id = manager.id",
                request.sql());
        assertArrayEquals(new Object[0], request.args());
    }

    @Test
    void selectAllShouldUseAliasWhenSingleTableAliasIsExplicit() {
        TestUserTable alias = TestUserTable.USERS.alias("u");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(alias)
                .where(alias.ID.eq(1L))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT u.* FROM users u WHERE u.id = ?",
                request.sql());
        assertArrayEquals(new Object[]{1L}, request.args());
    }

    @Test
    void predicateBuilderShouldSupportNotGroups() {
        TestUserTable users = TestUserTable.USERS;

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .where(where -> where
                        .eq(users.ID, 1L)
                        .and(and -> and.not(not -> not
                                .eq(users.NAME, "Bob")
                                .or(or -> or.lt(users.AGE, 18)))))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT * FROM users WHERE id = ? AND (NOT (name = ? OR (age < ?)))",
                request.sql());
        assertArrayEquals(new Object[]{1L, "Bob", 18}, request.args());
    }

    @Test
    void queryWrapperShouldSupportInnerAndRightJoin() {
        TestUserTable users = TestUserTable.USERS;
        TestUserTable innerAlias = new TestUserTable("users", "u2");
        TestUserTable rightAlias = new TestUserTable("users", "u3");

        SqlRequest request = sqlGenerator.renderQuery(dsl.query(Object.class)
                .selectAll()
                .from(users)
                .innerJoin(innerAlias)
                .on(users.ID.eq(innerAlias.ID))
                .rightJoin(rightAlias)
                .on(on -> on.eq(users.ID, rightAlias.ID))
                .toDefinition(), DbType.MYSQL);

        assertEquals(
                "SELECT users.* FROM users INNER JOIN users u2 ON users.id = u2.id RIGHT JOIN users u3 ON users.id = u3.id",
                request.sql());
        assertArrayEquals(new Object[0], request.args());
    }

    @Test
    void queryWrapperShouldExecuteWithBoundSqlSession() {
        RecordingSqlExecutor sqlSession = new RecordingSqlExecutor();
        TestUserTable users = TestUserTable.USERS;

        QueryWrapper<TestUser> query = new DSLContext(sqlSession).query(TestUser.class)
                .selectAll()
                .from(users)
                .where(users.ID.eq(1L));

        TestUser one = query.one();
        List<TestUser> list = query.list();
        long count = query.count();
        Page<TestUser> page = query.page(Paging.of(1, 10));

        assertEquals(sqlSession.oneResult, one);
        assertEquals(sqlSession.listResult, list);
        assertEquals(3L, count);
        assertEquals(3L, page.total());
        assertEquals(sqlSession.listResult, page.records());
    }

    @Test
    void dslContextShouldRejectNullExecutor() {
        assertThrows(NullPointerException.class, () -> new DSLContext(null));
    }

    @Test
    void selectAndSelectAllShouldBeMutuallyExclusiveInBothOrders() {
        TestUserTable users = TestUserTable.USERS;

        QueryWrapper<TestUser> selectFirst = dsl.query(TestUser.class).select(users.ID);
        QueryWrapper<TestUser> selectAllFirst = dsl.query(TestUser.class).selectAll();

        assertThrows(SqlExecutorException.class, selectFirst::selectAll);
        assertThrows(SqlExecutorException.class, () -> selectAllFirst.select(users.ID));
    }

    @Test
    void entityQueryShouldExecuteAllTerminalsWithoutResultTypeArguments() {
        RecordingSqlExecutor sqlExecutor = new RecordingSqlExecutor();
        DSLContext context = new DSLContext(sqlExecutor);

        TestUser one = context.from(TestUserTable.USERS).one();
        List<TestUser> list = context.from(TestUserTable.USERS).list();
        Page<TestUser> page = context.from(TestUserTable.USERS).page(1, 10);
        long count = context.from(TestUserTable.USERS).count();
        boolean exists = context.from(TestUserTable.USERS).exists();

        assertEquals(sqlExecutor.oneResult, one);
        assertEquals(sqlExecutor.listResult, list);
        assertEquals(sqlExecutor.listResult, page.records());
        assertEquals(3L, page.total());
        assertEquals(3L, count);
        assertEquals(true, exists);
    }

    @Test
    void queryWrapperShouldForwardTypeRefElementType() {
        TestUserTable users = TestUserTable.USERS;
        RecordingSqlExecutor sqlExecutor = new RecordingSqlExecutor();

        List<GenericRow<String>> result = new DSLContext(sqlExecutor)
                .select(new TypeRef<GenericRow<String>>() {
                }, users.NAME)
                .from(users)
                .list();

        assertEquals(List.of(), result);
        ParameterizedType elementType = (ParameterizedType) sqlExecutor.lastElementType;
        assertEquals(GenericRow.class, elementType.getRawType());
        assertArrayEquals(new Type[]{String.class}, elementType.getActualTypeArguments());
    }

    private static final class TestUser {
    }

    private record GenericRow<T>(T value) {
    }

    private static final class RecordingSqlExecutor implements SqlExecutor {
        private final DefaultSqlGenerator sqlGenerator = new DefaultSqlGenerator();
        private final org.byteora.kyra.orm.runtime.SqlPagingSupport sqlPagingSupport = new org.byteora.kyra.orm.runtime.SqlPagingSupport() {
            @Override
            public <T> Page<T> page(SqlExecutor sqlExecutor, org.byteora.kyra.orm.runtime.SqlExecutionContext context, String sql, Object[] args, Paging paging, Class<T> elementType) {
                return new Page<>(paging.getCurrent(), paging.getSize(), 3L, listResult.stream().map(elementType::cast).toList());
            }

            @Override
            public long count(SqlExecutor sqlExecutor, org.byteora.kyra.orm.runtime.SqlExecutionContext context, String sql, Object[] args) {
                return 3L;
            }
        };
        private final TypeConverter typeConverter = new TypeConverter();
        private final TestUser oneResult = new TestUser();
        private final List<TestUser> listResult = List.of(new TestUser(), new TestUser());
        private String lastSql;
        private Object[] lastArgs;
        private Type lastElementType;

        @Override
        public <T> T selectOne(String sql, Object[] args, Class<T> resultType) {
            this.lastSql = sql;
            this.lastArgs = args;
            return resultType.cast(oneResult);
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, Class<T> resultType) {
            this.lastSql = sql;
            this.lastArgs = args;
            if (resultType == Integer.class) {
                return List.of(resultType.cast(1));
            }
            return listResult.stream().map(resultType::cast).toList();
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, Type elementType) {
            this.lastSql = sql;
            this.lastArgs = args;
            this.lastElementType = elementType;
            if (elementType instanceof ParameterizedType) {
                return List.of();
            }
            return SqlExecutor.super.selectList(sql, args, elementType);
        }

        @Override
        public int update(String sql, Object[] args) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T updateAndReturnGeneratedKey(String sql, Object[] args, Class<T> resultType) {
            return null;
        }

        @Override
        public int[] executeBatch(String sql, List<Object[]> batchArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TypeConverter getTypeConverter() {
            return typeConverter;
        }

        @Override
        public void setTypeConverter(TypeConverter typeConverter) {
        }

        @Override
        public IdGenerator getIdGenerator() {
            return null;
        }

        @Override
        public void setIdGenerator(IdGenerator idGenerator) {

        }

        @Override
        public <T> T selectOne(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            return selectOne(sql, args, resultType);
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Class<T> resultType) {
            return selectList(sql, args, resultType);
        }

        @Override
        public <T> List<T> selectList(String sql, Object[] args, SqlExecutionContext context, Type elementType) {
            return selectList(sql, args, elementType);
        }

        @Override
        public int update(String sql, Object[] args, SqlExecutionContext context) {
            return update(sql, args);
        }

        @Override
        public int[] executeBatch(String sql, List<Object[]> batchArgs, SqlExecutionContext context) {
            return executeBatch(sql, batchArgs);
        }

        @Override
        public org.byteora.kyra.orm.runtime.SqlPagingSupport getSqlPagingSupport() {
            return sqlPagingSupport;
        }

        @Override
        public DbType getDbType() {
            return DbType.MYSQL;
        }

        @Override
        public org.byteora.kyra.orm.runtime.SqlGenerator getSqlGenerator() {
            return sqlGenerator;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> List<T> executeQuery(String sql, Object[] args, RowMapper<T> rowMapper) {
            if (sql.startsWith("select count(*)")) {
                return List.of((T) Long.valueOf(3L));
            }
            throw new UnsupportedOperationException();
        }
    }

    private static final class TestUserTable extends Table<TestUser> {
        private static final TestUserTable USERS = new TestUserTable("users", null);

        private final Column<TestUser, Long> ID = column("id", Long.class);
        private final Column<TestUser, String> NAME = column("name", String.class);
        private final Column<TestUser, Integer> AGE = column("age", Integer.class);

        private TestUserTable(String tableName, String alias) {
            super(TestUser.class, tableName, alias);
        }

        private TestUserTable alias(String alias) {
            return new TestUserTable(tableName(), alias);
        }

        @Override
        public Column<TestUser, ?> idColumn() {
            return ID;
        }

        @Override
        public String fieldName(String column) {
            return switch (column) {
                case "id" -> "id";
                case "name" -> "name";
                case "age" -> "age";
                default -> column;
            };
        }

        @Override
        public String columnName(String field) {
            return switch (field) {
                case "id" -> "id";
                case "name" -> "name";
                case "age" -> "age";
                default -> field;
            };
        }
    }
}
