package test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import utils.ReflectionUtils;

public class PersonMain {

  public static void main(String[] args) {
   /* Method[] allDeclaredMethods = ReflectionUtils.getAllDeclaredMethods(Person.class);
    for (Method m : allDeclaredMethods) {
      System.out.println(m);
    }*/

    Person james = new Person("james", 10);

    Method method = ReflectionUtils.findMethod(Person.class, "setAge", int.class);
    Method getAge = ReflectionUtils.findMethod(Person.class, "getAge");
//    System.out.println(getAge);
    int age = (int)ReflectionUtils.invokeMethod(getAge, james);
    System.out.println("james 나이: "+age);

    ReflectionUtils.invokeMethod(method, james, 18);
//    ReflectionUtils.invokeMethod(method, james);

    Field age1 = ReflectionUtils.findField(Person.class, "age", int.class);
    ReflectionUtils.makeAccessible(age1);
    int field = (int) ReflectionUtils.getField(age1, james);
    System.out.println(field);

    Field[] declaredFields = ReflectionUtils.getDeclaredFields(Person.class);
    Arrays.stream(declaredFields)
        .forEach(field1 ->{
          ReflectionUtils.makeAccessible(field1);
          System.out.println("field name = " + field1.getName() + ", field value = " + ReflectionUtils.getField(field1, james));
            }
        );


    System.out.println(james);
  }
}
