package com.example.simple;

import com.example.simple.entity.BaseUser;
import com.example.simple.entity.User;
import com.example.simple.sample.inheritance.AdminUser;
import com.example.simple.sample.inheritance.TestReflectTag;
import com.example.simple.sample.reflect.AdminAuthController;
import com.example.simple.sample.reflect.CtorOnlyUser;
import com.example.simple.sample.reflect.PlatformAuthController;
import com.example.simple.sample.reflect.RecordUser;
import com.example.simple.sample.reflect.UserKind;
import com.example.simple.support.GeneratedTypeNames;
import com.example.simple.support.Reflectors;
import org.byteora.kyra.core.runtime.AnnotationMeta;
import org.byteora.kyra.core.runtime.ClassInfo;
import org.byteora.kyra.core.runtime.FieldInfo;
import org.byteora.kyra.core.runtime.Reflector;
import org.byteora.kyra.core.runtime.ReflectorRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReflectorFeaturesTest {
    @Nested
    class EntityMetadata {
        @Test
        void shouldPreserveGenericTypeMetadata() {
            Reflector<User> reflector = Reflectors.load(User.class);

            ClassInfo classInfo = reflector.getClassInfo();
            ParameterizedType superType = assertInstanceOf(ParameterizedType.class, classInfo.superType());
            assertEquals(BaseUser.class, superType.getRawType());
            assertEquals(Long.class, superType.getActualTypeArguments()[0]);

            FieldInfo ids = reflector.getField("ids");
            ParameterizedType idsType = assertInstanceOf(ParameterizedType.class, ids.type());
            assertEquals(java.util.List.class, idsType.getRawType());
            assertEquals(String.class, idsType.getActualTypeArguments()[0]);
            assertEquals(User.class, classInfo.type());
        }
    }

    @Nested
    class RecordsAndEnums {
        @Test
        void shouldSupportRecords() {
            Reflector<RecordUser> reflector = Reflectors.load(RecordUser.class);
            RecordUser user = reflector.newInstance(new Object[]{"Alice", 18});

            assertEquals("Alice", reflector.get(user, "name"));
            assertEquals(18, reflector.get(user, "age"));
            assertThrows(UnsupportedOperationException.class, () -> reflector.set(user, "name", "Bob"));
        }

        @Test
        void shouldSupportNestedRecords() {
            Reflector<PlatformAuthController.LoginRequest> reflector = Reflectors.load(PlatformAuthController.LoginRequest.class);
            PlatformAuthController.LoginRequest request = reflector.newInstance(new Object[]{"alice", "secret"});

            assertEquals(PlatformAuthController.LoginRequest.class, reflector.getClassInfo().type());
            assertEquals("alice", reflector.get(request, "username"));
            assertEquals("secret", reflector.get(request, "password"));
        }

        @Test
        void nestedRecordsWithSameSimpleNameShouldNotCollide() {
            String platformReflectorName = GeneratedTypeNames.reflectorTypeName(PlatformAuthController.LoginRequest.class);
            String adminReflectorName = GeneratedTypeNames.reflectorTypeName(AdminAuthController.LoginRequest.class);
            Reflector<AdminAuthController.LoginRequest> reflector = Reflectors.load(AdminAuthController.LoginRequest.class);
            AdminAuthController.LoginRequest request = reflector.newInstance(new Object[]{"admin"});

            assertFalse(platformReflectorName.equals(adminReflectorName));
            assertEquals(AdminAuthController.LoginRequest.class, reflector.getClassInfo().type());
            assertEquals("admin", reflector.get(request, "username"));
        }

        @Test
        void shouldSupportEnums() {
            Reflector<UserKind> reflector = Reflectors.load(UserKind.class);

            assertEquals(UserKind.class, reflector.getClassInfo().type());
            assertThrows(UnsupportedOperationException.class, reflector::newInstance);
        }
    }

    @Nested
    class Inheritance {
        @BeforeAll
        static void installReflectors() {
            ReflectorRegistry.clear();
            ReflectorRegistry.register(com.example.simple.sample.inheritance.BaseUser.class,
                    Reflectors.load(com.example.simple.sample.inheritance.BaseUser.class));
            ReflectorRegistry.register(AdminUser.class, Reflectors.load(AdminUser.class));
        }

        @Test
        void shouldExposeClassMetadataAndInheritedMembers() {
            Reflector<AdminUser> reflector = ReflectorRegistry.get(AdminUser.class);
            AdminUser user = new AdminUser();

            reflector.set(user, "id", 7L);
            reflector.set(user, "role", "admin");

            ClassInfo classInfo = reflector.getClassInfo();
            assertNotNull(classInfo);
            assertEquals(AdminUser.class, classInfo.type());
            assertEquals(com.example.simple.sample.inheritance.BaseUser.class, classInfo.superType());

            AnnotationMeta tag = Arrays.stream(classInfo.annotations())
                    .filter(annotation -> annotation.type().equals(TestReflectTag.class.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("admin", tag.value("value"));

            assertEquals(7L, reflector.get(user, "id"));
            assertEquals("admin", reflector.get(user, "role"));
            assertEquals("prefix-7", reflector.invoke(user, "baseLabel", new Object[]{"prefix-"}));

            assertNotNull(reflector.getField("id"));
            assertNotNull(reflector.getField("role"));
            assertTrue(Arrays.asList(reflector.getFields()).containsAll(List.of("id", "role")));
            assertTrue(Arrays.asList(reflector.getMethods()).contains("baseLabel"));
            assertFalse(Arrays.asList(reflector.getMethods()).contains("equals"));
            assertEquals(0, reflector.getMethod("equals").length);
        }
    }

    @Nested
    class ConstructorOnly {
        @Test
        void shouldRejectPropertyAccessWithoutSetterOrGetter() {
            Reflector<CtorOnlyUser> reflector = Reflectors.load(CtorOnlyUser.class);
            CtorOnlyUser user = reflector.newInstance();
            assertNotNull(user);

            UnsupportedOperationException setterException = assertThrows(UnsupportedOperationException.class,
                    () -> reflector.set(user, "name", "Demo"));
            UnsupportedOperationException getterException = assertThrows(UnsupportedOperationException.class,
                    () -> reflector.get(user, "name"));
            IllegalArgumentException invokeException = assertThrows(IllegalArgumentException.class,
                    () -> reflector.invoke(user, "getName", new Object[0]));

            assertEquals("No setter or field access for property: name", setterException.getMessage());
            assertEquals("No getter or field access for property: name", getterException.getMessage());
            assertEquals("Unknown method", invokeException.getMessage());
        }
    }
}
