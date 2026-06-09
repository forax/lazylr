package com.github.forax.lazylr.grammar;

import com.github.forax.lazylr.Evaluator;
import com.github.forax.lazylr.MetaGrammar;
import com.github.forax.lazylr.Parser;
import com.github.forax.lazylr.Production;
import com.github.forax.lazylr.Terminal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Pattern;

public class PythonGrammarTest {
  private static final MetaGrammar META_GRAMMAR = MetaGrammar.load("""
    precedence {
      right: IF_NO_ELSE
      right: ELSE
      right: ELIF_SHIFT
    }
    
    grammar {
    
      // -----------------------------------------------------------------
      //  Top-level entry points
      // -----------------------------------------------------------------
      file_input : statements EOF
      file_input : EOF
    
      //interactive : statement_newline

      //eval : expressions opt_newlines EOF
    
      //opt_newlines :
      //opt_newlines : opt_newlines NEWLINE

      //func_type : LPAR RPAR RARROW expression opt_newlines EOF
      //func_type : LPAR type_expressions RPAR RARROW expression opt_newlines EOF

      // -----------------------------------------------------------------
      //  Statements
      // -----------------------------------------------------------------
      statements : statement
      statements : statements statement
    
      statement : compound_stmt
      statement : simple_stmts
    
      //statement_newline : compound_stmt NEWLINE
      //statement_newline : simple_stmts
      //statement_newline : NEWLINE
      //statement_newline : EOF

      simple_stmts : simple_stmt_list opt_semi NEWLINE
      simple_stmt_list : simple_stmt
      simple_stmt_list : simple_stmt_list SEMI simple_stmt
      opt_semi :
      opt_semi : SEMI
   
      simple_stmt : assignment
      simple_stmt : type_alias
      simple_stmt : star_expressions
      simple_stmt : return_stmt
      simple_stmt : import_stmt
      simple_stmt : raise_stmt
      simple_stmt : PASS
      simple_stmt : del_stmt
      simple_stmt : yield_stmt
      simple_stmt : assert_stmt
      simple_stmt : BREAK
      simple_stmt : CONTINUE
      simple_stmt : global_stmt
      simple_stmt : nonlocal_stmt
   
      compound_stmt : function_def
      compound_stmt : if_stmt
      compound_stmt : class_def
      compound_stmt : with_stmt
      compound_stmt : for_stmt
      compound_stmt : try_stmt
      compound_stmt : while_stmt
      compound_stmt : match_stmt
   
      // -----------------------------------------------------------------
      //  Simple statements
      // -----------------------------------------------------------------
    
    
      assignment : star_expressions EQUAL annotated_rhs
      assignment : star_expressions_eq_list annotated_rhs
      assignment : name COLON expression
      assignment : name COLON expression EQUAL annotated_rhs
      assignment : LPAR single_target RPAR COLON expression
      assignment : LPAR single_target RPAR COLON expression EQUAL annotated_rhs
      assignment : single_subscript_attribute_target COLON expression
      assignment : single_subscript_attribute_target COLON expression EQUAL annotated_rhs
      assignment : single_target augassign yield_expr
      assignment : single_target augassign star_expressions
    
      // Chained assignment: a = b = expr  (two or more EQUAL-separated lhs)
      star_expressions_eq_list : star_expressions EQUAL star_expressions EQUAL
      star_expressions_eq_list : star_expressions_eq_list star_expressions EQUAL
    
      annotated_rhs : yield_expr
      annotated_rhs : star_expressions
    
      augassign : PLUSEQUAL
      augassign : MINEQUAL
      augassign : STAREQUAL
      augassign : ATEQUAL
      augassign : SLASHEQUAL
      augassign : PERCENTEQUAL
      augassign : AMPEREQUAL
      augassign : VBAREQUAL
      augassign : CIRCUMFLEXEQUAL
      augassign : LEFTSHIFTEQUAL
      augassign : RIGHTSHIFTEQUAL
      augassign : DOUBLESTAREQUAL
      augassign : DOUBLESLASHEQUAL
    
      return_stmt : RETURN
      return_stmt : RETURN star_expressions
    
      raise_stmt : RAISE
      raise_stmt : RAISE expression
      raise_stmt : RAISE expression FROM expression
    
      global_stmt : GLOBAL name_list
      name_list : name
      name_list : name_list COMMA name
    
      nonlocal_stmt : NONLOCAL name_list
    
      del_stmt : DEL del_targets
    
      yield_stmt : yield_expr
    
      assert_stmt : ASSERT expression
      assert_stmt : ASSERT expression COMMA expression
    
      import_stmt : import_name
      import_stmt : import_from
    
      import_name : IMPORT dotted_as_names
      import_from : FROM import_from_dots dotted_name IMPORT import_from_targets
      import_from : FROM import_from_dots IMPORT import_from_targets
    
      import_from_dots :
      import_from_dots : import_from_dots DOT
      import_from_dots : import_from_dots ELLIPSIS
    
      import_from_targets : LPAR import_from_as_names RPAR
      import_from_targets : LPAR import_from_as_names COMMA RPAR
      import_from_targets : import_from_as_names
      import_from_targets : STAR
    
      import_from_as_names : import_from_as_name
      import_from_as_names : import_from_as_names COMMA import_from_as_name
    
      import_from_as_name : name
      import_from_as_name : name AS name
    
      dotted_as_names : dotted_as_name
      dotted_as_names : dotted_as_names COMMA dotted_as_name
    
      dotted_as_name : dotted_name
      dotted_as_name : dotted_name AS name
    
      dotted_name : name
      dotted_name : dotted_name DOT name
    
      // -----------------------------------------------------------------
      //  Compound statements
      // -----------------------------------------------------------------
    
      block : NEWLINE INDENT statements DEDENT
      block : simple_stmts
    
      decorators : decorator
      decorators : decorators decorator
      decorator : AT named_expression NEWLINE
    
      class_def : decorators class_def_raw
      class_def : class_def_raw
    
      class_def_raw : CLASS name COLON block
      class_def_raw : CLASS name type_params COLON block
      class_def_raw : CLASS name LPAR RPAR COLON block
      class_def_raw : CLASS name LPAR arguments RPAR COLON block
      class_def_raw : CLASS name type_params LPAR RPAR COLON block
      class_def_raw : CLASS name type_params LPAR arguments RPAR COLON block
    
      function_def : decorators function_def_raw
      function_def : function_def_raw
    
      function_def_raw : DEF name LPAR RPAR COLON block
      function_def_raw : DEF name LPAR RPAR COLON func_type_comment block
      function_def_raw : DEF name LPAR params RPAR COLON block
      function_def_raw : DEF name LPAR params RPAR COLON func_type_comment block
      function_def_raw : DEF name LPAR RPAR RARROW expression COLON block
      function_def_raw : DEF name LPAR RPAR RARROW expression COLON func_type_comment block
      function_def_raw : DEF name LPAR params RPAR RARROW expression COLON block
      function_def_raw : DEF name LPAR params RPAR RARROW expression COLON func_type_comment block
      function_def_raw : DEF name type_params LPAR RPAR COLON block
      function_def_raw : DEF name type_params LPAR RPAR COLON func_type_comment block
      function_def_raw : DEF name type_params LPAR params RPAR COLON block
      function_def_raw : DEF name type_params LPAR params RPAR COLON func_type_comment block
      function_def_raw : DEF name type_params LPAR RPAR RARROW expression COLON block
      function_def_raw : DEF name type_params LPAR RPAR RARROW expression COLON func_type_comment block
      function_def_raw : DEF name type_params LPAR params RPAR RARROW expression COLON block
      function_def_raw : DEF name type_params LPAR params RPAR RARROW expression COLON func_type_comment block
      function_def_raw : ASYNC DEF name LPAR RPAR COLON block
      function_def_raw : ASYNC DEF name LPAR RPAR COLON func_type_comment block
      function_def_raw : ASYNC DEF name LPAR params RPAR COLON block
      function_def_raw : ASYNC DEF name LPAR params RPAR COLON func_type_comment block
      function_def_raw : ASYNC DEF name LPAR RPAR RARROW expression COLON block
      function_def_raw : ASYNC DEF name LPAR RPAR RARROW expression COLON func_type_comment block
      function_def_raw : ASYNC DEF name LPAR params RPAR RARROW expression COLON block
      function_def_raw : ASYNC DEF name LPAR params RPAR RARROW expression COLON func_type_comment block
      function_def_raw : ASYNC DEF name type_params LPAR RPAR COLON block
      function_def_raw : ASYNC DEF name type_params LPAR RPAR COLON func_type_comment block
      function_def_raw : ASYNC DEF name type_params LPAR params RPAR COLON block
      function_def_raw : ASYNC DEF name type_params LPAR params RPAR COLON func_type_comment block
      function_def_raw : ASYNC DEF name type_params LPAR RPAR RARROW expression COLON block
      function_def_raw : ASYNC DEF name type_params LPAR RPAR RARROW expression COLON func_type_comment block
      function_def_raw : ASYNC DEF name type_params LPAR params RPAR RARROW expression COLON block
      function_def_raw : ASYNC DEF name type_params LPAR params RPAR RARROW expression COLON func_type_comment block
    
      // -----------------------------------------------------------------
      //  Function parameters
      // -----------------------------------------------------------------
    
      params : parameters
    
      parameters : slash_no_default star_etc
      parameters : slash_no_default
      parameters : slash_no_default param_no_default_list star_etc
      parameters : slash_no_default param_no_default_list
      parameters : slash_no_default param_with_default_list star_etc
      parameters : slash_no_default param_with_default_list
      parameters : slash_no_default param_no_default_list param_with_default_list star_etc
      parameters : slash_no_default param_no_default_list param_with_default_list
      parameters : slash_with_default star_etc
      parameters : slash_with_default
      parameters : slash_with_default param_with_default_list star_etc
      parameters : slash_with_default param_with_default_list
      parameters : param_no_default_list star_etc
      parameters : param_no_default_list
      parameters : param_no_default_list param_with_default_list star_etc
      parameters : param_no_default_list param_with_default_list
      parameters : param_with_default_list star_etc
      parameters : param_with_default_list
      parameters : star_etc
    
      param_no_default_list : param_no_default
      param_no_default_list : param_no_default_list param_no_default
    
      param_with_default_list : param_with_default
      param_with_default_list : param_with_default_list param_with_default
    
      param_maybe_default_list : param_maybe_default
      param_maybe_default_list : param_maybe_default_list param_maybe_default
    
      slash_no_default : param_no_default_list SLASH COMMA
      slash_no_default : param_no_default_list SLASH
    
      slash_with_default : param_with_default_list SLASH COMMA
      slash_with_default : param_with_default_list SLASH
      slash_with_default : param_no_default_list param_with_default_list SLASH COMMA
      slash_with_default : param_no_default_list param_with_default_list SLASH
    
      star_etc : STAR param_no_default param_maybe_default_list kwds
      star_etc : STAR param_no_default param_maybe_default_list
      star_etc : STAR param_no_default kwds
      star_etc : STAR param_no_default
      star_etc : STAR param_no_default_star_annotation param_maybe_default_list kwds
      star_etc : STAR param_no_default_star_annotation param_maybe_default_list
      star_etc : STAR param_no_default_star_annotation kwds
      star_etc : STAR param_no_default_star_annotation
      star_etc : STAR COMMA param_maybe_default_list kwds
      star_etc : STAR COMMA param_maybe_default_list
      star_etc : kwds
    
      kwds : DOUBLESTAR param_no_default
    
      param_no_default : param COMMA TYPE_COMMENT
      param_no_default : param COMMA
      param_no_default : param TYPE_COMMENT
      param_no_default : param
    
      param_no_default_star_annotation : param_star_annotation COMMA TYPE_COMMENT
      param_no_default_star_annotation : param_star_annotation COMMA
      param_no_default_star_annotation : param_star_annotation TYPE_COMMENT
      param_no_default_star_annotation : param_star_annotation
    
      param_with_default : param default_assignment COMMA TYPE_COMMENT
      param_with_default : param default_assignment COMMA
      param_with_default : param default_assignment TYPE_COMMENT
      param_with_default : param default_assignment
    
      param_maybe_default : param default_assignment COMMA TYPE_COMMENT
      param_maybe_default : param default_assignment COMMA
      param_maybe_default : param default_assignment TYPE_COMMENT
      param_maybe_default : param default_assignment
      param_maybe_default : param COMMA TYPE_COMMENT
      param_maybe_default : param COMMA
      param_maybe_default : param TYPE_COMMENT
      param_maybe_default : param
    
      param : name
      param : name annotation
    
      param_star_annotation : name star_annotation
    
      annotation : COLON expression
      star_annotation : COLON star_expression
      default_assignment : EQUAL expression
    
      // -----------------------------------------------------------------
      //  If / elif / else
      // -----------------------------------------------------------------
    
      if_stmt : IF named_expression COLON block opt_else_clause
      opt_else_clause :                          %prec IF_NO_ELSE
      opt_else_clause : elif_clause
      opt_else_clause : else_block
    
      elif_clause : ELIF named_expression COLON block opt_else_clause   %prec ELIF_SHIFT
    
      else_block : ELSE COLON block
    
      // -----------------------------------------------------------------
      //  While / For / With / Try
      // -----------------------------------------------------------------
    
      while_stmt : WHILE named_expression COLON block
      while_stmt : WHILE named_expression COLON block else_block
    
      for_stmt : FOR star_expressions IN star_expressions COLON block
      for_stmt : FOR star_expressions IN star_expressions COLON block else_block
      for_stmt : FOR star_expressions IN star_expressions COLON TYPE_COMMENT block
      for_stmt : FOR star_expressions IN star_expressions COLON TYPE_COMMENT block else_block
      for_stmt : ASYNC FOR star_expressions IN star_expressions COLON block
      for_stmt : ASYNC FOR star_expressions IN star_expressions COLON block else_block
      for_stmt : ASYNC FOR star_expressions IN star_expressions COLON TYPE_COMMENT block
      for_stmt : ASYNC FOR star_expressions IN star_expressions COLON TYPE_COMMENT block else_block
    
      with_stmt : WITH LPAR with_item_list RPAR COLON block
      with_stmt : WITH LPAR with_item_list COMMA RPAR COLON block
      with_stmt : WITH LPAR with_item_list RPAR COLON TYPE_COMMENT block
      with_stmt : WITH with_item_list COLON block
      with_stmt : WITH with_item_list COLON TYPE_COMMENT block
      with_stmt : ASYNC WITH LPAR with_item_list RPAR COLON block
      with_stmt : ASYNC WITH LPAR with_item_list COMMA RPAR COLON block
      with_stmt : ASYNC WITH with_item_list COLON block
      with_stmt : ASYNC WITH with_item_list COLON TYPE_COMMENT block
    
      with_item_list : with_item
      with_item_list : with_item_list COMMA with_item
    
      with_item : expression
      with_item : expression AS star_expression
    
      try_stmt : TRY COLON block try_suffix
    
      try_suffix : finally_block
      try_suffix : except_block_list try_opt_else try_opt_finally
      try_suffix : except_star_block_list try_opt_else try_opt_finally
    
      try_opt_else :
      try_opt_else : else_block
    
      try_opt_finally :
      try_opt_finally : finally_block
    
      except_block_list : except_block
      except_block_list : except_block_list except_block
    
      except_star_block_list : except_star_block
      except_star_block_list : except_star_block_list except_star_block
    
      except_block : EXCEPT COLON block
      except_block : EXCEPT expression COLON block
      except_block : EXCEPT expression AS name COLON block
    
      except_star_block : EXCEPT STAR expression COLON block
      except_star_block : EXCEPT STAR expression AS name COLON block
    
      finally_block : FINALLY COLON block
    
      // -----------------------------------------------------------------
      //  Match statement
      // -----------------------------------------------------------------
    
      match_stmt : NAME_OR_MATCH subject_expr COLON NEWLINE INDENT case_block_list DEDENT
    
      case_block_list : case_block
      case_block_list : case_block_list case_block
    
      subject_expr : star_named_expression COMMA
      subject_expr : star_named_expression COMMA star_named_expressions
      subject_expr : named_expression
    
      case_block : NAME_OR_CASE patterns COLON block
      case_block : NAME_OR_CASE patterns guard COLON block
    
      guard : IF named_expression
    
      patterns : open_sequence_pattern
      patterns : pattern
    
      pattern : as_pattern
      pattern : or_pattern
    
      as_pattern : or_pattern AS pattern_capture_target
    
      or_pattern : closed_pattern
      or_pattern : or_pattern VBAR closed_pattern
    
      closed_pattern : literal_pattern
      closed_pattern : capture_pattern
      closed_pattern : wildcard_pattern
      closed_pattern : value_pattern
      closed_pattern : group_pattern
      closed_pattern : sequence_pattern
      closed_pattern : mapping_pattern
      closed_pattern : class_pattern
    
      literal_pattern : signed_number
      literal_pattern : complex_number
      literal_pattern : strings
      literal_pattern : NONE
      literal_pattern : TRUE
      literal_pattern : FALSE
    
      literal_expr : signed_number
      literal_expr : complex_number
      literal_expr : strings
      literal_expr : NONE
      literal_expr : TRUE
      literal_expr : FALSE
    
      complex_number : signed_real_number PLUS imaginary_number
      complex_number : signed_real_number MINUS imaginary_number
    
      signed_number : NUMBER
      signed_number : MINUS NUMBER
    
      signed_real_number : real_number
      signed_real_number : MINUS real_number
    
      real_number : NUMBER
    
      imaginary_number : NUMBER
    
      capture_pattern : pattern_capture_target
    
      pattern_capture_target : name_except_underscore
    
      wildcard_pattern : NAME_OR_WILDCARD
    
      value_pattern : attr
    
      attr : name DOT name
      attr : attr DOT name
    
      name_or_attr : name
      name_or_attr : name_or_attr DOT name
    
      group_pattern : LPAR pattern RPAR
    
      sequence_pattern : LSQB RSQB
      sequence_pattern : LSQB maybe_sequence_pattern RSQB
      sequence_pattern : LPAR RPAR
      sequence_pattern : LPAR open_sequence_pattern RPAR
    
      open_sequence_pattern : maybe_star_pattern COMMA
      open_sequence_pattern : maybe_star_pattern COMMA maybe_sequence_pattern
    
      maybe_sequence_pattern : maybe_star_pattern
      maybe_sequence_pattern : maybe_sequence_pattern COMMA maybe_star_pattern
      maybe_sequence_pattern : maybe_sequence_pattern COMMA
    
      maybe_star_pattern : star_pattern
      maybe_star_pattern : pattern
    
      star_pattern : STAR name
    
      mapping_pattern : LBRACE RBRACE
      mapping_pattern : LBRACE double_star_pattern RBRACE
      mapping_pattern : LBRACE double_star_pattern COMMA RBRACE
      mapping_pattern : LBRACE items_pattern RBRACE
      mapping_pattern : LBRACE items_pattern COMMA RBRACE
      mapping_pattern : LBRACE items_pattern COMMA double_star_pattern RBRACE
      mapping_pattern : LBRACE items_pattern COMMA double_star_pattern COMMA RBRACE
    
      items_pattern : key_value_pattern
      items_pattern : items_pattern COMMA key_value_pattern
    
      key_value_pattern : literal_expr COLON pattern
      key_value_pattern : attr COLON pattern
    
      double_star_pattern : DOUBLESTAR pattern_capture_target
    
      class_pattern : name_or_attr LPAR RPAR
      class_pattern : name_or_attr LPAR positional_patterns RPAR
      class_pattern : name_or_attr LPAR positional_patterns COMMA RPAR
      class_pattern : name_or_attr LPAR keyword_patterns RPAR
      class_pattern : name_or_attr LPAR keyword_patterns COMMA RPAR
      class_pattern : name_or_attr LPAR positional_patterns COMMA keyword_patterns RPAR
      class_pattern : name_or_attr LPAR positional_patterns COMMA keyword_patterns COMMA RPAR
    
      positional_patterns : pattern
      positional_patterns : positional_patterns COMMA pattern
    
      keyword_patterns : keyword_pattern
      keyword_patterns : keyword_patterns COMMA keyword_pattern
    
      keyword_pattern : name EQUAL pattern
    
      // -----------------------------------------------------------------
      //  Type alias & type params
      // -----------------------------------------------------------------
    
      type_alias : NAME_OR_TYPE name EQUAL expression
      type_alias : NAME_OR_TYPE name type_params EQUAL expression
    
      type_params : LSQB type_param_seq RSQB
    
      type_param_seq : type_param
      type_param_seq : type_param_seq COMMA type_param
      type_param_seq : type_param_seq COMMA
    
      type_param : name
      type_param : name type_param_bound
      type_param : name type_param_default
      type_param : name type_param_bound type_param_default
      type_param : STAR name
      type_param : STAR name type_param_starred_default
      type_param : DOUBLESTAR name
      type_param : DOUBLESTAR name type_param_default
    
      type_param_bound : COLON expression
      type_param_default : EQUAL expression
      type_param_starred_default : EQUAL star_expression
    
      // -----------------------------------------------------------------
      //  Expressions
      // -----------------------------------------------------------------
    
      //expressions : star_expression
      //expressions : expressions COMMA star_expression
      //expressions : expressions COMMA
    
      expression : disjunction
      expression : disjunction IF disjunction ELSE expression
      expression : lambdef
    
      yield_expr : YIELD
      yield_expr : YIELD FROM expression
      yield_expr : YIELD star_expressions
    
      star_expressions : star_expression
      star_expressions : star_expressions COMMA star_expression
      star_expressions : star_expressions COMMA
    
      star_expression : STAR bitwise_or
      star_expression : expression
    
      star_named_expressions : star_named_expression
      star_named_expressions : star_named_expressions COMMA star_named_expression
    
      star_named_expression : STAR bitwise_or
      star_named_expression : named_expression
    
      assignment_expression : name COLONEQUAL expression
    
      named_expression : assignment_expression
      named_expression : expression
    
      disjunction : conjunction
      disjunction : disjunction OR conjunction
    
      conjunction : inversion
      conjunction : conjunction AND inversion
    
      inversion : NOT inversion
      inversion : comparison
    
      comparison : bitwise_or
      comparison : comparison compare_op_bitwise_or_pair
    
      compare_op_bitwise_or_pair : EQEQUAL bitwise_or
      compare_op_bitwise_or_pair : NOTEQUAL bitwise_or
      compare_op_bitwise_or_pair : LESSEQUAL bitwise_or
      compare_op_bitwise_or_pair : LESS bitwise_or
      compare_op_bitwise_or_pair : GREATEREQUAL bitwise_or
      compare_op_bitwise_or_pair : GREATER bitwise_or
      compare_op_bitwise_or_pair : NOT IN bitwise_or
      compare_op_bitwise_or_pair : IN bitwise_or
      compare_op_bitwise_or_pair : IS NOT bitwise_or
      compare_op_bitwise_or_pair : IS bitwise_or
    
      bitwise_or : bitwise_xor
      bitwise_or : bitwise_or VBAR bitwise_xor
    
      bitwise_xor : bitwise_and
      bitwise_xor : bitwise_xor CIRCUMFLEX bitwise_and
    
      bitwise_and : shift_expr
      bitwise_and : bitwise_and AMPER shift_expr
    
      shift_expr : sum
      shift_expr : shift_expr LEFTSHIFT sum
      shift_expr : shift_expr RIGHTSHIFT sum
    
      sum : term
      sum : sum PLUS term
      sum : sum MINUS term
    
      term : factor
      term : term STAR factor
      term : term SLASH factor
      term : term DOUBLESLASH factor
      term : term PERCENT factor
      term : term AT factor
    
      factor : PLUS factor
      factor : MINUS factor
      factor : TILDE factor
      factor : power
    
      power : await_primary
      power : await_primary DOUBLESTAR factor
    
      await_primary : AWAIT primary
      await_primary : primary
    
      primary : atom
      primary : primary DOT name
      primary : primary genexp
      primary : primary LPAR RPAR
      primary : primary LPAR arguments RPAR
      primary : primary LSQB slices RSQB
    
      slices : slice
      slices : slices COMMA slice
      slices : slices COMMA
      slices : starred_expression
      slices : slices COMMA starred_expression
    
      slice : expression
      slice : COLON
      slice : COLON expression
      slice : expression COLON
      slice : expression COLON expression
      slice : COLON COLON expression
      slice : COLON expression COLON
      slice : COLON expression COLON expression
      slice : expression COLON COLON expression
      slice : expression COLON expression COLON
      slice : expression COLON expression COLON expression
    
      atom : name
      atom : TRUE
      atom : FALSE
      atom : NONE
      atom : strings
      atom : NUMBER
      atom : tuple
      atom : group
      atom : genexp
      atom : list
      atom : listcomp
      atom : dict
      atom : set
      atom : dictcomp
      atom : setcomp
      atom : ELLIPSIS
    
      group : LPAR yield_expr RPAR
      group : LPAR named_expression RPAR
    
      // -----------------------------------------------------------------
      //  Lambda
      // -----------------------------------------------------------------
    
      lambdef : LAMBDA COLON expression
      lambdef : LAMBDA lambda_params COLON expression
    
      lambda_params : lambda_parameters
    
      lambda_parameters : lambda_slash_no_default lambda_star_etc
      lambda_parameters : lambda_slash_no_default
      lambda_parameters : lambda_slash_no_default lambda_param_no_default_list lambda_star_etc
      lambda_parameters : lambda_slash_no_default lambda_param_no_default_list
      lambda_parameters : lambda_slash_no_default lambda_param_with_default_list lambda_star_etc
      lambda_parameters : lambda_slash_no_default lambda_param_with_default_list
      lambda_parameters : lambda_slash_no_default lambda_param_no_default_list lambda_param_with_default_list lambda_star_etc
      lambda_parameters : lambda_slash_no_default lambda_param_no_default_list lambda_param_with_default_list
      lambda_parameters : lambda_slash_with_default lambda_star_etc
      lambda_parameters : lambda_slash_with_default
      lambda_parameters : lambda_slash_with_default lambda_param_with_default_list lambda_star_etc
      lambda_parameters : lambda_slash_with_default lambda_param_with_default_list
      lambda_parameters : lambda_param_no_default_list lambda_star_etc
      lambda_parameters : lambda_param_no_default_list
      lambda_parameters : lambda_param_no_default_list lambda_param_with_default_list lambda_star_etc
      lambda_parameters : lambda_param_no_default_list lambda_param_with_default_list
      lambda_parameters : lambda_param_with_default_list lambda_star_etc
      lambda_parameters : lambda_param_with_default_list
      lambda_parameters : lambda_star_etc
    
      lambda_param_no_default_list : lambda_param_no_default
      lambda_param_no_default_list : lambda_param_no_default_list lambda_param_no_default
    
      lambda_param_with_default_list : lambda_param_with_default
      lambda_param_with_default_list : lambda_param_with_default_list lambda_param_with_default
    
      lambda_param_maybe_default_list : lambda_param_maybe_default
      lambda_param_maybe_default_list : lambda_param_maybe_default_list lambda_param_maybe_default
    
      lambda_slash_no_default : lambda_param_no_default_list SLASH COMMA
      lambda_slash_no_default : lambda_param_no_default_list SLASH
    
      lambda_slash_with_default : lambda_param_with_default_list SLASH COMMA
      lambda_slash_with_default : lambda_param_with_default_list SLASH
      lambda_slash_with_default : lambda_param_no_default_list lambda_param_with_default_list SLASH COMMA
      lambda_slash_with_default : lambda_param_no_default_list lambda_param_with_default_list SLASH
    
      lambda_star_etc : STAR lambda_param_no_default lambda_param_maybe_default_list lambda_kwds
      lambda_star_etc : STAR lambda_param_no_default lambda_param_maybe_default_list
      lambda_star_etc : STAR lambda_param_no_default lambda_kwds
      lambda_star_etc : STAR lambda_param_no_default
      lambda_star_etc : STAR COMMA lambda_param_maybe_default_list lambda_kwds
      lambda_star_etc : STAR COMMA lambda_param_maybe_default_list
      lambda_star_etc : lambda_kwds
    
      lambda_kwds : DOUBLESTAR lambda_param_no_default
    
      lambda_param_no_default : lambda_param COMMA
      lambda_param_no_default : lambda_param
    
      lambda_param_with_default : lambda_param default_assignment COMMA
      lambda_param_with_default : lambda_param default_assignment
    
      lambda_param_maybe_default : lambda_param default_assignment COMMA
      lambda_param_maybe_default : lambda_param default_assignment
      lambda_param_maybe_default : lambda_param COMMA
      lambda_param_maybe_default : lambda_param
    
      lambda_param : name
    
      // -----------------------------------------------------------------
      //  Literals & strings
      // -----------------------------------------------------------------
    
      fstring_middle : fstring_replacement_field
      fstring_middle : FSTRING_MIDDLE
    
      fstring_replacement_field : LBRACE annotated_rhs RBRACE
      fstring_replacement_field : LBRACE annotated_rhs EQUAL RBRACE
      fstring_replacement_field : LBRACE annotated_rhs fstring_conversion RBRACE
      fstring_replacement_field : LBRACE annotated_rhs EQUAL fstring_conversion RBRACE
      fstring_replacement_field : LBRACE annotated_rhs fstring_full_format_spec RBRACE
      fstring_replacement_field : LBRACE annotated_rhs EQUAL fstring_full_format_spec RBRACE
      fstring_replacement_field : LBRACE annotated_rhs fstring_conversion fstring_full_format_spec RBRACE
      fstring_replacement_field : LBRACE annotated_rhs EQUAL fstring_conversion fstring_full_format_spec RBRACE
    
      fstring_conversion : EXCLAMATION name
    
      fstring_full_format_spec : COLON fstring_format_spec_list
      fstring_format_spec_list :
      fstring_format_spec_list : fstring_format_spec_list fstring_format_spec
    
      fstring_format_spec : FSTRING_MIDDLE
      fstring_format_spec : fstring_replacement_field
    
      fstring : FSTRING_START FSTRING_END
      fstring : FSTRING_START fstring_middle_list FSTRING_END
      fstring_middle_list : fstring_middle
      fstring_middle_list : fstring_middle_list fstring_middle
    
      string_item : fstring
      string_item : STRING
    
      strings : string_item
      strings : strings string_item
    
      list : LSQB RSQB
      list : LSQB star_named_expressions RSQB
    
      tuple : LPAR RPAR
      tuple : LPAR star_named_expression COMMA RPAR
      tuple : LPAR star_named_expression COMMA star_named_expressions RPAR
    
      set : LBRACE star_named_expressions RBRACE
    
      dict : LBRACE RBRACE
      dict : LBRACE double_starred_kvpairs RBRACE
    
      double_starred_kvpairs : double_starred_kvpair
      double_starred_kvpairs : double_starred_kvpairs COMMA double_starred_kvpair
      double_starred_kvpairs : double_starred_kvpairs COMMA
    
      double_starred_kvpair : DOUBLESTAR bitwise_or
      double_starred_kvpair : kvpair
    
      kvpair : expression COLON expression
    
      // -----------------------------------------------------------------
      //  Comprehensions & generators
      // -----------------------------------------------------------------
    
      for_if_clauses : for_if_clause
      for_if_clauses : for_if_clauses for_if_clause
    
      for_if_clause : FOR star_expressions IN disjunction
      for_if_clause : FOR star_expressions IN disjunction if_list
      for_if_clause : ASYNC FOR star_expressions IN disjunction
      for_if_clause : ASYNC FOR star_expressions IN disjunction if_list
    
      if_list : IF disjunction
      if_list : if_list IF disjunction
    
      listcomp : LSQB named_expression for_if_clauses RSQB
    
      setcomp : LBRACE named_expression for_if_clauses RBRACE
    
      genexp : LPAR assignment_expression for_if_clauses RPAR
      genexp : LPAR expression for_if_clauses RPAR
    
      dictcomp : LBRACE kvpair for_if_clauses RBRACE
    
      // -----------------------------------------------------------------
      //  Function call arguments
      // -----------------------------------------------------------------
    
      arguments : args
      arguments : args COMMA
    
      args : starred_expression
      args : assignment_expression
      args : expression
      args : args COMMA starred_expression
      args : args COMMA assignment_expression
      args : args COMMA expression
      args : args COMMA kwargs
      args : kwargs
    
      kwargs : kwarg_or_starred
      kwargs : kwarg_or_double_starred
      kwargs : kwargs COMMA kwarg_or_starred
      kwargs : kwargs COMMA kwarg_or_double_starred
    
      starred_expression : STAR expression
    
      kwarg_or_starred : name EQUAL expression
      kwarg_or_starred : starred_expression
    
      kwarg_or_double_starred : name EQUAL expression
      kwarg_or_double_starred : DOUBLESTAR expression
    
      // -----------------------------------------------------------------
      //  Assignment targets
      // -----------------------------------------------------------------
    
      single_target : single_subscript_attribute_target
      single_target : name
      single_target : LPAR single_target RPAR
    
      single_subscript_attribute_target : primary DOT name
      single_subscript_attribute_target : primary LSQB slices RSQB
    
      del_stmt : DEL star_expressions
    
      // -----------------------------------------------------------------
      //  Typing elements
      // -----------------------------------------------------------------
   
      //type_expressions : expression
      //type_expressions : type_expressions COMMA expression
      //type_expressions : type_expressions COMMA STAR expression
      //type_expressions : type_expressions COMMA DOUBLESTAR expression
      //type_expressions : type_expressions COMMA STAR expression COMMA DOUBLESTAR expression
      //type_expressions : STAR expression
      //type_expressions : STAR expression COMMA DOUBLESTAR expression
      //type_expressions : DOUBLESTAR expression
   
      func_type_comment : NEWLINE TYPE_COMMENT
      func_type_comment : TYPE_COMMENT
    
      // -----------------------------------------------------------------
      //  Name (soft keywords + hard keywords + NAME)
      // -----------------------------------------------------------------
    
      name_except_underscore : NAME
      name_except_underscore : NAME_OR_TYPE
      name_except_underscore : NAME_OR_MATCH
      name_except_underscore : NAME_OR_CASE
    
      name : NAME_OR_WILDCARD
      name : name_except_underscore
    }
    """);

