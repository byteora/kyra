package org.byteora.kyra.orm.runtime;

import org.byteora.kyra.core.runtime.AnnotationMeta;
import org.byteora.kyra.orm.dynamic.DynamicSqlArgumentResolver;
import org.byteora.kyra.orm.dynamic.DynamicSqlNode;
import org.byteora.kyra.orm.dynamic.DynamicSqlRenderer;
import org.byteora.kyra.orm.dynamic.MapperParameters;
import org.byteora.kyra.orm.query.Page;
import org.byteora.kyra.orm.query.Paging;
import org.byteora.kyra.orm.xml.SqlCommandType;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

public final class GeneratedMapperSupport {
    public static final AnnotationMeta[] NO_ANNOTATIONS = new AnnotationMeta[0];
    public static final Object[] NO_PARAMETER_VALUES = new Object[0];

    private GeneratedMapperSupport() {
    }

    public static <T> T selectOne(SqlExecutor sqlExecutor,
                                  Class<?> mapperType,
                                  String statementId,
                                  AnnotationMeta[] mapperMethodAnnotations,
                                  DynamicSqlNode sqlNode,
                                  String[] parameterNames,
                                  Object[] parameterValues,
                                  Class<T> resultType) {
        return selectOne(sqlExecutor, mapperType, statementId, mapperMethodAnnotations, sqlNode, parameterNames, parameterValues, (Type) resultType);
    }

    public static <T> T selectOne(SqlExecutor sqlExecutor,
                                  Class<?> mapperType,
                                  String statementId,
                                  AnnotationMeta[] mapperMethodAnnotations,
                                  DynamicSqlNode sqlNode,
                                  String[] parameterNames,
                                  Object[] parameterValues,
                                  Type resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .sqlExecutor(sqlExecutor)
                .mapper(mapperType, statementId)
                .annotations(mapperMethodAnnotations)
                .build();
        return sqlExecutor.selectOne(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context, resultType);
    }

    public static <T> List<T> selectList(SqlExecutor sqlExecutor,
                                         Class<?> mapperType,
                                         String statementId,
                                         AnnotationMeta[] mapperMethodAnnotations,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Class<T> resultType) {
        return selectList(sqlExecutor, mapperType, statementId, mapperMethodAnnotations, sqlNode, parameterNames, parameterValues, (Type) resultType);
    }

    public static <T> List<T> selectList(SqlExecutor sqlExecutor,
                                         Class<?> mapperType,
                                         String statementId,
                                         AnnotationMeta[] mapperMethodAnnotations,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Type resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .sqlExecutor(sqlExecutor)
                .mapper(mapperType, statementId)
                .annotations(mapperMethodAnnotations)
                .build();
        return sqlExecutor.selectList(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context, resultType);
    }

    public static <T> Page<T> selectPage(SqlExecutor sqlExecutor,
                                         Class<?> mapperType,
                                         String statementId,
                                         AnnotationMeta[] mapperMethodAnnotations,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Paging paging,
                                         Class<T> resultType) {
        return selectPage(sqlExecutor, mapperType, statementId, mapperMethodAnnotations, sqlNode, parameterNames, parameterValues, paging, (Type) resultType);
    }

    public static <T> Page<T> selectPage(SqlExecutor sqlExecutor,
                                         Class<?> mapperType,
                                         String statementId,
                                         AnnotationMeta[] mapperMethodAnnotations,
                                         DynamicSqlNode sqlNode,
                                         String[] parameterNames,
                                         Object[] parameterValues,
                                         Paging paging,
                                         Type resultType) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        SqlExecutionContext context = SqlExecutionContext.builder(SqlCommandType.SELECT)
                .sqlExecutor(sqlExecutor)
                .mapper(mapperType, statementId)
                .paging(paging)
                .annotations(mapperMethodAnnotations)
                .build();
        return sqlExecutor.getSqlPagingSupport().page(
                sqlExecutor,
                context,
                boundSql.getSql(),
                DynamicSqlArgumentResolver.resolve(boundSql),
                paging,
                resultType
        );
    }

    public static int update(SqlExecutor sqlExecutor,
                             Class<?> mapperType,
                             String statementId,
                             AnnotationMeta[] mapperMethodAnnotations,
                             SqlCommandType commandType,
                             DynamicSqlNode sqlNode,
                             String[] parameterNames,
                             Object[] parameterValues) {
        Map<String, Object> params = MapperParameters.build(parameterNames, parameterValues);
        BoundSql boundSql = DynamicSqlRenderer.render(sqlNode, params);
        SqlExecutionContext context = SqlExecutionContext.builder(commandType)
                .sqlExecutor(sqlExecutor)
                .mapper(mapperType, statementId)
                .annotations(mapperMethodAnnotations)
                .build();
        return sqlExecutor.update(boundSql.getSql(), DynamicSqlArgumentResolver.resolve(boundSql), context);
    }

}
