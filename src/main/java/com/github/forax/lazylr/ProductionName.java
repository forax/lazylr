package com.github.forax.lazylr;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/// Marks a method in a [Visitor] implementation as the handler
/// for a specific [Production].
///
/// The annotation value must match the production's name exactly,
/// following the format `head : symbol1 symbol2 ...`
/// (or `head : ε` for epsilon productions).
/// This is the same string returned by [Production#name()].
///
/// ```java
/// @ProductionName("E : E + E")
/// public Node add(Node left, Node right) {
///   return new BinaryOp("+", left, right);
/// }
/// ```
///
/// The method parameters correspond to the non-`null` evaluated values
/// of the production body symbols, in left-to-right order.
/// Terminal symbols whose evaluation returned `null` (because no
/// matching terminal method was defined) are filtered out and
/// do not appear as parameters.
///
/// @see Visitor
/// @see Production#name()
@Retention(RUNTIME)
@Target(METHOD)
@Documented
@Repeatable(ProductionName.Container.class)
public @interface ProductionName {
  /// The name of the production this method handles, in the format
  /// `head : symbol1 symbol2 ...` as returned by [Production#name()].
  ///
  /// @return the production name.
  String value();

  /// Repeatable container of [ProductionName].
  @Retention(RUNTIME)
  @Target(METHOD)
  @Documented
  @interface Container {
    /// Returns the production names
    /// @return the production names
    ProductionName[] value();
  }
}