  {
    //META_GRAMMAR.verify();
  }

  private static final class PythonLexer {

    private enum TokenType {
      // Soft keywords (placeholders)
      TYPE_COMMENT, INDENT, DEDENT, ENCODING,
      FSTRING_START, FSTRING_MIDDLE, FSTRING_END,

      // Keywords
      FALSE, AWAIT, ELSE, IMPORT, PASS, NONE, BREAK, EXCEPT, IN, RAISE,
      TRUE, CLASS, FINALLY, IS, RETURN, AND, CONTINUE, FOR, LAMBDA, TRY,
      AS, DEF, FROM, NONLOCAL, WHILE, ASSERT, DEL, GLOBAL, NOT, WITH,
      ASYNC, ELIF, IF, OR, YIELD,

      // Soft keywords
      NAME_OR_TYPE, NAME_OR_MATCH, NAME_OR_CASE, NAME_OR_WILDCARD,

      // Identifiers
      NAME,

      // Literals
      NUMBER, STRING,

      // Special
      NEWLINE, EOF,

      // Operators and punctuation
      ELLIPSIS, DOUBLESTAREQUAL, DOUBLESLASHEQUAL, LEFTSHIFTEQUAL,
      RIGHTSHIFTEQUAL, DOUBLESTAR, DOUBLESLASH, COLONEQUAL, RARROW,
      EQEQUAL, NOTEQUAL, LESSEQUAL, GREATEREQUAL, LEFTSHIFT, RIGHTSHIFT,
      ATEQUAL, STAREQUAL, SLASHEQUAL, PERCENTEQUAL, AMPEREQUAL, VBAREQUAL,
      CIRCUMFLEXEQUAL, PLUSEQUAL, MINEQUAL,

