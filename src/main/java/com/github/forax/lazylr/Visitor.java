package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/// A typed, reflection-based alternative to [com.github.forax.lazylr.Evaluator]
/// for transforming a parse into a domain-specific result.
///
/// Where [Evaluator] requires a single `switch`-based dispatch method
/// per kind of symbol, `Visitor` lets you write one plain Java method
/// per terminal or production, with typed parameters and return values.
///
/// The static method [#reflect(MethodHandles.Lookup, Visitor)],
/// inspects the visitor's public methods and builds an [Evaluator]
/// from them.
///
/// ### Terminal methods
/// A public method whose name matches a terminal name is called
/// whenever that terminal is shifted.
/// The method must take exactly one [Terminal] parameter.
/// If no method matches a given terminal, `null` is returned for it.
///
/// ```java
/// public Node num(Terminal terminal) {
///   return new NumLit(Integer.parseInt(terminal.value()));
/// }
/// ```
///
/// ### Production methods
/// A public method annotated with \@[ProductionName] whose value
/// matches the name of a [Production] is called whenever that production
/// is reduced.
/// Parameters correspond to the evaluated values
/// of the production body symbols, in left-to-right order.
/// If a symbol is a terminal and has no terminal method, its value is ignored.
///
/// ```java
/// @ProductionName("E : E + E")
/// public Node add(Node left, Node right) {
///   return new BinaryOp("+", left, right);
/// }
/// ```
///
/// ### Single-body pass-through
/// If a production has exactly one symbol in its body and no `@ProductionName`
/// method is defined for it, the single argument is returned as-is without
/// calling any method. This is convenient for chain productions like `E : num`
/// that simply forward a value up the tree.
///
/// ### Primitive types in method declaration
/// Primitive types are accepted as parameter and return value and are handled
/// transparently via boxing/unboxing.
/// Methods must not return `void`.
///
/// ### Validation
/// [#reflect(MethodHandles.Lookup, Visitor)] validates all public methods declared
/// on the visitor's class (excluding those inherited from [Object]) at the time
/// it is called.
/// A public method is rejected immediately with [IllegalStateException] if:
/// - its return type is `void`,
/// - it has a single parameter that is not [Terminal] and carries no
///   [ProductionName] annotation.
///
/// ### Usage
/// The simple way to use a `Visitor` is through [MetaGrammar#parse(CharSequence, Visitor)],
/// which handles the reflection call internally:
///
/// ```java
/// var result = mg.parse(input, new NodeVisitor());
/// ```
///
/// When working directly with a [Parser], call [#reflect(MethodHandles.Lookup, Visitor)]
/// explicitly and pass the resulting [Evaluator] to [Parser#parse(java.util.Iterator, Evaluator)]:
///
/// ```java
/// var evaluator = Visitor.reflect(MethodHandles.lookup(), new NodeVisitor());
/// var result    = parser.parse(lexer.tokenize(input), evaluator);
/// ```
///
/// Note that [MethodHandles#lookup()] must be called from the same class that
/// defines (or has access to) the visitor, so that the lookup has sufficient
/// access rights to reach the visitor's methods.
///
/// @param <V> the type of value produced by the visitor.
///
/// @see Evaluator
/// @see ProductionName
/// @see MetaGrammar#parse(CharSequence, Visitor)
/// @see Parser#parse(java.util.Iterator, Evaluator)
public interface Visitor<V extends @Nullable Object> {

  // Creates an [Evaluator] by inspecting the public methods of the visitor
  /// using the given lookup.
  ///
  /// See the [Visitor] documentation above for the full rules governing
  /// terminal methods, production methods, and validation.
  ///
  /// The lookup must be obtained by the caller with [MethodHandles#lookup()] so
  /// that it has sufficient access rights to reach the visitor's methods.
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
      if (method.getReturnType() == void.class) {
        throw new IllegalStateException("method " + method + " has no return type");
      }
      MethodHandle mh;
      try {
        mh = lookup.unreflect(method);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(e);
      }
      var productionNames = productionNames(method);
      if (!productionNames.isEmpty()) {
        var target = mh.asSpreader(Object[].class, method.getParameterCount())
            .asType(MethodType.methodType(Object.class, Object.class, Object[].class));
        for(var productionName : productionNames) {
          var duplicate = productionMap.putIfAbsent(productionName.value(), target);
          if (duplicate != null) {
            throw new IllegalStateException("duplicate production name: " + productionName.value());
          }
        }
        continue;
      }
      if (method.getParameterCount() != 1 || method.getParameterTypes()[0] != Terminal.class) {
        throw new IllegalStateException("terminal method " + method + " should take a single Terminal argument");
      }
      var target = mh.asType(MethodType.methodType(Object.class, Object.class, Terminal.class));
      terminalMap.put(method.getName(), target);
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
        var values = IntStream.range(0, production.body().size())
            .filter(i ->
                !(production.body().get(i) instanceof Terminal terminal) || terminalMap.containsKey(terminal.name()))
            .mapToObj(arguments::get)
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

  private static List<ProductionName> productionNames(Method method) {
    var productionName = method.getAnnotation(ProductionName.class);
    if (productionName != null) {
      return List.of(productionName);
    }
    var productionNameContainer = method.getAnnotation(ProductionName.Container.class);
    if (productionNameContainer != null) {
      return List.of(productionNameContainer.value());
    }
    return List.of();
  }
}
