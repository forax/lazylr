package com.github.forax.lazylr;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// A typed, reflection-based alternative to [com.github.forax.lazylr.Evaluator]
/// for visiting a parse into a result of type `V`.
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
/// If no method matches a given terminal, the terminal value is ignored.
///
/// ```java
/// class NodeVisitor implements Visitor<Node> {
///   public Node num(Terminal terminal) {
///     return new NumLit(Integer.parseInt(terminal.value()));
///   }
/// ...
/// ```
///
/// ### Production methods
/// A public method annotated with \@[ProductionName] whose value
/// matches the name of a [Production] is called whenever that production
/// is reduced.
/// Parameters correspond to the evaluated values
/// of the production body symbols, in left-to-right order.
/// If a symbol is a terminal and there is no corresponding terminal method,
/// the production method has no corresponding parameter.
/// If a terminal method returns `null`, `null` is passed as argument
/// to the production method.
///
/// ```java
/// class NodeVisitor implements Visitor<Node> {
/// ...
///   @ProductionName("E : E + E")
///   public Node add(Node left, Node right) {
///     return new BinaryOp("+", left, right);
///   }
/// }
/// ```
///
/// `@ProductionName` is a repeatable annotation, a production method can
/// be annotated by more than one `@ProductionName`.
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
/// [#reflect(MethodHandles.Lookup, Visitor)] validates that:
/// - the visitor class only inherits from `java.lang.Object`.
/// - the visitor class only implements the interface `Visitor'.
/// - all public methods declared are rejected immediately with  if:
///   - its return type is `void`,
///   - it has a single parameter that is not [Terminal] and carries no [ProductionName] annotation.
///
/// Otherwise, an exception [IllegalStateException] is thrown.
///
/// If you want to share code between different visitors, use delegation, not inheritance.
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
  /// that it has enough access rights to reach the visitor's methods.
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

    record VisitorCache(Map<String, MethodHandle> terminalMap, Map<String, MethodHandle> productionMap) {
      private static final ScopedValue<MethodHandles.Lookup> SCOPED_LOOKUP = ScopedValue.newInstance();
      private static final ClassValue<VisitorCache> CACHE = new ClassValue<>() {
        @Override
        protected VisitorCache computeValue(Class<?> type) {
          checkVisitorClass(type);

          var lookup = SCOPED_LOOKUP.get();

          var methods = type.getMethods();
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
              throw new AssertionError(e);  // Access rights have been checked before
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
          return new VisitorCache(Map.copyOf(terminalMap), Map.copyOf(productionMap));
        }
      };
    }

    var visitorClass = visitor.getClass();
    try {
      lookup.accessClass(visitorClass);
    } catch (IllegalAccessException e) {
      throw new IllegalStateException(e);
    }

    var cache = ScopedValue.where(VisitorCache.SCOPED_LOOKUP, lookup)
        .call(() -> VisitorCache.CACHE.get(visitorClass));
    var terminalMap = cache.terminalMap;
    var productionMap = cache.productionMap;
    return new Evaluator<>() {
      @Override
      @SuppressWarnings("unchecked")
      public V evaluate(Terminal terminal) {
        var mh = terminalMap.get(terminal.name());
        if (mh == null) {
          return null;  // No terminal method, ignore the terminal
        }
        try {
          return (V) mh.invokeExact((Object) visitor, terminal);
        } catch(WrongMethodTypeException | ClassCastException e) {
          throw new IllegalStateException("terminal method " + terminal.name() + " has wrong parameters", e);
        } catch (RuntimeException | Error e) {
          throw e;
        } catch (Throwable e) {
          throw new UndeclaredThrowableException(e);
        }
      }

      private Object[] extractArguments(Production production, List<V> arguments) {
        var size = production.body().size();
        var array = new Object[size];
        var index = 0;
        for (var i = 0; i < size; i++) {
          if (!(production.body().get(i) instanceof Terminal terminal) || terminalMap.containsKey(terminal.name())) {
            var value = arguments.get(i);
            array[index++] = value;
          }
        }
        return index == size ? array : Arrays.copyOf(array, index);
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
          throw new IllegalStateException(missingProductionEvaluator(production, visitor));
        }
        var values = extractArguments(production, arguments);
        try {
          return (V) mh.invokeExact((Object) visitor, values);
        } catch(WrongMethodTypeException | ClassCastException e) {
          throw new IllegalStateException("production method " + production.name() +
              " has wrong parameters, arguments " + Arrays.toString(values), e);
        } catch (RuntimeException | Error e) {
          throw e;
        } catch (Throwable e) {
          throw new UndeclaredThrowableException(e);
        }
      }
    };
  }

  /// Ensure that no bridge methods can appear
  private static void checkVisitorClass(Class<?> type) {
    if (type.getSuperclass() != Object.class) {
      throw new IllegalStateException("visitor class " + type.getSuperclass() + " is not an Object");
    }
    Class<?>[] interfaces = type.getInterfaces();
    if (interfaces.length != 1 || interfaces[0] != Visitor.class) {
      throw new IllegalStateException("visitor class can only implement the interface Visitor");
    }
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

  private static Map<String, Class<?>> inferTerminals(Class<?> visitorClass) {
    var terminalMap = new HashMap<String, Class<?>>();
    for(var method : visitorClass.getMethods()) {
      var returnType = method.getReturnType();
      if (returnType == void.class) {
        continue;
      }
      var parameterTypes = method.getParameterTypes();
      if (parameterTypes.length == 1 && parameterTypes[0] == Terminal.class) {
        terminalMap.putIfAbsent(method.getName(), returnType);
      }
    }
    return terminalMap;
  }

  private static Map<String, Class<?>> inferNonTerminals(Class<?> visitorClass) {
    var nonTerminalMap = new HashMap<String, Class<?>>();
    for(var method : visitorClass.getMethods()) {
      var returnType = method.getReturnType();
      var productionNames = productionNames(method);
      for(var productionName : productionNames) {
        var name = productionName.value();
        var spaceIndex = name.indexOf(' ');
        if (spaceIndex == -1) {
          continue;   // skip malformed @ProductionName
        }
        var nonTerminalName = name.substring(0, spaceIndex);
        nonTerminalMap.putIfAbsent(nonTerminalName, returnType);
      }
    }
    return nonTerminalMap;
  }

  private static Class<?> inferFromVisitorDeclaration(Class<?> visitorClass) {
    for (var interfaze : visitorClass.getGenericInterfaces()) {
      if (interfaze instanceof ParameterizedType parameterizedType) {
        var rawClass = (Class<?>) parameterizedType.getRawType();
        if (Visitor.class.isAssignableFrom(rawClass)) {
          var argument = parameterizedType.getActualTypeArguments()[0];
          if (argument instanceof Class<?> clazz) {
            return switch (clazz.getName()) {
              case "java.lang.Boolean" -> boolean.class;
              case "java.lang.Byte" -> byte.class;
              case "java.lang.Short" -> short.class;
              case "java.lang.Character" -> char.class;
              case "java.lang.Integer" -> int.class;
              case "java.lang.Long" -> long.class;
              case "java.lang.Float" -> float.class;
              case "java.lang.Double" -> double.class;
              default -> clazz;
            };
          }
        }
      }
    }
    return Object.class;
  }

  private static String missingProductionEvaluator(Production production, Visitor<?> visitor) {
    var visitorClass = visitor.getClass();
    var terminalMap = inferTerminals(visitorClass);
    var nonTerminalMap = inferNonTerminals(visitorClass);
    var visitorType = inferFromVisitorDeclaration(visitorClass);
    var builder = new StringBuilder();
    var returnType = nonTerminalMap.getOrDefault(production.head().name(), visitorType);
    builder.append("@ProductionName(\"").append(production.name()).append("\")\n");
    builder.append("public ").append(returnType.getSimpleName()).append(" method(");
    var i = 0;
    for(var symbol : production.body()) {
      switch (symbol) {
        case Terminal terminal -> {
          var parameterType =  terminalMap.get(terminal.name());
          if (parameterType == null) {
            continue;
          }
          if (i != 0) {
            builder.append(", ");
          }
          builder.append(parameterType.getSimpleName()).append(" ").append(terminal.name());
          i++;
        }
        case NonTerminal nonTerminal -> {
          if (i != 0) {
            builder.append(", ");
          }
          var parameterType =  nonTerminalMap.getOrDefault(nonTerminal.name(), visitorType);
          builder.append(parameterType.getSimpleName()).append(" param").append(i++);
        }
      }
    }
    builder.append(") {\n  throw new UnsupportedOperationException(\"TODO\");\n}\n");

    return "production \"" + production.name() + "\" has no evaluator method,  proposed code:\n" + builder;
  }
}