      // Single-char operators
      LPAR, RPAR, LSQB, RSQB, LBRACE, RBRACE, DOT, COLON, COMMA, SEMI,
      PLUS, MINUS, STAR, SLASH, VBAR, AMPER, LESS, GREATER, EQUAL,
      PERCENT, TILDE, CIRCUMFLEX, AT, EXCLAMATION
    }

    public record Token(TokenType type, String value, int line, int column) {
      @Override
      public String toString() {
        return String.format("Token(%s, '%s', line=%d, col=%d)", type, value, line, column);
      }
    }

    private static final HashMap<String, TokenType> KEYWORDS = new HashMap<>();
    private static final HashMap<String, TokenType> SOFT_KEYWORDS = new HashMap<>();

    static {
      KEYWORDS.put("False", TokenType.FALSE);
      KEYWORDS.put("await", TokenType.AWAIT);
      KEYWORDS.put("else", TokenType.ELSE);
      KEYWORDS.put("import", TokenType.IMPORT);
      KEYWORDS.put("pass", TokenType.PASS);
      KEYWORDS.put("None", TokenType.NONE);
      KEYWORDS.put("break", TokenType.BREAK);
      KEYWORDS.put("except", TokenType.EXCEPT);
      KEYWORDS.put("in", TokenType.IN);
      KEYWORDS.put("raise", TokenType.RAISE);
      KEYWORDS.put("True", TokenType.TRUE);
      KEYWORDS.put("class", TokenType.CLASS);
      KEYWORDS.put("finally", TokenType.FINALLY);
      KEYWORDS.put("is", TokenType.IS);
      KEYWORDS.put("return", TokenType.RETURN);
      KEYWORDS.put("and", TokenType.AND);
      KEYWORDS.put("continue", TokenType.CONTINUE);
      KEYWORDS.put("for", TokenType.FOR);
      KEYWORDS.put("lambda", TokenType.LAMBDA);
      KEYWORDS.put("try", TokenType.TRY);
      KEYWORDS.put("as", TokenType.AS);
      KEYWORDS.put("def", TokenType.DEF);
      KEYWORDS.put("from", TokenType.FROM);
      KEYWORDS.put("nonlocal", TokenType.NONLOCAL);
      KEYWORDS.put("while", TokenType.WHILE);
      KEYWORDS.put("assert", TokenType.ASSERT);
      KEYWORDS.put("del", TokenType.DEL);
      KEYWORDS.put("global", TokenType.GLOBAL);
      KEYWORDS.put("not", TokenType.NOT);
      KEYWORDS.put("with", TokenType.WITH);
      KEYWORDS.put("async", TokenType.ASYNC);
      KEYWORDS.put("elif", TokenType.ELIF);
      KEYWORDS.put("if", TokenType.IF);
      KEYWORDS.put("or", TokenType.OR);
      KEYWORDS.put("yield", TokenType.YIELD);

      // Soft keywords
      SOFT_KEYWORDS.put("type", TokenType.NAME_OR_TYPE);
      SOFT_KEYWORDS.put("match", TokenType.NAME_OR_MATCH);
      SOFT_KEYWORDS.put("case", TokenType.NAME_OR_CASE);
      SOFT_KEYWORDS.put("_", TokenType.NAME_OR_WILDCARD);
    }

