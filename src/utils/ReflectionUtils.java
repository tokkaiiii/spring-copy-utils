package utils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ReflectionUtils {

  /**
   * 개발자가 명시적으로 선언한 메소드만 걸러내는 필 컴파일러가 생성한 bridge method 제외, 자바 컴파일러가 내부적으로 생성한 synthetic method 제외,
   * Object 클래스 메소드 제회
   */
  private static final MethodFilter USER_DECLARED_METHODS = (method) -> !method.isBridge()
      && !method.isSynthetic() && method.getDeclaringClass() != Object.class;
  private static final FieldFilter COPYABLE_FIELDS = (field) ->
      !Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers());
  private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];
  private static final Method[] EMPTY_METHOD_ARRAY = new Method[0];
  private static final Field[] EMPTY_FIELD_ARRAY = new Field[0];
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  private static final Map<Class<?>, Method[]> declaredMethodsCache = new ConcurrentHashMap<>(256);
  private static final Map<Class<?>, Field[]> declaredFieldsCache = new ConcurrentHashMap<>(256);

  public static void handleReflectionException(Exception ex) {
    if (ex instanceof NoSuchMethodException) {
      throw new IllegalStateException("Method not found: " + ex.getMessage());
    } else if (ex instanceof IllegalAccessException) {
      throw new IllegalStateException("Cannot access method or field: " + ex.getMessage());
    } else {
      if (ex instanceof InvocationTargetException) {
        InvocationTargetException invocationTargetException = (InvocationTargetException) ex;
        handleInvocationTargetException(invocationTargetException);
      }

      if (ex instanceof RuntimeException) {
        RuntimeException runtimeException = (RuntimeException) ex;
        throw runtimeException;
      } else {
        throw new UndeclaredThrowableException(ex);
      }
    }
  }

  public static void handleInvocationTargetException(InvocationTargetException ex) {
    rethrowRuntimeException(ex.getTargetException());
  }

  public static void rethrowRuntimeException(Throwable ex) {
    if (ex instanceof RuntimeException runtimeException) {
      throw runtimeException;
    } else if (ex instanceof Error error) {
      throw error;
    } else {
      throw new UndeclaredThrowableException(ex);
    }
  }

  public static <T> Constructor<T> accessibleConstructor(Class<T> clazz, Class<?>... parameterTypes)
      throws NoSuchMethodException {
    Constructor<T> ctor = clazz.getDeclaredConstructor(parameterTypes);
    makeAccessible(ctor);
    return ctor;
  }

  public static void makeAccessible(Constructor<?> ctor) {
    if ((!Modifier.isPublic(ctor.getModifiers()) || !Modifier.isPublic(
        ctor.getDeclaringClass().getModifiers())) && !ctor.isAccessible()) {
      ctor.setAccessible(true);
    }

  }

  public static Method findMethod(Class<?> clazz, String name) {
    return findMethod(clazz, name, EMPTY_CLASS_ARRAY);
  }

  public static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.notNull(name, "Method name must not be null");

    for (Class<?> searchType = clazz; searchType != null; searchType = searchType.getSuperclass()) {
      Method[] methods = searchType.isInterface() ? searchType.getMethods()
          : getDeclaredMethods(searchType, false);

      for (Method method : methods) {
        if (name.equals(method.getName()) && (paramTypes == null || hasSameParams(method,
            paramTypes))) {
          return method;
        }
      }
    }

    return null;
  }

  private static boolean hasSameParams(Method method, Class<?>[] paramTypes) {
    return paramTypes.length == method.getParameterCount() && Arrays.equals(paramTypes,
        method.getParameterTypes());
  }


  public static Object invokeMethod(Method method, Object target) {
    return invokeMethod(method, target, EMPTY_OBJECT_ARRAY);
  }

  public static Object invokeMethod(Method method, Object target, Object... args) {
    try {
      return method.invoke(target, args);
    } catch (Exception ex) {
      handleReflectionException(ex);
      throw new IllegalStateException("Should never get here");
    }
  }

  public static Method[] getAllDeclaredMethods(Class<?> leafClass) {
    List<Method> methods = new ArrayList<>(20);
    Objects.requireNonNull(methods);
    doWithMethods(leafClass, methods::add);
    return methods.toArray(EMPTY_METHOD_ARRAY);
  }

  public static void doWithMethods(Class<?> clazz, MethodCallback mc) {
    doWithMethods(clazz, mc, (MethodFilter) null);
  }

  public static void doWithMethods(Class<?> clazz, MethodCallback mc, MethodFilter mf) {
    if (mf != USER_DECLARED_METHODS || clazz != Object.class) {
      Method[] methods = getDeclaredMethods(clazz, false);

      for (Method method : methods) {
        if (mf == null || mf.matches(method)) {
          try {
            mc.doWith(method);
          } catch (IllegalAccessException ex) {
            String var10002 = method.getName();
            throw new IllegalStateException(
                "Not allowed to access method '" + var10002 + "': " + ex);
          }
        }
      }

      if (clazz.getSuperclass() == null
          || mf == USER_DECLARED_METHODS && clazz.getSuperclass() == Object.class) {
        if (clazz.isInterface()) {
          for (Class<?> superIfc : clazz.getInterfaces()) {
            doWithMethods(superIfc, mc, mf);
          }
        }
      } else {
        doWithMethods(clazz.getSuperclass(), mc, mf);
      }
    }
  }

  private static Method[] getDeclaredMethods(Class<?> clazz, boolean defensive) {
    Assert.notNull(clazz, "Class must not be null");
    Method[] result = declaredMethodsCache.get(clazz);
    if (result == null) {
      try {
        Method[] declaredMethods = clazz.getDeclaredMethods();
        List<Method> defaultMethods = findDefaultMethodsOnInterfaces(clazz);
        if (defaultMethods != null) {
          result = new Method[declaredMethods.length + defaultMethods.size()];
          System.arraycopy(declaredMethods, 0, result, 0, declaredMethods.length);
          int index = declaredMethods.length;

          for (Method defaultMethod : defaultMethods) {
            result[index] = defaultMethod;
            ++index;
          }
        } else {
          result = declaredMethods;
        }

        declaredMethodsCache.put(clazz, result.length == 0 ? EMPTY_METHOD_ARRAY : result);
      } catch (Throwable ex) {
        throw new IllegalStateException(
            "Failed to introspect Class [" + clazz.getName() + "] from ClassLoader ["
                + clazz.getClassLoader() + "]", ex);
      }
    }

    return result.length != 0 && defensive ? result.clone() : result;
  }

  private static List<Method> findDefaultMethodsOnInterfaces(Class<?> clazz) {
    List<Method> result = null;

    for (Class<?> ifc : clazz.getInterfaces()) {
      for (Method method : ifc.getMethods()) {
        if (method.isDefault()) {
          if (result == null) {
            result = new ArrayList<>();
          }

          result.add(method);
        }
      }
    }

    return result;
  }

  public static Field findField(Class<?> clazz, String name) {
    return findField(clazz, name, (Class) null);
  }

  public static Field findField(Class<?> clazz, String name, Class<?> type) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.isTrue(name != null || type != null,
        "Either name or type of the field must be specified");

    for (Class<?> searchType = clazz; Object.class != searchType && searchType != null;
        searchType = searchType.getSuperclass()) {
      Field[] fields = getDeclaredFields(searchType);

      for (Field field : fields) {
        if ((name == null || name.equals(field.getName())) && (type == null || type.equals(
            field.getType()))) {
          return field;
        }
      }
    }

    return null;
  }

  public static Field findFieldIgnoreCase(Class<?> clazz, String name) {
    Assert.notNull(clazz, "Class must not be null");
    Assert.notNull(name, "Name must not be null");

    for (Class<?> searchType = clazz; Object.class != searchType && searchType != null;
        searchType = searchType.getSuperclass()) {
      Field[] fields = getDeclaredFields(searchType);

      for (Field field : fields) {
        if (name.equalsIgnoreCase(field.getName())) {
          return field;
        }
      }
    }

    return null;
  }

  public static void setField(Field field, Object target, Object value) {
    try {
      field.set(target, value);
    } catch (IllegalAccessException ex) {
      handleReflectionException(ex);
    }

  }

  public static Object getField(Field field, Object target) {
    try {
      return field.get(target);
    } catch (IllegalAccessException ex) {
      handleReflectionException(ex);
      throw new IllegalStateException("Should never get here");
    }
  }

  public static void doWithLocalFields(Class<?> clazz, FieldCallback fc) {
    for (Field field : getDeclaredFields(clazz)) {
      try {
        fc.doWith(field);
      } catch (IllegalAccessException ex) {
        String var10002 = field.getName();
        throw new IllegalStateException("Not allowed to access field '" + var10002 + "': " + ex);
      }
    }

  }

  public static void doWithFields(Class<?> clazz, FieldCallback fc) {
    doWithFields(clazz, fc, (FieldFilter) null);
  }

  public static void doWithFields(Class<?> clazz, FieldCallback fc, FieldFilter ff) {
    Class<?> targetClass = clazz;

    do {
      for (Field field : getDeclaredFields(targetClass)) {
        if (ff == null || ff.matches(field)) {
          try {
            fc.doWith(field);
          } catch (IllegalAccessException ex) {
            String var10002 = field.getName();
            throw new IllegalStateException(
                "Not allowed to access field '" + var10002 + "': " + ex);
          }
        }
      }

      targetClass = targetClass.getSuperclass();
    } while (targetClass != null && targetClass != Object.class);

  }

  public static Field[] getDeclaredFields(Class<?> clazz) {
    Assert.notNull(clazz, "Class must not be null");
    Field[] result = (Field[]) declaredFieldsCache.get(clazz);
    if (result == null) {
      try {
        result = clazz.getDeclaredFields();
        declaredFieldsCache.put(clazz, result.length == 0 ? EMPTY_FIELD_ARRAY : result);
      } catch (Throwable ex) {
        throw new IllegalStateException(
            "Failed to introspect Class [" + clazz.getName() + "] from ClassLoader ["
                + clazz.getClassLoader() + "]", ex);
      }
    }

    return result;
  }

  public static void makeAccessible(Field field) {
    if ((!Modifier.isPublic(field.getModifiers()) || !Modifier.isPublic(field.getDeclaringClass().getModifiers()) || Modifier.isFinal(field.getModifiers())) && !field.isAccessible()) {
      field.setAccessible(true);
    }

  }

  @FunctionalInterface
  public interface MethodFilter {

    boolean matches(Method method);

    default MethodFilter and(MethodFilter next) {
      Assert.notNull(next, "Next MethodFilter must not be null");
      return (method) -> matches(method) && next.matches(method);
    }
  }

  @FunctionalInterface
  public interface FieldFilter {

    boolean matches(Field field);

    default FieldFilter and(FieldFilter next) {
      Assert.notNull(next, "Next FieldFilter must not be null");
      return (field) -> matches(field) && next.matches(field);
    }
  }

  @FunctionalInterface
  public interface FieldCallback {

    void doWith(Field field) throws IllegalArgumentException, IllegalAccessException;
  }

  @FunctionalInterface
  public interface MethodCallback {

    void doWith(Method method) throws IllegalArgumentException, IllegalAccessException;
  }
}
