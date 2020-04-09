/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package com.appland.appmap.record;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.fail;

public class EventTemplateRegistryTest {
  @Test
  public void testRegister() {
    ArrayList<CtMethod> behaviorsToRegister = new ArrayList<CtMethod>();

    try {
      ClassPool classPool = ClassPool.getDefault();
      CtClass classType = classPool.get("com.appland.appmap.ExampleClass");

      behaviorsToRegister.add(classType.getDeclaredMethod("methodStaticZeroParam"));
      behaviorsToRegister.add(classType.getDeclaredMethod("methodStaticSingleParam"));
      behaviorsToRegister.add(classType.getDeclaredMethod("methodZeroParam"));
      behaviorsToRegister.add(classType.getDeclaredMethod("methodOneParam"));
    } catch (NotFoundException e) {
      fail(e.getMessage());
    }

    for (CtMethod method : behaviorsToRegister) {
      EventTemplateRegistry.get().register(method);
    }
  }
}