    private static final LinkedHashMap<String, TokenType> OPERATORS = new LinkedHashMap<>();
    static {
      OPERATORS.put("...", TokenType.ELLIPSIS);
      OPERATORS.put("**=", TokenType.DOUBLESTAREQUAL);
      OPERATORS.put("//=", TokenType.DOUBLESLASHEQUAL);
      OPERATORS.put("<<=", TokenType.LEFTSHIFTEQUAL);
      OPERATORS.put(">>=", TokenType.RIGHTSHIFTEQUAL);
      OPERATORS.put("**", TokenType.DOUBLESTAR);
      OPERATORS.put("//", TokenType.DOUBLESLASH);
      OPERATORS.put(":=", TokenType.COLONEQUAL);
      OPERATORS.put("->", TokenType.RARROW);
      OPERATORS.put("==", TokenType.EQEQUAL);
      OPERATORS.put("!=", TokenType.NOTEQUAL);
      OPERATORS.put("<=", TokenType.LESSEQUAL);
      OPERATORS.put(">=", TokenType.GREATEREQUAL);
      OPERATORS.put("<<", TokenType.LEFTSHIFT);
      OPERATORS.put(">>", TokenType.RIGHTSHIFT);
      OPERATORS.put("@=", TokenType.ATEQUAL);
      OPERATORS.put("*=", TokenType.STAREQUAL);
      OPERATORS.put("/=", TokenType.SLASHEQUAL);
      OPERATORS.put("%=", TokenType.PERCENTEQUAL);
      OPERATORS.put("&=", TokenType.AMPEREQUAL);
      OPERATORS.put("|=", TokenType.VBAREQUAL);
      OPERATORS.put("^=", TokenType.CIRCUMFLEXEQUAL);
      OPERATORS.put("+=", TokenType.PLUSEQUAL);
      OPERATORS.put("-=", TokenType.MINEQUAL);
      OPERATORS.put("(", TokenType.LPAR);
      OPERATORS.put(")", TokenType.RPAR);
      OPERATORS.put("[", TokenType.LSQB);
      OPERATORS.put("]", TokenType.RSQB);
      OPERATORS.put("{", TokenType.LBRACE);
      OPERATORS.put("}", TokenType.RBRACE);
      OPERATORS.put(".", TokenType.DOT);
      OPERATORS.put(":", TokenType.COLON);
      OPERATORS.put(",", TokenType.COMMA);
      OPERATORS.put(";", TokenType.SEMI);
      OPERATORS.put("+", TokenType.PLUS);
      OPERATORS.put("-", TokenType.MINUS);
      OPERATORS.put("*", TokenType.STAR);
      OPERATORS.put("/", TokenType.SLASH);
      OPERATORS.put("|", TokenType.VBAR);
      OPERATORS.put("&", TokenType.AMPER);
      OPERATORS.put("<", TokenType.LESS);
      OPERATORS.put(">", TokenType.GREATER);
      OPERATORS.put("=", TokenType.EQUAL);
      OPERATORS.put("%", TokenType.PERCENT);
      OPERATORS.put("~", TokenType.TILDE);
      OPERATORS.put("^", TokenType.CIRCUMFLEX);
      OPERATORS.put("@", TokenType.AT);
      OPERATORS.put("!", TokenType.EXCLAMATION);
    }

