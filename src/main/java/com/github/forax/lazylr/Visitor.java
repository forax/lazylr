package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

public interface Visitor<V extends @Nullable Object> {
  /// Creates an [Evaluator] by inspecting the public methods of
  /// the 'visitor' using 'lookup' access.
  ///
  /// ### Terminal methods
  /// A public method named using the name of a [Terminal].
  /// The method return type must be non-`void`.
  /// If no method matches a given terminal name, `null` is returned
  /// for that terminal.
  ///
  /// ### Production methods
  /// A public method annotated with \@[ProductionName] whose value
  /// matches the name of a [Production].
  /// The method must have exactly one parameter for each
  /// non-`null` [Symbol] in the production body.
  ///
  /// ### Single-body pass-through
  /// If a production has exactly one symbol in its body and no
  /// `@ProductionName` method is found, the single argument
  /// is returned as-is without calling any method.
  ///
  /// Primitive parameter types are accepted; boxing and unboxing
  /// are handled transparently.
  ///
  /// ### Validation
  /// All public methods declared on the `visitor`'s class (excluding those
  /// inherited from [Object]) are inspected when calling 'reflect()'.
  /// A method is rejected immediately with [IllegalStateException] if:
  /// - It has no parameters,
  /// - Its return type is `void`,
  /// - It has a single parameter that is not [Terminal] and carries no
  ///   \@[ProductionName] annotation.
  ///
  /// @param <V> the type of the visitor result
  /// @param lookup the lookup to use when creating method handles; its access
  ///               rights determine which methods on `object` are reachable.
  ///               Must not be `null`.
  /// @param visitor the visitor whose public methods define the terminal and
  ///                production handlers; must not be `null`.
  /// @return a new 'Evaluator' backed by the methods of 'object'.
  /// @throws NullPointerException if 'lookup' or 'visitor' is `null`.
  /// @throws IllegalStateException if any public method on 'visitor' violates
  ///         the constraints above, or if the lookup does not have enough
  ///         access to reflect on a public method.
  static <V extends @Nullable Object> Evaluator<V> reflect(MethodHandles.Lookup lookup, Visitor<V> visitor) {
    Objects.requireNonNull(lookup);
    Objects.requireNonNull(visitor);
    var methods = visitor.getClass().getMethods();
    var productionMap = new HashMap<String, MethodHandle>();
    var terminalMap = new HashMap<String, MethodHandle>();
    for(var method : methods) {
      if (method.getDeclaringClass() == Object.class || Modifier.isStatic(method.getModifiers())) {
        continue;
      }
      if (method.getParameterCount() == 0) {
        throw new IllegalStateException("method " + method + " has no arguments");
      }
      if (method.getReturnType() == void.class) {
        throw new IllegalStateException("method " + method + " has no return type");
      }
      MethodHandle mh;
      try {
        mh = lookup.unreflect(method);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
      var productionName = method.getAnnotation(ProductionName.class);
      if (productionName != null) {
        productionMap.put(productionName.value(),
            mh.asSpreader(Object[].class, method.getParameterCount())
                .asType(MethodType.methodType(Object.class, Object.class, Object[].class)));
        continue;
      }
      if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != Terminal.class) {
        throw new IllegalStateException("terminal method " + method + " should take a single Terminal argument");
      }
      terminalMap.put(method.getName(),
          mh.asType(MethodType.methodType(Object.class, Object.class,Terminal.class)));
    }
    return new Evaluator<>() {
      @Override
      @SuppressWarnings("unchecked")
      public V evaluate(Terminal terminal) {
        var mh = terminalMap.get(terminal.name());
        if (mh == null) {
          return null;  // The Terminal has no value
        }
        try {
          return (V) mh.invokeExact((Object) visitor, terminal);
        } catch(WrongMethodTypeException | ClassCastException e) {
          throw new IllegalStateException("terminal method " + terminal.name() + " has wrong parameter type", e);
        } catch (RuntimeException | Error e) {
          throw e;
        } catch (Throwable e) {
          throw new UndeclaredThrowableException(e);
        }
      }

      @Override
      @SuppressWarnings("unchecked")
      public V evaluate(Production production, List<V> arguments) {
        var mh = productionMap.get(production.name());
        if (mh == null) {
          // A production can have no evaluator if it is a leaf node
          if (production.body().size() == 1) {
            return arguments.getFirst();
          }
          throw new IllegalStateException("production " + production.name() + " has no evaluator");
        }
        var values = arguments.stream()
            .filter(Objects::nonNull)
            .toArray();
        try {
          return (V) mh.invokeExact((Object) visitor, values);
        } catch(WrongMethodTypeException | ClassCastException e) {
          throw new IllegalStateException("production method " + production.name() +
              " has wrong parameter types, arguments " + Arrays.toString(values), e);
        } catch (RuntimeException | Error e) {
          throw e;
        } catch (Throwable e) {
          throw new UndeclaredThrowableException(e);
        }
      }
    };
  }
}
