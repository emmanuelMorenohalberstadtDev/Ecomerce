package com.ecommerce.auth.infrastructure.persistence;

import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

/**
 * Reports {@link SqlTypes#OTHER} — matching what Postgres' JDBC driver reports for the
 * {@code citext} column type, so Hibernate's {@code ddl-auto=validate} schema check agrees with
 * the real column type — while delegating actual parameter binding/reading to
 * {@link VarcharJdbcType}, since citext accepts plain string values over the wire exactly like
 * varchar. Plain {@code @JdbcTypeCode(SqlTypes.OTHER)} satisfies validation but breaks runtime
 * binding (Hibernate's generic OTHER handling expects a binary payload, not a String).
 */
public class CitextJdbcType implements JdbcType {

    public static final CitextJdbcType INSTANCE = new CitextJdbcType();

    @Override
    public int getJdbcTypeCode() {
        return SqlTypes.OTHER;
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return VarcharJdbcType.INSTANCE.getBinder(javaType);
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return VarcharJdbcType.INSTANCE.getExtractor(javaType);
    }
}