    // Regex patterns
    private static final Pattern NUMBER_PATTERN = Pattern.compile(
        "0[xX][0-9a-fA-F]+[nN]?|0[bB][01]+[nN]?|0[oO][0-7]+[nN]?|"
            + "[0-9]+[jJ]|[0-9]+\\.[0-9]*(?:[eE][+-]?[0-9]+)?[jJ]?|"
            + "\\.[0-9]+(?:[eE][+-]?[0-9]+)?[jJ]?|"
            + "[0-9]+(?:[eE][+-]?[0-9]+)[jJ]?|[0-9]+[nN]?");
    private static final Pattern STRING_PATTERN = Pattern.compile(
        "(?:[bBuUrRfF]|[rR][bBfF]|[bBfF][rR])?(?:\"\"\"(?:[^\\\\]|\\\\.)*?\"\"\"|"
            + "'''(?:[^\\\\]|\\\\.)*?'''|"
            + "\"(?:[^\\\\\\n\"]|\\\\.)*\"|'(?:[^\\\\\\n']|\\\\.)*')");
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern NEWLINE_PATTERN = Pattern.compile("[\\r]?\\n");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[ \\t\\f]+");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("#[^\\r\\n]*");
    private static final Pattern LINE_CONT_PATTERN = Pattern.compile("\\\\[\\r]?\\n");

    // Indentation tracking
    private static final class IndentStack {
      private final ArrayDeque<Integer> indents;

      private IndentStack() {
        indents = new ArrayDeque<>();
        super();
        indents.push(0); // Start with 0 indentation
      }

      public List<Token> handleIndentation(int column, int line) {
        var tokens = new ArrayList<Token>();
        var currentIndent = (int) indents.peek();
        if (column > currentIndent) {
          indents.push(column);
          tokens.add(new Token(TokenType.INDENT, "", line, column));
        } else if (column < currentIndent) {
          while (column < indents.peek()) {
            indents.pop();
            tokens.add(new Token(TokenType.DEDENT, "", line, column));
          }
          if (column != indents.peek()) {
            throw new RuntimeException("Inconsistent indentation at line " + line);
          }
        }
        return tokens;
      }

      public List<Token> handleEOF(int line) {
        var tokens = new ArrayList<Token>();
        while (indents.size() > 1) {
          indents.pop();
          tokens.add(new Token(TokenType.DEDENT, "", line, 0));
        }
        return tokens;
      }
    }

    private final String input;
    private int position;
    private int line;
    private int column;
    private final IndentStack indentStack;
    private boolean atLineStart;
    private int parenDepth;

    private PythonLexer(String input) {
      this.input = input;
      this.line = 1;
      this.column = 1;
      this.indentStack = new IndentStack();
      this.atLineStart = true;
      super();
    }

    public List<Token> tokenize() {
      var tokens = new ArrayList<Token>();
      while (position < input.length()) {
        if (atLineStart) {
          // Handle indentation
          var indent = skipWhitespace();
          if (position >= input.length()) {
            break;
          }

          // Skip empty lines
          if (input.charAt(position) == '\n' || input.charAt(position) == '\r') {
            skipNewline();
            continue;
          }

          // Skip comments
          if (input.charAt(position) == '#') {
            skipComment();
            if (position < input.length() && (input.charAt(position) == '\n' || input.charAt(position) == '\r')) {
              skipNewline();
            }
            continue;
          }

          // Check for line continuation
          if (input.charAt(position) == '\\') {
            skipLineContinuation();
            continue;
          }

          // Handle indentation only if not inside parentheses
          if (parenDepth == 0) {
            tokens.addAll(indentStack.handleIndentation(indent, line));
          }

          atLineStart = false;
        }

        // Skip whitespace
        skipWhitespace();

        if (position >= input.length()) {
          break;
        }

        var ch = input.charAt(position);

        // Handle newlines
        if (ch == '\n' || ch == '\r') {
          skipNewline();
          if (parenDepth == 0) {
            tokens.add(new Token(TokenType.NEWLINE, "\\n", line - 1, column));
          }
          atLineStart = true;
          continue;
        }

        // Handle comments
        if (ch == '#') {
          skipComment();
          continue;
        }

        // Handle line continuation
        if (ch == '\\') {
          skipLineContinuation();
          continue;
        }

        // Handle strings
        if (ch == '"' || ch == '\'') {
          var string = readString();
          tokens.add(new Token(TokenType.STRING, string, line, column - string.length()));
          continue;
        }

        // f-strings and other prefixed strings all go through readString via STRING_PATTERN
        if ((ch == 'f' || ch == 'F' || ch == 'b' || ch == 'B' || ch == 'r' || ch == 'R' || ch == 'u' || ch == 'U')
            && position + 1 < input.length()) {
          var matcher = STRING_PATTERN.matcher(input.substring(position));
          if (matcher.lookingAt()) {
            var string = readString();
            tokens.add(new Token(TokenType.STRING, string, line, column - string.length()));
            continue;
          }
        }

        // Handle numbers
        if (Character.isDigit(ch) || (ch == '.' && position + 1 < input.length() && Character.isDigit(input.charAt(position + 1)))) {
          var number = readNumber();
          tokens.add(new Token(TokenType.NUMBER, number, line, column - number.length()));
          continue;
        }

        // Handle identifiers and keywords
        if (Character.isLetter(ch) || ch == '_') {
          var name = readName();
          var type = getIdentifierType(name);
          tokens.add(new Token(type, name, line, column - name.length()));
          continue;
        }

        // Handle operators and punctuation
        var operator = readOperator();
        if (operator != null) {
          var type = OPERATORS.get(operator);
          if (type == TokenType.LPAR || type == TokenType.LSQB || type == TokenType.LBRACE) {
            parenDepth++;
          } else if (type == TokenType.RPAR || type == TokenType.RSQB || type == TokenType.RBRACE) {
            parenDepth = Math.max(0, parenDepth - 1);
          }
          tokens.add(new Token(type, operator, line, column - operator.length()));
          continue;
        }

        // Skip unrecognized characters
        position++;
        column++;
      }

      // Handle final DEDENT and EOF
      tokens.addAll(indentStack.handleEOF(line));
      tokens.add(new Token(TokenType.EOF, "", line, column));
      return tokens;
    }

    private int skipWhitespace() {
      var matcher = WHITESPACE_PATTERN.matcher(input.substring(position));
      if (matcher.lookingAt()) {
        var whitespace = matcher.group();
        position += whitespace.length();
        column += whitespace.length();
        return whitespace.length();
      }
      return 0;
    }

    private void skipNewline() {
      var matcher = NEWLINE_PATTERN.matcher(input.substring(position));
      if (matcher.lookingAt()) {
        var newline = matcher.group();
        position += newline.length();
        line++;
        column = 1;
      }
    }

    private void skipComment() {
      var matcher = COMMENT_PATTERN.matcher(input.substring(position));
      if (matcher.lookingAt()) {
        position += matcher.group().length();
        column += matcher.group().length();
      }
    }

    private void skipLineContinuation() {
      var matcher = LINE_CONT_PATTERN.matcher(input.substring(position));
      if (matcher.lookingAt()) {
        position += matcher.group().length();
        line++;
        column = 1;
      }
    }

    private String readString() {
      var matcher = STRING_PATTERN.matcher(input.substring(position));
      if (matcher.lookingAt()) {
        var string = matcher.group();
        position += string.length();
        // Count lines in multi-line strings
        for (char c : string.toCharArray()) {
          if (c == '\n') {
            line++;
            column = 1;
          } else {
            column++;
          }
        }
        return string;
      }
      return "";
    }

    private String readNumber() {
      var matcher = NUMBER_PATTERN.matcher(input.substring(position));
      if (matcher.lookingAt()) {
        var number = matcher.group();
        position += number.length();
        column += number.length();
        return number;
      }
      return "";
    }

    private String readName() {
      var matcher = NAME_PATTERN.matcher(input.substring(position));
      if (matcher.lookingAt()) {
        var name = matcher.group();
        position += name.length();
        column += name.length();
        return name;
      }
      return "";
    }

    private @Nullable String readOperator() {
      var remaining = input.substring(position);
      for (var operator : OPERATORS.keySet()) {
        if (remaining.startsWith(operator)) {
          position += operator.length();
          column += operator.length();
          return operator;
        }
      }
      return null;
    }

    private TokenType getIdentifierType(String name) {
      var keywordType = KEYWORDS.get(name);
      if (keywordType != null) {
        return keywordType;
      }
      // Check soft keywords
      var softKeywordType = SOFT_KEYWORDS.get(name);
      if (softKeywordType != null) {
        return softKeywordType;
      }
      return TokenType.NAME;
    }

    public static Iterator<Terminal> tokenize(String source) {
      var lexer = new PythonLexer(source);
      var tokens = lexer.tokenize();
      var iterator = tokens.iterator();
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public Terminal next() {
          var token = iterator.next();
          return new Terminal(token.type.name(), token.value);
        }
      };
    }
  }

  private static void parse(String source) {
    if (!source.endsWith("\n")) {
      source += '\n';
    }
    var input = PythonLexer.tokenize(source);
    var parser = Parser.createParser(META_GRAMMAR.grammar(), META_GRAMMAR.precedenceMap());
    parser.parse(input, new Evaluator<@Nullable Object>() {
      @Override
      public @Nullable Object evaluate(Terminal terminal) {
        return null;
      }

      @Override
      public @Nullable Object evaluate(Production production, List<@Nullable Object> arguments) {
        return null;
      }
    });
  }

  @Nested
  public class LiteralsAndExpressions {
    @Test
    public void testNumericLiterals() {
      parse("42");
      parse("3.14159");
      parse("1e-9");
      parse("0b1010");
      parse("0x7f");
      parse("3 + 4j");
    }

    @Test
    public void testStringLiterals() {
      parse("'single quotes'");
      parse("""
        "double quotes"
        """);
      parse("\"\"\"triple double quotes\"\"\"");
      parse("""
          '''triple single
          multi-line'''
          """);
    }

    @Test
    public void testFStringLiterals() {
      parse("f'f-string {variable}'");
      parse("r'raw string\\n'");
    }

    @Test
    public void pass() {
      parse("pass");
    }

    @Test
    public void testEllipsis() {
      parse("...");
    }

    @Test
    public void testCollections() {
      parse("[1, 2, 3, 4]");
      parse("(1, 2, 3)");
      parse("{1, 2, 3}");
      parse("{'a': 1, 'b': 2}");
      parse("{}");
    }
  }

  @Nested
  public class Assignments {
    @Test
    public void testAssignments() {
      parse("x = 1");
      parse("a = b = c = 42");
      parse("x += 1");
      parse("y -= 2");
      parse("z *= 3");
      parse("a //= 4");
      parse("b **= 5");
    }

    @Test
    public void testUnpackingAssignments() {
      parse("a, b = values");
      parse("a, *rest = items");
      parse("*head, tail = items");
      parse("a, (b, c) = nested");
    }

    @Test
    public void testAnnotatedAssignments() {
      parse("x: int");
      parse("x: int = 42");
      parse("data: list[str] = []");
    }
  }

  @Nested
  public class AssertionStatements {
    @Test
    public void testAssertStatements() {
      parse("assert x");
      parse("assert x > 0, 'must be positive'");
    }
  }

  @Nested
  public class DeletionStatement {
    @Test
    public void testDelStatements() {
      parse("del x");
      parse("del a[0]");
      parse("del a, b, c");
    }
  }

  @Nested
  public class RaiseStatements {
    @Test
    public void testRaise() {
      parse("raise");
      parse("raise ValueError()");
      parse("raise ValueError() from exc");
    }
  }

  @Nested
  public class YieldStatements {
    @Test
    public void testYield() {
      parse("""
        def gen():
            yield 1
        """);
    }

    @Test
    public void testYieldFrom() {
      parse("""
        def gen():
            yield from other()
        """);
    }
  }

  @Nested
  public class OperatorsAndPrecedence {
    @Test
    public void testArithmetic() {
      parse("x = a + b * c ** d / e // f % g");
      parse("x = -a + ~b");
    }

    @Test
    public void testBitwiseAndBoolean() {
      parse("x = (a & b) | (c ^ d) >> 2 << 1");
      parse("x = not a and b or c");
    }

    @Test
    public void testComparisons() {
      parse("x = a == b != c < d <= e > f >= g");
      parse("x = a is b and c is not d");
      parse("x = e in f or g not in h");
    }

    @Test
    public void testTernaryAndWalrus() {
      parse("x = true_val if condition else false_val");
      parse("if (x := call()) > 0: pass");
    }
  }

  @Nested
  public class Expressions {
    @Test
    public void testTupleExpressions() {
      parse("()");
      parse("(1,)");
      parse("1, 2, 3");
    }

    @Test
    public void testStarredExpressions() {
      parse("[*a]");
      parse("(*a, *b)");
      parse("{*a}");
    }

    @Test
    public void testConditionalExpressions() {
      parse("a if b else c");
      parse("a if b else c if d else e");
    }

    @Test
    public void testNamedExpression() {
      parse("(n := len(items))");
    }
  }

  @Nested
  public class ControlFlow {
    @Test @Disabled
    public void testIfStatements() {
      parse("""
          if x > 0:
              print("positive")
          elif x < 0:
              print("negative")
          else:
              print("zero")
          """);
    }

    @Test
    public void testWhileLoops() {
      parse("""
          while condition:
              break
          else:
              continue
          """);
    }

    @Test @Disabled
    public void testForLoops() {
      parse("""
          for i in range(10):
              if i == 5:
                  continue
              print(i)
          """);
    }

    @Test
    public void testExecStyleStatements() {
      parse("return");
      parse("break");
      parse("continue");
    }
  }

  @Nested
  public class PatternMatching {
    @Test
    public void testMatchCase() {
      parse("""
          match status:
              case 200:
                  return "OK"
              case 404 | 405:
                  return "Not Found"
              case _:
                  return "Unknown"
          """);
    }

    @Test
    public void testSequencePatterns() {
      parse("""
        match value:
            case [x, y]:
                pass
        """);
    }

    @Test
    public void testMappingPatterns() {
      parse("""
        match value:
            case {"name": name}:
                pass
        """);
    }

    @Test
    public void testClassPatterns() {
      parse("""
        match obj:
            case Point(x, y):
                pass
        """);
    }

    @Test
    public void testGuards() {
      parse("""
        match value:
            case x if x > 0:
                pass
        """);
    }
  }

  @Nested
  public class FunctionsAndLambdas {
    @Test
    public void testSimpleFunction() {
      parse("""
          def greet(name):
              return "Hello " + name
          """);
    }

    @Test
    public void testFStringFunction() {
      parse("""
          def greet(name):
              return f"Hello, {name}"
          """);
    }

    @Test
    public void testComplexArguments() {
      parse("""
          def complex_func(a, b=10, *args, kw_only, **kwargs):
              yield a
              return
          """);
    }

    @Test
    public void testPositionalOnlyArguments() {
      parse("""
        def f(a, b, /):
            pass
        """);
    }

    @Test
    public void testKeywordOnlyArguments() {
      parse("""
        def f(*, x, y):
            pass
        """);
    }

    @Test
    public void testMixedPositionalAndKeywordArguments() {
      parse("""
        def f(a, /, b, *, c):
            pass
        """);
    }

    @Test
    public void testTypeHinting() {
      parse("""
          def add(x: int, y: int = 0) -> int:
              return x + y
          """);
    }

    @Test
    public void testLambdasAndAsync() {
      parse("f = lambda x, y=1: x + y");
      parse("""
          async def fetch():
              await asyncio.sleep(1)
          """);
    }
  }

  @Nested
  public class CallsAndAttributes {
    @Test @Disabled
    public void testFunctionCalls() {
      parse("f()");
      parse("f(1, 2, 3)");
      parse("f(*args)");
      parse("f(**kwargs)");
      parse("f(1, *args, x=1, **kwargs)");
    }

    @Test @Disabled
    public void testAttributes() {
      parse("obj.field");
      parse("obj.method().field");
      parse("a.b.c.d");
    }
  }

  @Nested
  public class AsyncFeatures {
    @Test @Disabled
    public void testAsyncFor() {
      parse("""
        async def f():
            async for item in source:
                pass
        """);
    }

    @Test
    public void testAsyncWith() {
      parse("""
        async def f():
            async with lock:
                pass
        """);
    }
  }

  @Nested
  public class Decorators {
    @Test
    public void testFunctionDecorators() {
      parse("""
        @cache
        def f():
            pass
        """);

      parse("""
        @decorator(arg)
        def f():
            pass
        """);
    }

    @Test
    public void testMultipleDecorators() {
      parse("""
        @a
        @b
        def f():
            pass
        """);
    }
  }

  @Nested
  public class Classes {
    @Test
    public void testBasicClass() {
      parse("""
          class Empty:
              pass
          """);
    }

    @Test @Disabled
    public void testInheritanceAndMethods() {
      parse("""
          @decorator
          class Dog(Animal, Pack):
              def __init__(self, name):
                  super().__init__()
                  self._name = name
          
              def bark(self):
                  return "woof"
          """);
    }
  }

  @Nested
  public class ExceptionsAndContexts {
    @Test @Disabled
    public void testTryExceptFinally() {
      parse("""
          try:
              raise ValueError("error")
          except TypeError as e:
              pass
          except (AttributeError, KeyError):
              log_error()
          else:
              print("success")
          finally:
              cleanup()
          """);
    }

    @Test @Disabled
    public void testWithStatements() {
      parse("""
          with open("file.txt") as f, open("out.txt", "w") as out:
              out.write(f.read())
          """);
    }
  }

  @Nested
  public class ComprehensionsAndSlicing {
    @Test @Disabled
    public void testComprehensions() {
      parse("[x**2 for x in items if x > 0]");
      parse("{k: v for k, v in dict.items()}");
      parse("(x for x in generator)");
    }

    @Test @Disabled
    public void testMoreComprehensions() {
      parse("{x for x in items}");
      parse("""
        [x
         for x in items
         if x > 0
         if x < 100]
        """);
    }

    @Test @Disabled
    public void testNestedComprehensions() {
      parse("""
        [(x, y)
         for x in xs
         for y in ys]
        """);
    }

    @Test @Disabled
    public void testSlicingAndSubscripts() {
      parse("matrix[0][1]");
      parse("array[1:10:2]");
      parse("array[:5]");
      parse("array[5:]");
      parse("multi_dim[1:3, ::-1]");
    }
  }

  @Nested
  public class ImportsAndModules {
    @Test
    public void testImports() {
      parse("import os, sys");
      parse("import numpy as np");
      parse("from math import pi, sqrt as s");
      parse("from .relative import sibling");
      parse("from ...parent import grandparent");
    }

    @Test
    public void testGlobalNonlocal() {
      parse("""
          def outer():
              x = 1
              def inner():
                  nonlocal x
                  global y
                  x = 2
          """);
    }
  }
}